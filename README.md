# CycleApp — application Android d'itinéraires vélo / VTT (squelette)

Application **native Kotlin + Jetpack Compose** pour consulter des itinéraires (et en
ajouter par **import de fichiers** GPX / GeoJSON / KML), avec carte **MapLibre Native**,
profil altimétrique natif synchronisé, et fonctionnement **hors-ligne** (MBTiles / cache).

> ⚠️ **Ceci est un squelette de démarrage** à ouvrir dans Android Studio. Il pose
> l'architecture complète et la plupart des écrans, mais n'a pas pu être compilé ici :
> quelques signatures de l'API MapLibre 11 peuvent demander de petits ajustements, et
> le `gradle-wrapper.jar` (binaire) est régénéré automatiquement par Android Studio.

## Ouvrir le projet
1. Android Studio (récent) → **Open** → sélectionner le dossier `cycle-app`.
2. Laisser Gradle se synchroniser (il télécharge Gradle 8.11.1 + dépendances).
3. Si proposé, accepter les mises à jour AGP/Kotlin/Compose.
4. Lancer sur un appareil/émulateur **API 24+**.
   - Backend rendu = **Vulkan** (défaut MapLibre). Si l'émulateur plante au rendu,
     remplacer dans `app/build.gradle.kts` la dépendance par
     `org.maplibre.gl:android-sdk-opengl:11.11.0`.

## Versions
- Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.1.0 · Compose BOM 2025.01.00
- MapLibre Native Android **11.11.0** · Room 2.6.1 · Navigation Compose 2.8.x
- minSdk 24 · target/compile 35 · `java.time` via core-library-desugaring · base Room **v2** (migration destructive en dev)

## Architecture
```
app/src/main/java/fr/lc4918/cycle/
├─ CycleApp.kt              Application : init MapLibre + dépôt + amorçage
├─ MainActivity.kt          setContent { AppRoot() }
├─ domain/
│  ├─ model/Models.kt       TrackPoint, Sample, TrackStats, ComputedTrack
│  └─ geo/TrackMath.kt      distance, lissage, pente, D+/D-, temps en mouvement (porté du JS)
│  └─ geo/Format.kt         formats durée / distance / altitude
├─ data/
│  ├─ db/                   Room : Folder, Route, Provider, Composite, Settings + DAO + base
│  ├─ seed/Providers.kt     21 fonds par défaut (OSM défaut, IGN FR/ES, overlays, DEM…)
│  ├─ imp/TrackImporter.kt  parseurs GeoJSON / GPX / KML
│  └─ repo/                 CycleRepository (import, calcul, stockage), GeoJsonStore
├─ map/StyleBuilder.kt      style MapLibre (fond + overlays + relief, MBTiles inclus)
└─ ui/
   ├─ components/MapLibreView.kt  MapView (AndroidView) + MapController (tracé, curseur, tap tolérant)
   ├─ profile/ElevationProfile.kt profil natif (Canvas) coloré par pente + curseur
   ├─ routes/MainScreen.kt        menu latéral (légende arborescente, avatar→réglages) + carte + profil
   ├─ settings/SettingsScreen.kt  unités, menu, tolérance, relief, fond défaut, dossier MBTiles, éditeur providers
   └─ nav/AppRoot.kt              NavHost (main / settings)
```

## Fonds de carte
- Édités dans les réglages (URL + clé API, activer/désactiver).
- **MBTiles** : fournir un provider de type `MBTILES` dont l'`urlTemplate` est le nom de
  fichier (rangé dans le **dossier MBTiles** configurable) ou un `mbtiles:///chemin` complet.
  MapLibre exige un **chemin réel** : à l'import, copier le `.mbtiles` dans ce dossier.
- **Composite** = un fond opaque + des overlays (modèle `CompositeEntity`).
- Relief : provider `DEM` (terrarium) + interrupteur *hillshade*.

## Couches de points (markers)
- Import **GeoJSON / GPX (wpt) / KML (Placemark)** comme couche de points, au même titre qu'un parcours.
- Marqueurs affichés sur la carte ; **tap sur un marqueur → infobulle** (lecture) avec les propriétés
  dans l'ordre du schéma de couche puis les propriétés propres au marqueur.
- Types de valeur gérés en lecture : **texte**, **image** (affichée à la taille de l'infobulle, bouton
  « Agrandir » en haut-droite → visionneuse plein écran), **lien** (pastille moderne cliquable, pas un `<a>` souligné).
- Bouton **crayon → formulaire (popup 80 %)** : édition des valeurs texte et lien (étape 1).

## Ce qui marche dans le squelette
- Import GPX/GeoJSON/KML → calcul stats (distance, D+/D-, pente, temps mouvement) → stockage.
- Liste arborescente (dossiers/itinéraires) dans le menu latéral plein écran (burger + swipe).
- Affichage carte du tracé + profil natif synchronisé (scrub → curseur).
- Réglages : unités, mode menu, tolérance de tap, relief, fond par défaut, dossier MBTiles, éditeur de providers (URL/clé/tileSize).
- Arborescence : **renommer / déplacer / supprimer** dossiers, parcours et couches ; **nouveau sous-dossier**.
- Tap sur la carte près d'un tracé → **curseur positionné au point le plus proche** (synchro carte ↔ profil).
- **Import d'un fichier `.mbtiles`** : copie vers le dossier réel + lecture des métadonnées SQLite (nom, zoom, format) + enregistrement comme fond utilisable hors-ligne (raster ; pbf/vectoriel signalé non géré en v1).

## TODO (prochaines itérations)
- **Éditeur d'infobulle, étape 2** : ajouter une propriété (texte/image/lien) avec **portée marqueur ou couche**,
  **import d'image** depuis le formulaire, et **réorganisation drag-drop** des propriétés (appliquée à toute la couche).
- UI de sélection des overlays + éditeur de composites.
- Gestion des régions hors-ligne (offline packs) + taille du cache ambiant.
- Renommer/déplacer dossiers & itinéraires (les DAO existent déjà).
- Tap carte → point le plus proche pour synchroniser le curseur (actuellement hit-test du tracé).
- Centrage sur la position GPS, terrain 3D.

Voir `SPEC.md` pour la spécification détaillée.
