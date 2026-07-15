# Application Android (Kotlin) - itinéraires vélo / VTT - Spécification

> Ce document décrit le **périmètre v1** tel que défini au démarrage du projet. Il reste la
> référence des intentions d'origine, mais l'application a dépassé ce périmètre : voir la
> [section 11](#11-au-delà-de-la-v1) pour ce qui a été ajouté depuis, et
> [`DEVELOPER.md`](DEVELOPER.md) pour l'état réel du code.

## 1. Périmètre v1
- Consulter les itinéraires existants (catalogue local, carte + profil altimétrique synchronisé).
- Ajouter des itinéraires par **import de fichier** (GPX / GeoJSON / KML) et les ranger dans des dossiers.
- Organisation en **arborescence de dossiers** (créer, renommer, déplacer dossiers et itinéraires).
- **Hors-ligne** : cache ambient + régions pré-téléchargées ; itinéraires stockés en local.
- Pas de dessin à la main (prévu plus tard), pas de suivi GPS temps réel en v1.

## 2. Pile technique
- Kotlin, **Jetpack Compose** (Material 3), une `Activity`, **Navigation Compose**.
- Carte : **MapLibre Native Android** (raster + vectoriel, API hors-ligne intégrée).
- Stockage : **Room** (catalogue, dossiers, providers, réglages) + fichiers pour la géométrie GeoJSON.
- Profil altimétrique : **Canvas Compose natif** (réimplémente la logique de `ol-elevation-profile` :
  distance, D+/D-, classes de pente, temps en mouvement, synchro marqueur).
- Architecture MVVM : Repository -> ViewModel (StateFlow) -> écrans Compose.
- Parsing : GeoJSON (kotlinx.serialization), GPX (lib type jpx), KML (XML léger) -> modèle interne unique.

## 3. Hors-ligne
- **Ambient cache** MapLibre : les tuiles chargées en ligne sont mises en cache et réutilisées hors-ligne (taille configurable).
- **Offline regions** : pré-téléchargement d'une zone (bbox + plage de zoom) dans une base locale.
- Itinéraires toujours disponibles (Room + fichiers).

## 4. Fonds de carte (registre éditable)
Chaque provider est éditable dans les réglages : **URL** et **API_KEY** personnalisables, activer/désactiver, ajouter/supprimer.
Repris de `js/map.js` + ajouts demandés. Type : `xyz` (raster tuilé), `wms`, `wmts`, `vector` (style), `dem` (relief).

### Monde
| Nom | Type | URL (gabarit) | Clé |
| --- | --- | --- | --- |
| **OpenStreetMap (défaut)** | xyz | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | - |
| Mapbox Outdoors | xyz | `https://api.mapbox.com/styles/v1/mapbox/outdoors-v10/tiles/256/{z}/{x}/{y}@2x?access_token={KEY}` | Mapbox |
| Google Street | xyz | `https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}` (s=0..3) | - |
| Google Satellite | xyz | `https://mt{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}` | - |
| Google Relief | xyz | `https://mt{s}.google.com/vt/lyrs=p&x={x}&y={y}&z={z}` | - |
| OSM Cycle (Thunderforest) | xyz | `https://tile.thunderforest.com/cycle/{z}/{x}/{y}.png?apikey={KEY}` | Thunderforest |
| Thunderforest Outdoors | xyz | `https://tile.thunderforest.com/outdoors/{z}/{x}/{y}.png?apikey={KEY}` | Thunderforest |
| OpenFreeMap (vectoriel, sans clé) | vector | `https://tiles.openfreemap.org/styles/liberty` | - |
| MapTiler Outdoor (vectoriel) | vector | `https://api.maptiler.com/maps/outdoor-v2/style.json?key={KEY}` | MapTiler |

### Overlays (transparents)
| Nom | Type | URL | Clé |
| --- | --- | --- | --- |
| Waymarked Trails VTT | xyz | `https://tile.waymarkedtrails.org/mtb/{z}/{x}/{y}.png` | - |
| Waymarked Trails Cycle | xyz | `https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png` | - |
| Hillshade (ombrage) | dem->hillshade | DEM terrarium `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png` (sans clé) | - |

### Pays
| Nom | Type | URL | Clé |
| --- | --- | --- | --- |
| France - IGN Scan | wmts | `https://data.geopf.fr/private/wmts?LAYER=GEOGRAPHICALGRIDSYSTEMS.MAPS&apikey={KEY}&FORMAT=image/jpeg&SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&STYLE=normal&TILEMATRIXSET=PM&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}` | IGN |
| Espagne - IGN MTN | wms | `https://www.ign.es/wms-inspire/mapa-raster?LAYERS=mtn_rasterizado&FORMAT=image/png&...&BBOX={bbox-epsg-3857}` | - |
| Hongrie - Turistautak | xyz | `https://terkep.turistautak.hu/tiles/turistautak-domborzattal/{z}/{x}/{y}.png` | - |
| Slovaquie - Freemap | xyz | `https://t{s}.freemap.sk/T/{z}/{x}/{y}.png` | - |
| Autriche - basemap.at | xyz | `https://maps{s}.wien.gv.at/basemap/geolandbasemap/normal/google3857/{z}/{y}/{x}.png` (s=1..4) | - |
| Norvège - Statkart | xyz | `https://opencache.statkart.no/gatekeeper/gk/gk.open_gmaps?layers=toporaster3&zoom={z}&x={x}&y={y}` | - |
| Belgique - NGI | xyz | `https://www.ngi.be/cartoweb/1.0.0/topo/default/3857/{z}/{y}/{x}.png` | - |
| Suède - Lantmäteriet | xyz | `https://api.lantmateriet.se/open/topowebb-ccby/v1/wmts/token/{KEY}/1.0.0/topowebb/default/3857/{z}/{y}/{x}.png` | Lantmäteriet |
| Croatie - DGU | wms | `https://geoportal.dgu.hr/services/tk/wms?LAYERS=TK25&FORMAT=image/jpeg&...&BBOX={bbox-epsg-3857}` | - |

### Relief / 3D
- **Hillshade** : source `raster-dem` (terrarium, sans clé) + couche `hillshade` (togglable).
- **Terrain 3D** (option) : même DEM en relief exagéré.

## 5. Modèle de données
```
Folder(id, name, parentId?, sortOrder)            // arborescence (principal = parentId null)
Route(id, name, folderId, link?, description?,
      source, importedAt, geometryFile,           // chemin du GeoJSON stocké
      distance, ascent, descent, minEle, maxEle,
      movingTime?, hasZ, hasTime, bboxJson, color)
Provider(id, name, group, type, urlTemplate, apiKey?, subdomains?, maxZoom, attribution, enabled, sortOrder)
Settings(units, sideMenuMode, tapToleranceDp, hillshade, terrain3d, ambientCacheMb, defaultBasemapId, mbtilesDir, ...)
Composite(id, name, baseProviderId, overlayProviderIds[])  // fond non transparent + 1..n overlays
```

## 6. Import + rangement
- Bouton "Importer" -> sélecteur de fichiers (SAF) -> parse GPX/GeoJSON/KML -> aperçu (nom, distance, D+/D-, Z/temps détectés).
- Choix de la **destination** :
  - dossier **existant**, ou
  - **nouveau dossier** : principal (racine) ou **sous-dossier d'un dossier existant** (choix du parent).
- Renommer / déplacer / supprimer dossiers ; déplacer un itinéraire d'un dossier à un autre (glisser ou menu).

## 7. Menu latéral (légende)
- Affiche l'**arborescence dossiers + itinéraires** (la légende), pleine largeur.
- Ouverture configurable : **bouton burger en haut à droite** OU **swipe gauche->droite** (réglage).
- **Avatar** en haut du menu -> tap ouvre les **Réglages**.
- Tap sur un itinéraire = l'affiche sur la carte + profil ; cases pour afficher/masquer plusieurs tracés.

## 8. Réglages
- Fonds de carte : liste éditable (URL + API_KEY par provider, activer/désactiver, ajouter/supprimer).
- **Fond par défaut** au lancement (défaut : OpenStreetMap).
- **Emplacement des MBTiles** : dossier à chemin réel où sont rangés/copiés les `.mbtiles` (défaut : dossier privé de l'app).
- Hors-ligne : taille du cache ambient, gérer les régions (télécharger / supprimer).
- Relief : hillshade on/off, terrain 3D on/off.
- Unités : métrique / impérial.
- Menu latéral : mode burger / swipe.
- **Tolérance de tap** : rayon (en dp) de sélection autour du doigt (défaut ~16 dp).
- Profil : options (lissage, pente, durée...) reprises de la librairie.

## 9. Sélection tolérante (tap sur tracé/marker)
- Au tap, on interroge MapLibre `queryRenderedFeatures` sur une **boîte** centrée sur le doigt, agrandie de `tapToleranceDp` (convertie en px selon la densité).
- Si plusieurs tracés sont dans la tolérance, on prend le plus proche (distance point->segment minimale).
- Même tolérance pour accrocher le profil au tracé.

## 4bis. Sources locales fichier (hors-ligne sans serveur)
- **MBTiles** : provider de type `mbtiles`, URL `mbtiles:///<chemin réel>/fichier.mbtiles` (raster ou vectoriel ; préciser `tileSize`, souvent 512). Idéal pour l'IGN España MTN en MBTiles.
- **PMTiles** (MapLibre Android ≥ 11.7.0) : préfixe `pmtiles://` avec `file://`/`asset://`/`https://`.
- Import : l'utilisateur choisit un `.mbtiles` (SAF) -> l'app le **copie** dans le dossier MBTiles (chemin réel configurable) -> enregistré comme provider local, utilisable hors-ligne.
- Un provider local peut servir de **fond** ou d'**overlay** selon qu'il est opaque ou transparent.

## 4ter. Fonds composites (fond + overlays)
- Un **composite** = un fond non transparent + un ou plusieurs overlays (ex. OSM + Waymarked Trails VTT, ou IGN España MBTiles + relief).
- Modèle `Composite(baseProviderId, overlayProviderIds[])` : à l'affichage, MapLibre empile les couches (fond en dessous, overlays au-dessus) dans le style.
- Créés/édités dans les réglages ; apparaissent comme un choix de fond à part entière (radio) dans le menu latéral.

## 10. Points d'attention
- Plusieurs URL d'origine sont en **http** : Android bloque le cleartext par défaut. On privilégie **https** quand le provider le permet ; pour les rares http-only, on ajoutera une exception réseau ciblée (network security config) - à valider provider par provider.
- **Clés API** présentes dans l'ancien code (Mapbox, Thunderforest, IGN, Lantmäteriet) : à reconfigurer dans les réglages ; certaines peuvent être périmées/limitées.
- **Google** (street/satellite/relief via mt*.google.com) : usage hors API officielle, contraire aux CGU Google - inclus car présent dans l'existant, mais à considérer comme "best effort"/à remplacer (ex. par des fonds équivalents).
- `mbtiles://` exige un **chemin de fichier réel** : les URI `content://` du sélecteur ne sont pas utilisables directement -> on copie le fichier importé dans le dossier MBTiles (chemin réel). Les MBTiles fournis par certains éditeurs (ex. MapTiler) peuvent imposer des conditions de redistribution.
- Le fond **OSM standard** par défaut suit la *tile usage policy* d'OSM (usage léger toléré) ; pour un usage intensif, préférer un fond dédié ou self-hosté.
- Versions exactes des dépendances (MapLibre Native, Compose BOM, AGP/Kotlin) **vérifiées au moment du scaffolding** (postérieures à ma date de connaissance).

## 11. Au-delà de la v1

Livré depuis, et non prévu par la spécification ci-dessus.

### Cartes hors-ligne (téléchargement de zone)
La v1 prévoyait les *offline regions* de MapLibre. L'implémentation retenue est différente :
saisie d'une emprise sur la carte, choix d'une plage de zoom, puis téléchargement des tuiles
dans un **MBTiles** produit par l'app, qui devient une couche comme une autre. Ce choix vaut
pour tous les types de fonds (y compris WMS via `{bbox-epsg-3857}`) et réutilise le chemin de
lecture MBTiles déjà en place.

### Profil altimétrique
- **Zoom** sur une portion : sélection d'un début et d'une fin, jusqu'à trois niveaux imbriqués,
  synchronisé avec la carte.
- **Lissage** réglable (1 à 100 m) avant calcul, pour les traces GPS à points espacés.
- **Échelle verticale** : Auto (le profil remplit la hauteur) ou absolue en mètres par
  centimètre physique, pour un rendu proportionné où une pente de 2 % n'apparaît pas comme un mur.

### Infobulles
- **Édition** complète : titre (propriété `name`), champs texte, lien et image, ajout de champs,
  suppression du marqueur.
- **Alias** des clés GPX usuelles : `ele` (avec unité), `desc`, `cmt` ; `sym` et `type` sont
  masqués mais conservés à l'enregistrement.
- **Image de garde** épinglable, affichée au-dessus du titre.
- **Photos des waypoints GPX** (OruxMaps, OsmAnd, Locus, Garmin) récupérées à l'import et
  copiées dans le stockage de l'app.
- **Placement** configurable : automatique ou l'une des 9 positions autour du marqueur.

### Fonds de carte
- **Légende** : un fond peut déclarer une légende (`ProviderEntity.legendAsset`), qui fait
  apparaître un bouton d'information sur la carte. Mécanisme générique, inauguré par le fond
  **AF3V voies cyclables** (WMS Lizmap, trois couches en une requête).
- Le registre de la section 4 n'est plus exhaustif : les fonds vivent dans
  `data/seed/Providers.kt` et en base, où l'utilisateur peut les modifier.

### Mises à jour automatiques
Non prévu en v1 : l'app lit un manifeste publié par sa propre CI, compare les versions, puis
télécharge et lance l'installation. Réglable en Auto ou Manuel. Détail dans
[`WORKFLOW.md`](WORKFLOW.md#6-mises-à-jour-automatiques).

### Écarts avec le modèle de données de la section 5
`Route` s'appelle `LayerEntity` : une couche importée peut porter des traces **et** des points
dans un même fichier (`hasLine` / `hasPoints`), là où la v1 supposait un itinéraire par entrée.
`Composite` se limite à un fond et **un** overlay avec opacité réglable, et non `overlayProviderIds[]`.
