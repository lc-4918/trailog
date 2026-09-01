# Guide de développement - Trailog

Ce guide s'adresse aux développeurs souhaitant compiler, comprendre ou contribuer au
code de Trailog (application Android native, Kotlin + Jetpack Compose).

## 1. Prérequis

- **Android Studio** récent (Ladybug ou plus récent recommandé).
- **JDK 17** (fourni par Android Studio, ou installé séparément).
- **Gradle** : géré automatiquement par le wrapper (`./gradlew`), aucune installation manuelle requise.
- **Git**.

## 2. Installation locale

```bash
git clone https://github.com/lc-4918/trailog.git
cd trailog
```

1. Ouvrir le dossier `trailog` dans Android Studio (**Open**).
2. Laisser Gradle se synchroniser (télécharge Gradle 8.11.1 et les dépendances).
3. Copier `local.properties.sample` vers `local.properties` et ajuster le chemin du SDK
   Android si besoin (`sdk.dir`).
4. Lancer sur un appareil ou émulateur **API 24+**.

> Aucun secret n'est nécessaire pour un build **debug** local ; la signature release
> (voir [`WORKFLOW.md`](WORKFLOW.md)) ne concerne que le CI/CD.

## 3. Structure du projet

```
app/src/main/java/fr/lc4918/trailog/
├─ MainActivity.kt              setContent { AppRoot(autoCheckUpdates) }
├─ TrailogApp.kt                Application : init MapLibre + dépôt + amorçage
├─ domain/
│  ├─ model/                    Models.kt (TrackPoint, TrackStats), Points.kt, BubblePosition.kt
│  └─ geo/                      TrackMath.kt (distance, D+/D-, pente), Format.kt
├─ data/
│  ├─ backup/                   archive zip de sauvegarde et sa relecture
│  ├─ db/                       Room : Entities, DAO, AppDatabase (migrations explicites)
│  ├─ seed/Providers.kt         Fonds de carte par défaut
│  ├─ imp/                      LayerImporter (GPX/GeoJSON/KML), PropertyDetector
│  └─ repo/                     TrailogRepository, LayerGeoJson, StoragePaths
├─ elevation/                   altitudes manquantes : IGN en France, OpenTopography ailleurs
├─ geocode/                     Photon (recherche de lieu / adresse), etat de connexion
├─ location/                    suivi de position hors de l'ecran : service de premier plan, veille sur la trace, son de l'alerte
├─ net/                         ServiceUrl (reseau local ou service externe)
├─ poi/                         points d'interet : DATAtourisme, Overpass (OSM, requetes decoupees en
│                              tuiles), regle de partage entre les deux sources, cache
├─ routing/                     Valhalla et BRouter (itineraire, duree, trace ; 5 disciplines), Polyline,
│                              RouteTimeout (delai proportionnel a la longueur demandee)
├─ map/                         BasemapIcons, CompositeBasemaps, StyleBuilder (style MapLibre),
│                              AmbientCache (taille du cache de tuiles)
│  └─ offline/                  TileMath, TileUrl, TileHttp, OfflineTileDownloader, OfflineThumbnails
├─ update/                      UpdateManager (manifeste + installateur), UpdateDialog
└─ ui/
   ├─ components/                MapLibreView, Avatar, ImageViewer, CompactTextField
   ├─ profile/                   ElevationProfile (Canvas), SlopeLegend, SlopeRamp
   ├─ routes/                    l'ecran principal, decoupe par domaine :
   │                              MainScreen (carte, etats, effets), MainViewModel,
   │                              MainDrawer (menu lateral et son arborescence),
   │                              MapChrome (commandes posees sur la carte, echelle, legende),
   │                              MapEffects (ce que l'ecran demande au controleur MapLibre),
   │                              ImportFlow (du fichier a la couche, d'ou qu'il vienne),
   │                              ProfilePanels, TrackEditUi, MainDialogs, ImportPicker,
   │                              DeviceHeading et HeadingFusion (d'ou la fleche tire sa direction)
   ├─ location/                  le suivi de position vu de l'ecran : autorisations, allumage, position ponctuelle
   ├─ alert/                     suivi de trace : banniere d'eloignement, choix de la trace, son, et le
   │                              tableau de bord de l'avancement (FollowProgress, FollowDashboard)
   ├─ poi/                       couche des points d'interet : marqueurs, infobulle, chargement,
   │                              PoiCorridor (couloir autour des traces affichees)
   ├─ points/                    InfoBubble, PropertyEditor, FieldMeta, BubblePlacement, AnchoredBubble
   ├─ geocode/                   barre de recherche, infobulle du lieu, etat de la recherche
   ├─ mappoint/                  point designe par un appui long : son adresse, et PointMeasures - les
   │                              deux distances, que l'infobulle d'un point d'interet porte aussi
   ├─ measure/                   mesure d'une distance entre deux points d'une trace
   ├─ planner/                   bande du planificateur d'itineraire, etat de ses etapes
   ├─ offline/                   Saisie de la zone et configuration du téléchargement
   ├─ settings/                  SettingsScreen (le squelette de l'ecran et son aiguillage), puis un
   │                              fichier par onglet : SettingsMapTab, SettingsTilesTab,
   │                              SettingsRoutesTab, SettingsSystemTab. SettingsWidgets porte les
   │                              briques des quatre ; SettingsStyle la palette, SettingsViewModel l'etat
   ├─ theme/                     Theme.kt
   └─ nav/                       AppRoot.kt
```

Voir [`ARCHITECTURE.md`](ARCHITECTURE.md) pour la structure technique (modules, règle de dépendance,
vues dynamiques, décisions), [`SPEC.md`](SPEC.md) pour la spécification fonctionnelle, et
[`BASEMAPS.md`](BASEMAPS.md) pour le catalogue des fonds de carte et les règles qui le gouvernent.

## 4. Build & Run

```bash
./gradlew :app:assembleDebug      # build l'APK debug
./gradlew :app:installDebug       # build + installe sur device/émulateur connecté
```

Logs : `adb logcat` ou la fenêtre **Logcat** d'Android Studio.

## 5. Tests

```bash
./gradlew :app:testDebugUnitTest  # tests unitaires JVM (JUnit 4)
```

Les tests unitaires vivent dans `app/src/test/java/fr/lc4918/trailog/` :

| Test | Ce qu'il verrouille |
|---|---|
| `domain/geo/TrackMathTest` | calculs géométriques (distance, D+/D-, pente) |
| `ui/points/BubblePlacementTest` | placement de l'infobulle autour du marqueur, bornes d'écran |
| `update/ReleaseInfoTest` | forme du manifeste écrit par la CI et calcul du `versionCode` |
| `geocode/PhotonTest` | construction de la requête et lecture de la réponse du géocodeur |
| `routing/ValhallaTest` | requête et réponse du moteur d'itinéraire, correspondance des disciplines |
| `routing/PolylineTest` | décodage des polylignes, dont la précision propre à Valhalla |
| `poi/OverpassTest` | requête et lecture de la réponse d'OpenStreetMap, la seconde source de points d'intérêt |
| `location/TrackWatchTest` | alerte d'éloignement : déclenchement, zone morte, réarmement |
| `poi/PoiStreamTest` | ordre d'affichage des deux sources, et report d'un chargement sur le suivant |
| `poi/PoiSourcesTest` | qui répond pour quelles catégories : le partage entre DATAtourisme et OSM |
| `ui/poi/PoiCorridorTest` | couloir autour des traces : ce qu'on affiche, et ce qu'on demande |
| `ui/poi/PoiStateTest` | ce que la couche dit d'elle-même, et quand elle redemande une emprise |
| `routing/RouteTimeoutTest` | délai accordé au moteur selon la longueur du trajet |
| `ui/routes/HeadingFusionTest` | d'où la flèche tire sa direction : déplacement ou boussole |
| `ui/alert/FollowProgressTest` | les neuf chiffres du tableau de bord du suivi |
| `ui/mappoint/PointMeasuresTest` | les deux distances, et leur indépendance d'une bulle à l'autre |
| `ui/location/LastFixShownTest` | la dernière position mesurée, gardée à l'arrêt du suivi |

`ReleaseInfoTest` mérite une note : il garde un contrat entre deux fichiers qui ne se compilent
pas ensemble, le `jq` du workflow et le parseur Kotlin. Une divergence n'y produirait aucune
erreur visible, seulement des mises à jour qui cesseraient d'être proposées.

**Les tests d'interface** (`*UiTest`) vivent au même endroit et se lancent par la même commande : ils
composent pour de vrai sous Robolectric, sans appareil ni émulateur. La limite à connaître est que rien
de ce qui embarque une `MapView` ne peut y être composé - les bibliothèques natives de MapLibre ne se
chargent pas sur la JVM. Le détail est dans [`TESTS.md`](TESTS.md#tests-dinterface).

Seule la migration de base est un test d'instrumentation (`app/src/androidTest/`) : elle a besoin d'un
vrai SQLite.

## 6. Workflow de contribution

1. **Fork** du dépôt, puis clone de votre fork.
2. Créer une **branche** dédiée (`git checkout -b feature/ma-fonctionnalite`).
3. Commits atomiques, messages clairs décrivant le *pourquoi*.
4. Vérifier que `./gradlew :app:assembleDebug` et les tests passent localement.
5. Ouvrir une **Pull Request** vers `main` sur `lc-4918/trailog`.
6. Revue de code, puis merge.

La publication d'une version, elle, est décrite dans [`WORKFLOW.md`](WORKFLOW.md) : un tag `vX.Y.Z`
poussé sur `main` déclenche l'APK signé, la Release GitHub et le manifeste que lisent les
installations existantes.

## 7. Architecture

- **Pattern** : MVVM, `Repository` (accès données) -> `ViewModel` (`StateFlow`) -> écrans Compose.
- **UI** : Jetpack Compose (Material 3), une seule `Activity`. **Pas de NavHost** : `AppRoot.kt`
  superpose les réglages à `MainScreen` dans une `Box`, pour ne pas détruire la `MapView` (une
  recréation provoquait une réinitialisation du zoom et un scintillement).
- **Carte** : MapLibre Native Android, intégrée via `AndroidView` (`MapLibreView.kt`) et pilotée
  par un contrôleur dédié (tracé, curseur, tolérance de tap).
- **Stockage** : Room (catalogue, dossiers, fournisseurs de tuiles, réglages) + fichiers
  GeoJSON pour la géométrie des traces. Le schéma évolue par **migrations explicites**
  (`AppDatabase.kt`) et non par recréation destructive, qui effacerait les couches importées.
- **Asynchrone** : Kotlin Coroutines.
- **Sérialisation** : kotlinx.serialization (GeoJSON, manifeste de mise à jour).
- **Réglages** : table `settings` de Room (une seule ligne). Seule la langue est à part, dans des
  `SharedPreferences` (`LocalePrefs`), devant être lue avant la création de la base.
- **Images** : Coil 3 (dont support SVG pour les drapeaux, GIF).

### Dépendances principales

| Domaine | Bibliothèque | Version |
|---|---|---|
| UI | Jetpack Compose (BOM) | 2025.01.00 |
| Persistance | Room | 2.6.1 |
| Carte | MapLibre Native Android | 11.11.0 |
| Coroutines | kotlinx-coroutines | 1.9.0 |
| Sérialisation | kotlinx-serialization | 1.7.3 |
| Images | Coil 3 | 3.1.0 |
| Build | AGP | 8.7.3 |
| Langage | Kotlin | 2.1.0 |

## 8. Styling & Conventions

- Kotlin idiomatique, style officiel (celui appliqué par défaut par Android Studio / `ktlint`
  s'il est ajouté au projet).
- Composables nommés en `PascalCase`, fonctions/variables en `camelCase`.
- Un fichier par écran/composant significatif plutôt que de gros fichiers fourre-tout.
- Commentaires réservés à ce qui n'est pas évident (contraintes, contournements) ; pas de
  commentaires décrivant ce que le code fait déjà de façon lisible.

### Pièges connus de Compose

**Ne pas passer de lambda `suspend` en paramètre d'un `@Composable`.** Le plugin du compilateur
Compose réécrit la signature des composables (ajout du `Composer` et des index de changement) et
les types fonctionnels des paramètres perdent au passage leur modificateur `suspend`. Un appel de
cette lambda depuis un `LaunchedEffect` échoue alors à la compilation, avec un message trompeur
qui désigne la fonction appelée et non le paramètre :

```
Suspend function 'search' should be called only from a coroutine or another suspend function
```

Le piège est que le code paraît correct : le type déclaré est bien `suspend (String) -> List<T>`,
et l'appel a bien lieu dans une coroutine.

**Contournement retenu** : passer les *données* nécessaires à l'appel, et laisser le composable
appeler lui-même la fonction suspendue. Cf. `ui/planner/RoutePlannerBand.kt`, où `GeocodingParams`
(instance, langue, nombre de propositions) remplace la lambda de recherche, `StepRow` appelant
`Photon.search` directement dans son `LaunchedEffect`.

**Ne pas nommer une méthode `setX` quand la classe expose une propriété `x`.** Kotlin engendre déjà
un mutateur `setX` pour la propriété, et les deux se heurtent sur la JVM (`Platform declaration
clash`), y compris lorsque le mutateur est `private set`. Préférer un verbe : `collapse(v)` plutôt
que `setCollapsed(v)`, `moveProfileCursor(i)` plutôt que `setProfileCursor(i)`.

## 9. Workflow CI/CD

Le détail du pipeline GitHub Actions (build debug à chaque push, release signée à chaque tag,
publication du manifeste de mise à jour) est documenté dans [`WORKFLOW.md`](WORKFLOW.md). En
résumé, avant de créer un tag de release, vérifiez que le job **Build debug APK** est vert sur
votre dernier commit `main`.

Deux points à connaître avant de toucher au versionnement ou aux mises à jour :

- `versionCode` et `versionName` sont **dérivés du tag git** par `app/build.gradle.kts`
  (`git describe`), jamais codés en dur. Un build hors tag porte un suffixe descriptif
  (`0.2.0-23-gabc1234`), d'où la comparaison sur `versionCode` et non sur `versionName`.
- Le même calcul de `versionCode` est refait par la CI pour écrire le manifeste. Les deux
  doivent rester d'accord, cf. [`WORKFLOW.md` section 6](WORKFLOW.md#6-mises-à-jour-automatiques).

## 10. Issues et Discussions

- Signaler un bug ou proposer une fonctionnalité : [GitHub Issues](https://github.com/lc-4918/trailog/issues).
- Décrire le contexte, les étapes de reproduction (pour un bug) ou le cas d'usage
  (pour une fonctionnalité), et l'environnement (version Android, version de l'app).

## 11. Ressources

- [Documentation Kotlin](https://kotlinlang.org/docs/home.html)
- [Documentation Android](https://developer.android.com/docs)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [MapLibre Native Android](https://maplibre.org/maplibre-native/android/api/)
