# Fonds de carte - Trailog

Ce que Trailog sait afficher sous les traces, et comment un fond se décrit. Le catalogue livré vit dans
[`data/seed/Providers.kt`](app/src/main/java/fr/lc4918/trailog/data/seed/Providers.kt) ; une fois semé,
il vit en base et l'utilisateur le modifie. Ce document décrit les **règles** ; les URL exactes, elles,
n'existent qu'à un seul endroit, le code semé, et les recopier ici les ferait diverger.

- [Ce qu'est un fond](#ce-quest-un-fond)
- [Types de service](#types-de-service)
- [Gabarits d'URL](#gabarits-durl)
- [Le catalogue livré](#le-catalogue-livré)
- [Pourquoi tel service et pas tel autre](#pourquoi-tel-service-et-pas-tel-autre)
- [Composites](#composites)
- [Fonds locaux et hors ligne](#fonds-locaux-et-hors-ligne)
- [Légendes](#légendes)
- [Le gestionnaire de fonds](#le-gestionnaire-de-fonds)
- [Import / export des fournisseurs](#import--export-des-fournisseurs)
- [Points d'attention](#points-dattention)

## Ce qu'est un fond

Un **fournisseur** (`ProviderEntity`) décrit une source de tuiles, et rien d'autre : l'application ne
connaît pas les fonds un par un, elle applique ce que la fiche déclare.

| Champ | Rôle |
|---|---|
| `id`, `name` | identifiant stable, et nom affiché |
| `groupName` | rubrique du gestionnaire : `Monde`, `Pays`, `Overlays`, `Relief`, `Local` |
| `type` | protocole (voir ci-dessous) |
| `urlTemplate` | gabarit, avec ses substitutions |
| `apiKey` | valeur de `{KEY}` ; vide si le service n'en demande pas |
| `subdomains` | valeurs de `{s}`, séparées par des virgules |
| `minZoom`, `maxZoom` | plage réellement servie : hors d'elle, MapLibre agrandit la dernière tuile plutôt que de demander une image que le service refuserait |
| `tileSize` | 256 en général, 512 pour certains MBTiles |
| `transparent` | surcouche (se pose **au-dessus** d'un fond) ou fond opaque |
| `enabled` | visible dans le gestionnaire de fonds |
| `builtin` | livré avec l'application, par opposition à ajouté à la main |
| `folderId`, `sortOrder` | rangement dans le gestionnaire |
| `legendAsset` | légende dépliable, si le fond en porte une |
| `opacityPct` | force du rendu ; ne concerne aujourd'hui que le relief (voir plus bas) |

## Types de service

| Type | Ce que c'est | Ce qu'il faut renseigner |
|---|---|---|
| `XYZ` | tuiles raster numérotées | gabarit `{z}/{x}/{y}` |
| `WMS` | image composée à la demande | requête `GetMap` complète, avec `{bbox-epsg-3857}` |
| `WMTS` | tuiles servies en KVP ou en REST | gabarit avec `TILEMATRIX`/`TILECOL`/`TILEROW`, ou chemin REST |
| `VECTOR` | style vectoriel MapLibre | URL du `style.json` |
| `DEM` | modèle de terrain, converti en ombrage | gabarit de tuiles terrarium |
| `MBTILES` | fichier local (raster ou vectoriel) | `mbtiles:///<chemin réel>` |
| `PMTILES` | archive unique, locale ou distante | `pmtiles://` devant un `file://`, `asset://` ou `https://` |

## Gabarits d'URL

| Substitution | Remplacée par |
|---|---|
| `{z}` `{x}` `{y}` | tuile demandée |
| `{s}` | l'un des `subdomains`, à tour de rôle |
| `{KEY}` | la clé du fournisseur |
| `{bbox-epsg-3857}` | emprise de la tuile, dans l'ordre `minx,miny,maxx,maxy` qu'impose WMS 1.3.0 |

L'ordre de la bbox est verrouillé par un test (`TileUrlTest`) : inversé, le service répond une image, mais
prise ailleurs - une faute qu'aucune erreur ne signale.

## Le catalogue livré

Une trentaine de fonds sont semés. **Sept seulement sont cochés d'entrée** : le gestionnaire ne montre que
les fonds activés, et une liste de trente entrées y serait illisible. Les autres attendent un interrupteur
dans les réglages, sans qu'il faille ressaisir leur URL.

| Rubrique | Fonds | Coché d'entrée |
|---|---|---|
| Monde | OpenStreetMap (défaut), Mapbox Outdoors, Google Street / Satellite / Relief | oui |
| Monde | Thunderforest Cycle et Outdoors, OpenFreeMap Liberty, MapTiler Outdoor, Freemap Outdoor | non |
| Overlays | Waymarked Trails VTT et Cycle, AF3V voies cyclables | non |
| Relief | DEM terrarium | non |
| Pays | France (IGN Scan), Espagne (IGN MTN) | oui |
| Pays | Hongrie, Slovaquie, Autriche, Norvège, Belgique, Suède, Croatie, Suisse, Allemagne, Finlande, Slovénie, Tchéquie, Royaume-Uni, Pologne, Portugal | non |

Cinq demandent une **clé** : Mapbox, Thunderforest (à fournir), IGN, Finlande, Royaume-Uni.

## Pourquoi tel service et pas tel autre

MapLibre n'affiche que du **Web Mercator (EPSG:3857)**. C'est ce qui écarte la plupart des services
nationaux les plus fins, et ce qui explique les choix du catalogue :

- une matrice de tuiles nationale (British National Grid, ETRS-TM35FIN, D96/TM slovène, S-JTSK tchèque)
  est inutilisable telle quelle, même quand le service la publie ;
- un **WMS** sauve souvent la situation : son moteur reprojette à la volée, là où le WMTS du même
  organisme ne publie ses tuiles que dans la projection du pays ;
- certains moteurs acceptent le 3857 sans le déclarer dans leur `GetCapabilities` : c'est vérifié au cas
  par cas, sur un point de repère connu.

Les raisons propres à chaque pays sont notées **au-dessus de leur entrée** dans `Providers.kt` : couche
retenue, variantes disponibles, plage de zoom utile, et ce qui a été essayé avant.

## Le relief

Le relief est un fond comme un autre : un DEM que MapLibre convertit en ombrage, allumé par son
interrupteur dans le gestionnaire, et non une option à part. Ses tuiles brutes ne sont pas des images à
regarder - elles codent des altitudes - ce qui lui vaut son seul réglage propre : la **force de son
ombrage** (`opacityPct`), réglable dans sa fiche, sous *Réglages / Tuiles / Fournisseurs*, avec un retour
à la valeur par défaut. 40 % : au-delà, l'ombre mange le fond posé dessous.

## Composites

Un **composite** est un fond opaque plus une surcouche transparente, avec son opacité, présenté comme un
choix de fond à part entière. Un seul est semé : les voies cyclables de l'AF3V sur Mapbox Outdoors.

Un composite affiche ses deux couches sans regarder leur interrupteur : la surcouche d'un composite n'a
pas à encombrer la liste des fonds pour être visible dans celui-ci.

## Fonds locaux et hors ligne

- **MBTiles importé** : l'utilisateur choisit un `.mbtiles`, l'application le **copie** dans son dossier
  MBTiles (chemin réel, réglable). La copie n'est pas un confort : `mbtiles://` exige un chemin de
  fichier, et l'URI `content://` du sélecteur Android n'en est pas un.
- **Zone téléchargée** : délimiter une emprise sur la carte et choisir une plage de zoom produit un
  MBTiles écrit par l'application, qui devient un fond local comme un autre. Le téléchargement vaut pour
  tous les types, WMS compris.
- Un fond local sert de fond ou de surcouche selon qu'il est opaque ou transparent, comme un fond distant.

## Légendes

Un fond peut déclarer une légende (`legendAsset`), qui fait apparaître un bouton d'information sur la
carte. Le mécanisme est générique ; l'AF3V l'inaugure.

## Le gestionnaire de fonds

Le gestionnaire (menu latéral) montre les fonds **activés**, rangés en dossiers que l'utilisateur crée et
réorganise par glisser-déposer. Il porte aussi le relief et le choix du fond courant.

## Import / export des fournisseurs

Les fournisseurs s'exportent et s'importent en JSON, **clés comprises et en clair** : c'est le seul moyen
de reporter une configuration d'un appareil à l'autre sans tout ressaisir. Un import est présenté avant
d'être appliqué.

## Points d'attention

- **Clés livrées** : le dépôt est public et les clés semées sont en clair dans `Providers.kt`. Elles sont
  personnelles et assumées comme telles ; certaines peuvent être limitées ou périmées, et chacune se
  remplace dans les réglages.
- **Google** (street, satellite, relief) : usage hors API officielle, contraire aux CGU de Google. Présent
  parce qu'il l'était dans l'existant dont vient le catalogue, à considérer comme "best effort".
- **OpenStreetMap standard**, fond par défaut, suit la *tile usage policy* d'OSM : usage léger toléré. Un
  usage intensif demande un fond dédié ou auto-hébergé.
- **Cleartext** : Android bloque le `http` par défaut. On privilégie `https` partout ; un service qui n'en
  offrirait pas demanderait une exception réseau ciblée.
- **Redistribution** : les MBTiles fournis par certains éditeurs imposent leurs propres conditions.
