#!/usr/bin/env bash
# Construit l'index de lieux de toute l'Europe, un carre BRouter de 5 degres a la fois.
#
# L'extrait Europe (35 Go) ne passe pas d'un bloc dans build_index.py : son cache de positions de noeuds
# ferait ~50 Go et ses lieux ne tiendraient pas en memoire. On le decoupe donc par lots de carres avec
# osmium-tool, en deux niveaux (bandes de 10 degres, puis carres ; strategie "smart" : les relations de
# limites sont completees, une commune a cheval sur deux carres reste fermee), chaque carre deborde d'une marge sur ses voisins pour que les lieux pres du bord
# trouvent leur localite, puis build_index.py --tile n'ecrit que le carre vise.
#
# Seuls sont faits les carres ou BRouter publie des donnees (BROUTER_URL).
#
# Reprise : un carre deja fait laisse un marqueur dans le dossier de travail et n'est pas refait.
#
# Usage : build_europe.sh europe.osm.pbf dossier-de-sortie dossier-de-travail [carres-par-lot]
# Prerequis : osmium-tool (apt, ou OSMIUM=chemin du binaire), le venv de build_index.py (VENV, par defaut ~/geocode-data/venv).
set -euo pipefail

input_pbf=$1
output_dir=$2
work_dir=$3
batch_size=${4:-2}
margin=0.2
venv=${VENV:-$HOME/geocode-data/venv}
osmium=${OSMIUM:-osmium}
script_dir=$(cd "$(dirname "$0")" && pwd)

mkdir -p "$output_dir" "$work_dir"

# Les carres qui recouvrent l'emprise de l'extrait (lue dans son en-tete).
read -r min_lon min_lat max_lon max_lat < <(
    "$osmium" fileinfo -g header.boxes "$input_pbf" | head -1 | tr -d '()' | tr ',' ' ')
floor5() { python3 -c "import math,sys; print(math.floor(float(sys.argv[1]) / 5) * 5)" "$1"; }
ceil_int() { python3 -c "import math,sys; print(math.ceil(float(sys.argv[1])))" "$1"; }
max_lon=$(ceil_int "$max_lon")
max_lat=$(ceil_int "$max_lat")
tile_name() {
    local lon=$1 lat=$2
    local ew=E ns=N
    [ "$lon" -lt 0 ] && ew=W
    [ "$lat" -lt 0 ] && ns=S
    echo "${ew}${lon#-}_${ns}${lat#-}"
}
# Seuls les carres ou BRouter publie des donnees : l'index se telecharge avec une zone d'itineraire, un
# carre de mer n'en a pas. Sans reponse du serveur, on garde tous les carres de l'emprise.
brouter_url=${BROUTER_URL:-https://brouter.de/brouter/segments4/}
brouter_tiles=$(curl -sf "$brouter_url" | grep -oE '[EW][0-9]+_[NS][0-9]+\.rd5' | sort -u || true)
pending_tiles=()
for ((lon = $(floor5 "$min_lon"); lon < max_lon; lon += 5)); do
    for ((lat = $(floor5 "$min_lat"); lat < max_lat; lat += 5)); do
        name=$(tile_name "$lon" "$lat")
        [ -e "$work_dir/$name.done" ] && continue
        if [ -n "$brouter_tiles" ] && ! grep -qx "$name.rd5" <<<"$brouter_tiles"; then continue; fi
        pending_tiles+=("$lon $lat $name")
    done
done
echo "${#pending_tiles[@]} carres a traiter" >&2

# Decoupe en un passage les carres donnes ("lon lat nom" ...) depuis source_pbf, puis les indexe un par un.
extract_and_index() {
    local source_pbf=$1
    shift
    local batch=("$@")
    local config="$work_dir/batch.json" separator="" entry lon lat name tile_pbf
    {
        echo '{"extracts": ['
        for entry in "${batch[@]}"; do
            read -r lon lat name <<<"$entry"
            printf '%s{"output": "%s.osm.pbf", "bbox": [%s, %s, %s, %s]}' "$separator" "$name" \
                "$(python3 -c "print($lon - $margin)")" "$(python3 -c "print($lat - $margin)")" \
                "$(python3 -c "print($lon + 5 + $margin)")" "$(python3 -c "print($lat + 5 + $margin)")"
            separator=$',\n'
        done
        echo ']}'
    } >"$config"
    echo "$(date +%T) decoupe : ${batch[*]}" >&2
    "$osmium" extract --overwrite -s smart -S types=multipolygon,boundary -d "$work_dir" -c "$config" "$source_pbf"
    for entry in "${batch[@]}"; do
        read -r lon lat name <<<"$entry"
        tile_pbf="$work_dir/$name.osm.pbf"
        echo "$(date +%T) $name : $(du -h "$tile_pbf" | cut -f1)" >&2
        "$venv/bin/python" "$script_dir/build_index.py" "$tile_pbf" "$output_dir" --tile "$name"
        rm -f "$tile_pbf"
        touch "$work_dir/$name.done"
    done
}

# Deux niveaux : chaque passage d'osmium sur l'extrait Europe le relit en entier (~13 min). On en tire donc
# d'abord une bande de 10 degres de longitude, puis les carres de la bande, par lots, depuis ce fichier
# bien plus petit.
strip_width=10
declare -A strip_tiles=()
for entry in "${pending_tiles[@]}"; do
    read -r lon lat name <<<"$entry"
    strip=$(( (lon - (lon % strip_width + strip_width) % strip_width) ))
    strip_tiles[$strip]+="$entry"$'\n'
done
for strip in $(printf '%s\n' "${!strip_tiles[@]}" | sort -n); do
    mapfile -t tiles < <(printf '%s' "${strip_tiles[$strip]}")
    strip_min_lat=$(printf '%s\n' "${tiles[@]}" | awk '{print $2}' | sort -n | head -1)
    strip_max_lat=$(printf '%s\n' "${tiles[@]}" | awk '{print $2}' | sort -n | tail -1)
    strip_pbf="$work_dir/strip.osm.pbf"
    strip_done="$work_dir/strip-$strip.ok"
    # Une bande deja tiree (marqueur pose apres un decoupage complet) se reprend telle quelle : la
    # relire dans l'extrait Europe coute un quart d'heure.
    if [ -e "$strip_done" ] && [ -e "$strip_pbf" ]; then
        echo "$(date +%T) bande $strip : ${#tiles[@]} carres (bande deja decoupee)" >&2
    else
        rm -f "$work_dir"/strip-*.ok
        echo "$(date +%T) bande $strip : ${#tiles[@]} carres" >&2
        "$osmium" extract --overwrite -s smart -S types=multipolygon,boundary -o "$strip_pbf" \
            -b "$(python3 -c "print(f'{$strip - $margin},{$strip_min_lat - $margin},{$strip + $strip_width + $margin},{$strip_max_lat + 5 + $margin}')")" \
            "$input_pbf"
        touch "$strip_done"
    fi
    # Memoire : osmium extract tient, par carre, des ensembles de bits a la mesure du plus GRAND
    # identifiant OSM (plus de 13 milliards), soit ~3 Go par carre quel que soit son contenu. D'ou des
    # lots de deux carres par defaut. Renumeroter la bande eviterait ce cout, mais la renumerotation
    # d'une bande dense (12 Go pour France-Benelux-Royaume-Uni) epuise elle-meme 15 Go.
    for ((start = 0; start < ${#tiles[@]}; start += batch_size)); do
        extract_and_index "$strip_pbf" "${tiles[@]:start:batch_size}"
    done
    rm -f "$strip_pbf" "$strip_done"
done
echo "$(date +%T) termine : $(wc -l <"$output_dir/index.txt") carres dans index.txt" >&2
