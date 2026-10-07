#!/usr/bin/env python3
"""
Construit l'index de lieux hors ligne a partir d'un extrait OpenStreetMap (.osm.pbf).

Sortie : une base SQLite par carre de 5 degres, du meme decoupage que les donnees de BRouter, de sorte
qu'une zone d'itineraire telechargee emporte avec elle de quoi chercher ses lieux sans reseau.

  E5_N45.gc.sqlite.gz   la base du carre, compressee (l'application la decompresse a l'arrivee)
  index.txt             une ligne par carre : nom, octets compresses, octets decompresses, date (ms)

Chaque base porte :
  localities(id, name)                               les communes que les lieux designent
  places(id, name, kind, lat, lon, locality, cell)   lat/lon en 1e-5 degre (entiers)
  name_fts, street_fts                               FTS5 sans contenu (lieux ; rues), recherche par prefixe
  index sur cell                                     geocodage inverse (cases de 0,01 degre)

Contient les lieux (localites, relief, eau, refuges, gares, cols) ET les rues nommees.

Usage : build_index.py extrait.osm.pbf dossier-de-sortie
"""
import argparse
import math
import os
import sqlite3
import sys
import time
from collections import defaultdict

import osmium

# kind : code entier stable, lu par l'application pour choisir l'icone et le rang.
KIND = {
    "city": 1, "town": 2, "village": 3, "hamlet": 4, "suburb": 5, "locality": 6, "island": 7,
    "peak": 10, "volcano": 11, "saddle": 12, "pass": 13, "cape": 14, "bay": 15, "beach": 16,
    "spring": 17, "cave": 18, "glacier": 19, "valley": 20, "ridge": 21,
    "lake": 30, "river": 31,
    "hut": 40, "viewpoint": 41, "camp": 42, "station": 43,
    "street": 60,
}
# Importance d'un genre : plus petit = plus en vue. Les identifiants d'un carre se donnent dans cet ordre,
# si bien qu'une recherche qui s'arrete a ses N premiers resultats (l'ordre d'une table FTS est celui des
# identifiants) rend d'abord les villes, puis les villages, et les hameaux et lieux-dits en dernier.
TIER = {
    "city": 0, "town": 1, "village": 2, "suburb": 3, "island": 3,
    "peak": 4, "volcano": 4, "pass": 4, "saddle": 4, "lake": 4, "hut": 4, "station": 4, "viewpoint": 4,
    "cape": 4, "bay": 4, "glacier": 4, "valley": 4, "ridge": 4, "river": 4, "camp": 4, "beach": 4,
    "hamlet": 5, "spring": 6, "cave": 6, "locality": 7, "street": 8,
}
LOCALITY_KINDS = {"city", "town", "village", "hamlet"}
STREET_HIGHWAYS = {
    "residential", "living_street", "pedestrian", "unclassified", "tertiary", "secondary", "primary",
    "trunk", "road", "motorway",
}
PLACE_VALUES = {
    "city", "town", "village", "hamlet", "suburb", "quarter", "neighbourhood", "isolated_dwelling",
    "locality", "island", "islet",
}
NATURAL = {
    "peak": "peak", "volcano": "volcano", "saddle": "saddle", "cape": "cape", "bay": "bay",
    "beach": "beach", "spring": "spring", "hot_spring": "spring", "cave_entrance": "cave",
    "glacier": "glacier", "valley": "valley", "ridge": "ridge", "water": "lake",
}
TOURISM = {"alpine_hut": "hut", "wilderness_hut": "hut", "viewpoint": "viewpoint", "camp_site": "camp"}
RAILWAY = {"station": "station", "halt": "station"}

DEDUPE_CELL_WAY = 0.02   # une meme voie ou riviere en plusieurs tronçons : un seul enregistrement par case
GRID = 0.01              # case du geocodage inverse (cf. cell_of)
LOCALITY_GRID = 0.1


def classify(tags):
    """Le genre du lieu nomme porte par ces tags, ou None."""
    place = tags.get("place")
    if place in PLACE_VALUES:
        return {"quarter": "suburb", "neighbourhood": "suburb", "isolated_dwelling": "hamlet",
                "islet": "island"}.get(place, place)
    if tags.get("mountain_pass") == "yes" or tags.get("highway") == "mountain_pass":
        return "pass"
    natural = NATURAL.get(tags.get("natural"))
    if natural:
        return natural
    if tags.get("waterway") == "river":
        return "river"
    if tags.get("tourism") in TOURISM:
        return TOURISM[tags["tourism"]]
    if tags.get("railway") in RAILWAY:
        return RAILWAY[tags["railway"]]
    if tags.get("highway") in STREET_HIGHWAYS:
        return "street"
    return None


def collect(path):
    """Un seul passage : nœuds et voies nommes, ramenes a un point."""
    records = []                  # (name, kind, lat, lon)
    seen = set()
    types = osmium.osm.NODE | osmium.osm.WAY
    fp = (osmium.FileProcessor(path, types)
          .with_filter(osmium.filter.KeyFilter("name"))
          .with_locations("sparse_file_array," + path + ".nodecache"))
    start = time.time()
    count = 0
    for obj in fp:
        count += 1
        if count % 500_000 == 0:
            print(f"  {count:>10} objets nommes lus, {len(records)} retenus, {time.time() - start:.0f} s",
                  file=sys.stderr)
        name = obj.tags.get("name")
        if not name:
            continue
        kind = classify(obj.tags)
        if kind is None:
            continue
        if obj.is_node():
            lat, lon = obj.location.lat, obj.location.lon
        else:
            try:
                nodes = obj.nodes
                mid = nodes[len(nodes) // 2].location
                if not mid.valid():
                    continue
            except osmium.InvalidLocationError:
                continue
            lat, lon = mid.lat, mid.lon
            if kind in ("street", "river", "lake", "valley", "ridge", "glacier", "beach", "bay"):
                key = (name, kind, round(lat / DEDUPE_CELL_WAY), round(lon / DEDUPE_CELL_WAY))
                if key in seen:
                    continue
                seen.add(key)
        records.append((name, kind, lat, lon))
    try:
        os.remove(path + ".nodecache")
    except OSError:
        pass
    return records


def assign_localities(records):
    """Pour chaque enregistrement, l'indice de la localite la plus proche (ville, village, hameau)."""
    grid = defaultdict(list)
    for index, (_, kind, lat, lon) in enumerate(records):
        if kind in LOCALITY_KINDS:
            grid[(int(lat // LOCALITY_GRID), int(lon // LOCALITY_GRID))].append(index)
    result = []
    for name, kind, lat, lon in records:
        if kind in LOCALITY_KINDS:
            result.append(None)
            continue
        cy, cx = int(lat // LOCALITY_GRID), int(lon // LOCALITY_GRID)
        best, best_d = None, 1e18
        cos = math.cos(math.radians(lat))
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                for j in grid.get((cy + dy, cx + dx), ()):
                    _, k2, la2, lo2 = records[j]
                    d = (la2 - lat) ** 2 + ((lo2 - lon) * cos) ** 2
                    if k2 == "hamlet":
                        d *= 4          # une commune l'emporte sur un hameau de peu plus pres
                    if d < best_d:
                        best, best_d = j, d
        result.append(best)
    return result


TILE = 5   # degres : le carre de BRouter (cf. BrouterTile cote application)


def tile_of(lat, lon):
    return (math.floor(lon / TILE) * TILE, math.floor(lat / TILE) * TILE)


def tile_name(lon0, lat0):
    return f"{'E' if lon0 >= 0 else 'W'}{abs(lon0)}_{'N' if lat0 >= 0 else 'S'}{abs(lat0)}"


def cell_of(ilat, ilon):
    """Case de 0,01 degre, en un entier, a partir des coordonnees en 1e-5 degre : le geocodage inverse
    cherche dans les cases voisines. Arithmetique entiere, la meme dans l'application (PlaceQuery.cellOf) -
    un quotient de flottants ne tombe pas toujours du meme cote d'une frontiere de case."""
    return (ilat // 1000) * 100_000 + (ilon // 1000) + 20_000


def write_tile_db(path, records, indices, localities):
    """Une base par carre : les lieux de ce carre, et les noms des localites qu'ils designent."""
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.executescript("""
        PRAGMA page_size = 4096;
        PRAGMA journal_mode = OFF;
        PRAGMA user_version = 1;
        CREATE TABLE localities(id INTEGER PRIMARY KEY, name TEXT NOT NULL);
        CREATE TABLE places(
            id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind INTEGER NOT NULL,
            lat INTEGER NOT NULL, lon INTEGER NOT NULL, locality INTEGER, cell INTEGER NOT NULL);
        CREATE VIRTUAL TABLE name_fts USING fts5(
            name, content='', contentless_delete=0, detail=none,
            tokenize='unicode61 remove_diacritics 2', prefix='2 3');
        CREATE VIRTUAL TABLE street_fts USING fts5(
            name, content='', contentless_delete=0, detail=none,
            tokenize='unicode61 remove_diacritics 2', prefix='2 3');
    """)
    local_ids = {}
    rows, fts, street_fts = [], [], []
    # Par importance, puis par longueur de nom : "Revel" avant "Revel-Tourdan" (cf. TIER).
    indices = sorted(indices, key=lambda i: (TIER[records[i][1]], len(records[i][0]), records[i][0]))
    for n, old in enumerate(indices, start=1):
        name, kind, lat, lon = records[old]
        loc = localities[old]
        loc_id = None
        if loc is not None:
            loc_id = local_ids.get(loc)
            if loc_id is None:
                loc_id = local_ids[loc] = len(local_ids) + 1
        ilat, ilon = round(lat * 1e5), round(lon * 1e5)
        rows.append((n, name, KIND[kind], ilat, ilon, loc_id, cell_of(ilat, ilon)))
        (street_fts if kind == "street" else fts).append((n, name))
    db.executemany("INSERT INTO localities VALUES (?,?)", [(i, records[j][0]) for j, i in local_ids.items()])
    db.executemany("INSERT INTO places VALUES (?,?,?,?,?,?,?)", rows)
    db.executemany("INSERT INTO name_fts(rowid, name) VALUES (?,?)", fts)
    db.executemany("INSERT INTO street_fts(rowid, name) VALUES (?,?)", street_fts)
    db.execute("CREATE INDEX places_cell ON places(cell)")
    db.commit()
    db.execute("VACUUM")
    db.close()


def gzip_file(path):
    import gzip
    import shutil
    # Date d'en-tete figee : deux executions sur le meme extrait rendent le meme fichier.
    with open(path, "rb") as source, open(path + ".gz", "wb") as raw_out, \
            gzip.GzipFile(fileobj=raw_out, mode="wb", compresslevel=9, mtime=0) as target:
        shutil.copyfileobj(source, target)
    os.remove(path)
    return os.path.getsize(path + ".gz")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("pbf")
    parser.add_argument("outdir", help="un fichier E5_N45.gc.sqlite.gz par carre, plus index.txt")
    args = parser.parse_args()
    os.makedirs(args.outdir, exist_ok=True)

    t0 = time.time()
    records = collect(args.pbf)
    print(f"{len(records)} enregistrements en {time.time() - t0:.0f} s", file=sys.stderr)
    localities = assign_localities(records)
    print(f"localites assignees en {time.time() - t0:.0f} s", file=sys.stderr)

    by_tile = defaultdict(list)
    for index, (_, _, lat, lon) in enumerate(records):
        by_tile[tile_of(lat, lon)].append(index)

    index_lines = []
    now_ms = int(time.time() * 1000)
    for (lon0, lat0), indices in sorted(by_tile.items()):
        name = tile_name(lon0, lat0)
        raw_path = os.path.join(args.outdir, name + ".gc.sqlite")
        write_tile_db(raw_path, records, indices, localities)
        raw = os.path.getsize(raw_path)
        packed = gzip_file(raw_path)
        index_lines.append(f"{name} {packed} {raw} {now_ms}")
        print(f"  {name}: {len(indices)} lieux, {raw / 1e6:.1f} Mo, {packed / 1e6:.1f} Mo compresses", file=sys.stderr)
    with open(os.path.join(args.outdir, "index.txt"), "w") as out:
        out.write("\n".join(index_lines) + "\n")


if __name__ == "__main__":
    main()
