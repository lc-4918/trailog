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
├─ MainActivity.kt              setContent { AppRoot() }
├─ TrailogApp.kt                Application : init MapLibre + dépôt + amorçage
├─ domain/
│  ├─ model/                    Models.kt (TrackPoint, TrackStats…), Points.kt
│  └─ geo/                      TrackMath.kt (distance, D+/D-, pente…), Format.kt
├─ data/
│  ├─ db/                       Room : Entities, DAO, AppDatabase
│  ├─ seed/Providers.kt         Fonds de carte par défaut
│  ├─ imp/                      LayerImporter (GPX/GeoJSON/KML), PropertyDetector
│  └─ repo/                     CycleRepository, LayerGeoJson, StoragePaths
├─ map/                         BasemapIcons, CompositeBasemaps, StyleBuilder (style MapLibre)
└─ ui/
   ├─ components/                MapLibreView, Avatar, ImageViewer, CompactTextField…
   ├─ profile/                   ElevationProfile (Canvas), SlopeLegend, SlopeRamp
   ├─ routes/                    MainScreen, MainViewModel
   ├─ points/                    InfoBubble, PropertyEditor
   ├─ settings/                  SettingsScreen, SettingsViewModel
   ├─ theme/                     Theme.kt
   └─ nav/                       AppRoot.kt (NavHost)
```

Voir [`SPEC.md`](SPEC.md) pour la spécification fonctionnelle détaillée.

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

Les tests unitaires vivent dans `app/src/test/java/fr/lc4918/trailog/` (ex. `TrackMathTest.kt`
pour la logique de calcul géométrique). Il n'y a pas encore de tests instrumentés (UI),
contributions bienvenues.

## 6. Workflow de contribution

1. **Fork** du dépôt, puis clone de votre fork.
2. Créer une **branche** dédiée (`git checkout -b feature/ma-fonctionnalite`).
3. Commits atomiques, messages clairs décrivant le *pourquoi*.
4. Vérifier que `./gradlew :app:assembleDebug` et les tests passent localement.
5. Ouvrir une **Pull Request** vers `main` sur `lc-4918/trailog`.
6. Revue de code, puis merge.

## 7. Architecture

- **Pattern** : MVVM, `Repository` (accès données) -> `ViewModel` (`StateFlow`) -> écrans Compose.
- **UI** : Jetpack Compose (Material 3), une seule `Activity`, navigation via Navigation Compose
  (`AppRoot.kt`, routes *main* / *settings*).
- **Carte** : MapLibre Native Android, intégrée via `AndroidView` (`MapLibreView.kt`) et pilotée
  par un contrôleur dédié (tracé, curseur, tolérance de tap).
- **Stockage** : Room (catalogue, dossiers, fournisseurs de tuiles, réglages) + fichiers
  GeoJSON pour la géométrie des traces.
- **Asynchrone** : Kotlin Coroutines.
- **Sérialisation** : kotlinx.serialization (GeoJSON).
- **Réglages** : DataStore Preferences.
- **Images** : Coil 3 (dont support SVG pour les drapeaux, GIF).

### Dépendances principales

| Domaine | Bibliothèque | Version |
|---|---|---|
| UI | Jetpack Compose (BOM) | 2025.01.00 |
| Navigation | Navigation Compose | 2.8.5 |
| Persistance | Room | 2.6.1 |
| Carte | MapLibre Native Android | 11.11.0 |
| Coroutines | kotlinx-coroutines | 1.9.0 |
| Sérialisation | kotlinx-serialization | 1.7.3 |
| Réglages | DataStore Preferences | 1.1.1 |
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

## 9. Workflow CI/CD

Le détail du pipeline GitHub Actions (build debug à chaque push, release signée à chaque
tag) est documenté dans [`WORKFLOW.md`](WORKFLOW.md). En résumé, avant de créer un tag de
release, vérifiez que le job **Build debug APK** est vert sur votre dernier commit `main`.

## 10. Issues et Discussions

- Signaler un bug ou proposer une fonctionnalité : [GitHub Issues](https://github.com/lc-4918/trailog/issues).
- Décrire le contexte, les étapes de reproduction (pour un bug) ou le cas d'usage
  (pour une fonctionnalité), et l'environnement (version Android, version de l'app).

## 11. Ressources

- [Documentation Kotlin](https://kotlinlang.org/docs/home.html)
- [Documentation Android](https://developer.android.com/docs)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [MapLibre Native Android](https://maplibre.org/maplibre-native/android/api/)
