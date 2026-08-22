# Architecture technique - Trailog

Ce document décrit la **structure** de l'application : ses modules, la règle de dépendance qui les
gouverne, les chemins d'exécution qui les traversent, et les décisions qui les ont fixés.

Il ne redit pas ce que disent les autres documents du dépôt :

| Document | Répond à |
|---|---|
| [`SPEC.md`](SPEC.md) | Ce que l'application fait, du point de vue de l'utilisateur |
| [`CONTEXT.md`](CONTEXT.md) | Pourquoi les choix ont été faits, mesures et incidents à l'appui |
| [`DEVELOPER.md`](DEVELOPER.md) | Comment compiler, où sont les fichiers |
| [`TESTS.md`](TESTS.md) | Ce que chaque test verrouille |
| [`WORKFLOW.md`](WORKFLOW.md) | Intégration continue, signature, publication |
| **`ARCHITECTURE.md`** | **Comment c'est bâti, et ce qui doit le rester** |

Version décrite : 0.14.1. Chiffres relevés sur le dépôt à cette version.

---

## Table des matières

1. [Contexte système](#1-contexte-système)
2. [Contraintes d'architecture](#2-contraintes-darchitecture)
3. [Vue des modules](#3-vue-des-modules)
4. [Responsabilités et invariants](#4-responsabilités-et-invariants)
5. [Vues dynamiques](#5-vues-dynamiques)
6. [Modèle de persistance](#6-modèle-de-persistance)
7. [Concurrence et cycle de vie](#7-concurrence-et-cycle-de-vie)
8. [Contrats avec les services externes](#8-contrats-avec-les-services-externes)
9. [Attributs de qualité](#9-attributs-de-qualité)
10. [Décisions d'architecture](#10-décisions-darchitecture)
11. [Risques et dettes connues](#11-risques-et-dettes-connues)
12. [Points d'extension](#12-points-dextension)

---

## 1. Contexte système

Application Android native mono-processus, sans composant serveur propre. Elle consomme des services
publics tiers et n'expose aucune interface réseau.

```
                        +---------------------------+
   fichiers GPX/KML     |                           |     tuiles raster/vecteur
   d'autres apps  ----> |                           | <-- IGN, OSM, MapTiler...
                        |         TRAILOG           |
   capteur GPS ------>  |   (un seul processus,     | <-- Valhalla / BRouter   (itinéraires)
                        |    une seule Activity)    | <-- Photon               (géocodage)
   stockage local <---> |                           | <-- DATAtourisme / Overpass (POI)
   (Room + fichiers)    |                           | <-- IGN / OpenTopography (altitudes)
                        +---------------------------+ <-- GitHub Releases      (mises à jour)
```

**Acteurs.** Un utilisateur unique, sur son appareil. Aucun compte, aucune synchronisation, aucune
télémétrie.

**Propriété structurante.** Tous les services externes sont **facultatifs et remplaçables** : leur URL
est un réglage, et leur indisponibilité doit dégrader l'application sans la casser. Aucun n'est sur le
chemin critique du démarrage.

---

## 2. Contraintes d'architecture

Ces cinq contraintes expliquent la majorité des décisions du document. Elles sont des données d'entrée,
pas des choix.

| # | Contrainte | Conséquence architecturale |
|---|---|---|
| C1 | **Hors ligne d'abord.** L'usage cible est le terrain, sans réseau. | Tout ce qui peut être précalculé l'est ; tout ce qui peut être caché localement l'est. Aucune fonction ne doit échouer faute de réseau : elle dégrade. |
| C2 | **Aucun store.** Distribution par GitHub Releases. | L'application porte son propre mécanisme de mise à jour, et donc `REQUEST_INSTALL_PACKAGES`. |
| C3 | **minSdk 24** (Android 7.0). | Chaque usage d'API récente doit prévoir le chemin ancien. Pas de `Modifier.blur` avant Android 12, pas d'installation par application avant Android 8. |
| C4 | **Un seul développeur**, pas de revue de code. | Les zones où une faute est *invisible* sont testées en priorité : migrations, contrat CI/application, règles de partage des sources. |
| C5 | **Services tiers publics, à quota.** | Le nombre de requêtes est une préoccupation d'architecture, pas d'optimisation. Les règles qui le décident vivent dans du code pur et testé. |

---

## 3. Vue des modules

### 3.1 Empilement

```
+--------------------------------------------------------------+
|  ui/                              65 fichiers, 17 700 lignes  |
|  Écrans Compose, porteurs d'état, effets, composants          |
+--------------------------------------------------------------+
        |                      |                       |
        v                      v                       v
+------------------+  +-----------------+  +------------------------+
| data/repo        |  | domain/         |  | services               |
| TrailogRepository|  | 15 fichiers     |  | poi/ routing/ geocode/  |
| (façade unique)  |  | Kotlin pur      |  | elevation/ map/        |
+------------------+  | ZÉRO Android    |  | location/ net/ update/ |
        |             +-----------------+  +------------------------+
        v                      ^                       |
+------------------+           |                       |
| data/db (Room)   |           +-----------------------+
| data/imp         |             (les services utilisent le domaine)
| data/backup      |
+------------------+
```

### 3.2 Règle de dépendance

**`domain/` ne connaît rien.** Aucun import `android.*`, `androidx.*`, ni Compose. C'est la règle la
plus importante du dépôt, et la seule dont la violation se détecte mécaniquement :

```bash
grep -rn "^import android" app/src/main/java/fr/lc4918/trailog/domain/   # doit rendre zéro ligne
```

Les autres dépendances autorisées :

| Depuis | Vers | Autorisé |
|---|---|---|
| `ui/` | tout | oui |
| `data/repo` | `data/db`, `data/imp`, `domain/`, `map/offline`, `elevation/`, `routing/` | oui |
| `data/db` | `domain/` | oui |
| services (`poi/`, `routing/`...) | `domain/`, `map/offline/TileHttp` | oui |
| `domain/` | *rien* | **non** |
| `data/*` | `ui/*` | **non**, sauf `ui/offline/OfflineDownloadRequest` (voir dette D3) |

### 3.3 Le cas particulier des règles pures dans `ui/`

Trois fichiers vivent sous `ui/` sans dépendre de Compose ni d'Android, et c'est délibéré :

- `ui/poi/PoiLoading.kt` : quand demander des points d'intérêt (zoom minimal, temporisation, marge).
- `domain/model/PoiFilters.kt`, `PoiCategory.kt` : ce qu'on demande, et la catégorie d'un lieu.
- `poi/PoiSources.kt` : quelle source répond où.

Ces trois-là décident du **nombre de requêtes envoyées aux services tiers** (contrainte C5). Ils sont
séparés de leurs effets Compose (`ui/poi/PoiEffects.kt`) exactement pour être testables en JUnit sans
émulateur. C'est le motif à reproduire pour toute règle dont une modification silencieuse aurait un
coût externe.

---

## 4. Responsabilités et invariants

### `domain/`

| Fichier | Responsabilité |
|---|---|
| `geo/TrackMath` | Distance, dénivelé, pente, lissage, décimation, échantillon interpolé |
| `geo/TrackEdit` | Couper, joindre, inverser une trace ; localiser un point sur une ligne |
| `geo/TrackMeasure` | Portion de trace entre deux points, et sa longueur |
| `geo/OffTrack` | Projection de la position sur une trace, écart |
| `geo/Format` | Distances, durées, altitudes en unités métriques ou impériales |
| `model/` | Types de données et énumérations de réglage |

**Invariants.** Pas d'entrée-sortie, pas de dépendance à Android, pas d'état mutable global. Toute
fonction y est déterministe et testable sans montage.

### `data/repo/TrailogRepository`

Façade unique vers la persistance. Seul point qui connaît à la fois la base et le système de fichiers.

**Invariants.**
- Aucune méthode `suspend` publique ne s'exécute sur le thread principal (chacune ouvre son
  `withContext(Dispatchers.IO)`).
- La géométrie n'entre jamais en base : elle va sur disque (voir section 6).
- Les fichiers dérivés (`.map`, `.prof`) sont écrits **à l'import**, jamais à l'affichage.

### `ui/routes/MainViewModel`

Le seul `ViewModel` de l'écran principal. Expose des `StateFlow` et des fonctions de commande.

**Invariants.**
- N'expose que des `StateFlow` en lecture, jamais de `MutableStateFlow`.
- Ne connaît pas Compose.
- Tout travail long passe par `viewModelScope`, donc s'annule avec le ViewModel.

### `ui/` : les trois formes récurrentes

| Forme | Signature | Rôle |
|---|---|---|
| **Porteur d'état** | `@Stable class XxxState` | Un état local et ses transitions. Sept exemplaires. Ne survit pas à la rotation. |
| **Composable-effet** | `@Composable fun XxxEffects(...)` sans émission | Le lien avec le monde extérieur, sous forme d'effets. |
| **Squelette partagé** | `@Composable fun AnchoredBubble(..., content)` | L'invariant de plusieurs sites d'appel, les variations en paramètres. |

Les porteurs qui ont besoin d'un contexte Android ou d'effets sont créés par une fonction
`rememberXxx()` qui les instancie et branche leurs effets : `rememberLocationControls`,
`rememberImportFlow`, `rememberCameraPlacement`.

---

## 5. Vues dynamiques

### 5.1 Démarrage

```
Android
  |-- TrailogApp.attachBaseContext   -> LocalePrefs.wrap (langue avant tout)
  |-- TrailogApp.onCreate            -> MapLibre.getInstance
  |                                     TrailogRepository(this)
  |                                     scope.launch { ensureSeed() }        (fonds par défaut)
  |                                     scope.launch { sweepDownloads() }    (ménage des APK)
  |
  |-- MainActivity.onCreate          -> installSplashScreen() AVANT super
  |                                     enableEdgeToEdge()
  |                                     ImportInbox.offer(urisOf(intent))
  |                                     setContent { ... }
  |
  +-- Compose                        -> AppRoot
                                          MainScreen  (collecte, effets, émission)
                                            MapLibreView -> MapView -> styleTick++
                                          SettingsScreen (superposé si demandé)
```

Point notable : `ensureSeed()` et l'affichage sont **concurrents**. L'écran se compose avant que la base
ne soit prête, d'où les valeurs de repli partout (`settings?.markerSize ?: 36`).

### 5.2 Import d'un fichier

Deux entrées, une seule suite. `ImportFlow` (`ui/routes/ImportFlow.kt`) porte les deux.

```
[bouton Importer]                    [fichier ouvert depuis une autre app]
        |                                          |
        |                                    ImportInbox.offer(uris)
        v                                          v
  askFolder()  <------------------------  LaunchedEffect(inbox) -> askFolder()
        |
        v
  ImportFolderDialog  --(dossier choisi)-->  proceed(folderId)
                                                 |
                              +------------------+------------------+
                              |                                     |
                    ImportInbox non vide                    ImportInbox vide
                              |                                     |
                    importUris(attendus)                     launchPicker()
                              |                              (permission média puis SAF)
                    onFilesEntrusted()                              |
                    (ouvre le menu)                          onFilesPicked(uris)
                              |                                     |
                              +------------------+------------------+
                                                 v
                                   vm.importLayer(bytes, nom, folderId)
                                                 v
                        TrailogRepository.importLayer
                          LayerImporter.parse (GPX / KML / GeoJSON)
                          ElevationFiller (si altitudes manquantes et réglage actif)
                          écriture .map  (GeoJSON prêt pour la carte)
                          écriture .prof (profil précalculé)
                          insertion LayerEntity
                                                 v
                                  Room réémet layers -> renderLayers -> carte
```

### 5.3 Chargement des points d'intérêt

Le chemin le plus contraint de l'application, parce qu'il touche des services à quota (C5).

```
carte immobile -> idleTick++
        v
LaunchedEffect(visible, idleTick, filters)          [ui/poi/PoiEffects.kt]
        |
        |-- zoom < MIN_ZOOM (11) ? -> tooFar(), on sort
        |-- delay(DEBOUNCE_MS = 500)                 (un geste de plus annule cet effet)
        |-- needsLoad(vue, filters, now) ?
        |     non si la vue est dans l'emprise déjà chargée
        |     non si cette zone a échoué il y a moins de RETRY_AFTER_FAIL_MS (60 s)
        v
PoiLoading.grow(vue, MARGIN = 0.25)   -> emprise élargie
        v
PoiRepository.load(...) : Flow<PoiLoad>             [poi/PoiRepository.kt]
        |
        +-- DATAtourisme    (1 à 2 requêtes)   --+
        +-- OSM groupe FOOD      (Overpass)    --+--> poiStream
        +-- OSM groupe PRACTICAL (Overpass)    --+       |
              (sémaphore : 1 requête à la fois)          |
                                                         v
        chaque source publie DÈS SON ARRIVÉE  -> PoiLoad(pois, complete = false)
        toutes arrivées                       -> PoiLoad(pois, complete = retenable)
        aucune n'a répondu                    -> PoiLoad(cache, fromCache = true)
        v
PoiState.publish(...)   -> loaded = if (complete) box else null
```

**L'invariant central** : une emprise n'est retenue comme chargée que si le chargement est **allé à son
terme**, toutes sources arrivées, aucune tronquée, aucune en échec. Un chargement interrompu ne laisse
aucune trace, et le geste suivant redemande.

### 5.4 Calcul d'itinéraire

```
[mesure depuis un point]   [planificateur]   [jonction de deux traces]
             \                   |                    /
              +------------------+-------------------+
                                 v
                  Router.route(ctx, engine, base, points, profile, prefs)
                                 |
              +------------------+------------------+
              v                                     v
      Valhalla.route                          Brouter.route
      1 requête HTTP                          1) POST /profile -> id (mis en cache)
      coût dans le serveur                    2) GET  itinéraire sous cet id
      altitudes demandées (pas 30 m)          altitudes déjà dans la géométrie
              |                                     |
              +------------------+------------------+
                                 v
                          RouteResult(meters, seconds, points)
                                 v
                 TrackMath.compute -> ComputedTrack (profil, pente)
                                 v
                 controller.setRouteLines(...)  +  panneau de profil
```

### 5.5 Téléchargement hors ligne

Producteurs-consommateur à deux canaux, imposé par la contrainte de thread de SQLite.

```
TileMath.tilesFor(bbox, z)  ou  TileMath.tilesAlong(couloir, z, rayon)
        v
tileChannel (UNLIMITED, prérempli puis fermé)
        v
6 workers sur Dispatchers.IO
   TileHttp.get(TileUrl.build(provider, x, y, z))
        v
writeChannel (capacité 256 : borne la mémoire si le disque est plus lent que le réseau)
        v
1 consommateur sur un dispatcher MONO-THREAD dédié
   MbtilesWriter : open / beginBatch / putTile / commit tous les 128 / metadata / close
        v
protocole d'arrêt :  workers.joinAll()  ->  writeChannel.close()  ->  writerJob.join()
```

**Invariant.** `MbtilesWriter` possède tout le cycle de vie du fichier sur son seul thread. Une
annulation ou une exception déclenche `abort()` (rollback puis fermeture) depuis ce même thread.

---

## 6. Modèle de persistance

### 6.1 Répartition

| Donnée | Où | Pourquoi |
|---|---|---|
| Catalogue (dossiers, couches, fonds, composites, réglages) | **Room** | Petit, relationnel, interrogeable, observable |
| Géométrie des traces | **fichier** `layers/<id>.map` | Des milliers de points ; MapLibre le lit directement par URI |
| Profil altimétrique | **fichier** `layers/<id>.prof` | Précalculé à l'import, affichage instantané |
| Images des points | **fichier** `images/` | Binaire, hors du champ d'une base |
| Cache des points d'intérêt | **Room** (`poi_cache`) | Doit être interrogé par emprise et purgé par date |
| Langue de l'interface | **SharedPreferences** | Lue avant la création de la base (`attachBaseContext`) |

### 6.2 Tables

```
folders        (id, name, parentId, sortOrder)
layers         (id, name, folderId, color, visible, sortOrder, schemaJson, bbox, hasLine, ...)
providers      (id TEXT, name, type, urlTemplate, minZoom, maxZoom, attribution, ...)
basemap_folders(id, name, sortOrder)
composites     (id, name, layersJson, ...)
settings       (id = 0, ligne unique, 85 colonnes)
poi_cache      (uuid TEXT PK, label, lat, lon, categoryKey, fetchedAt, pinned, ...)
```

`settings` est une **ligne unique** de nombreuses colonnes plutôt qu'une table clé-valeur. Le typage est
alors porté par `SettingsEntity`, et une faute de nom ne compile pas.

### 6.3 Migrations

Base en **version 58**, 42 migrations explicites conservées, aucune destruction. Ajouter un réglage
demande quatre gestes, et les quatre sont obligatoires :

1. le champ dans `SettingsEntity` ;
2. la constante SQL dans `MigrationSql` ;
3. l'objet `Migration(n, n+1)` et son enregistrement dans `addMigrations(...)` ;
4. l'incrémentation de `version = n+1`.

`fallbackToDestructiveMigration()` est présent en dernier recours, mais une migration manquante y
mènerait, c'est-à-dire à la perte des couches importées. **Ce n'est pas un filet, c'est un piège** :
toute évolution de schéma doit avoir sa migration.

> La valeur `DEFAULT` d'une colonne ajoutée est une décision produit. Elle s'applique aux bases
> existantes et détermine ce que voient les utilisateurs après mise à jour.

### 6.4 Caches

| Cache | Portée | Éviction |
|---|---|---|
| Profils décodés | mémoire, `TrailogRepository` | LRU, 6 traces |
| Textes de profils BRouter | mémoire, `Router` | aucune (5 au total, borné par construction) |
| Identifiants de profils BRouter | mémoire, `Brouter` | aucune ; volontairement pas persisté |
| Points d'intérêt | Room, `poi_cache` | 7 jours, sauf lignes `pinned` |
| Tuiles de carte | disque, MapLibre | géré par MapLibre |

---

## 7. Concurrence et cycle de vie

### 7.1 Scopes

| Scope | Durée de vie | Usage |
|---|---|---|
| `CoroutineScope(SupervisorJob() + Dispatchers.IO)` dans `TrailogApp` | le processus | Amorçage, ménage. Ne doit **jamais** porter de travail lié à un écran. |
| `viewModelScope` | le ViewModel | Tout le travail de fond de l'écran principal |
| `rememberCoroutineScope()` | la composition | Gestes de l'interface (ouvrir le menu) |
| `LaunchedEffect` | ses clés | Effets liés à un état ; **annulé et relancé** au changement de clé |

### 7.2 Dispatchers

- `Dispatchers.Main` : par défaut dans un composable et un `LaunchedEffect`.
- `Dispatchers.IO` : réseau, disque, base. Ouvert par `withContext` au plus près de l'appel bloquant.
- `Dispatchers.Default` : calcul (`TrackMath.compute` sur plusieurs milliers de points).
- Dispatcher mono-thread dédié : écriture MBTiles (contrainte SQLite).

### 7.3 Annulation

L'annulation est coopérative : elle prend effet au prochain point de suspension. Deux conséquences
traitées explicitement dans le code :

- **`ensureActive()`** dans les boucles de travail long (workers de téléchargement).
- **`runInterruptible { }`** autour des appels `HttpURLConnection`, qui ne s'aperçoivent pas d'une
  annulation et continueraient de consommer les créneaux du service.

### 7.4 Ce qui survit à l'écran

| Élément | Survit à | Mécanisme |
|---|---|---|
| Suivi de position | l'écran éteint, l'app en arrière-plan | `LocationService`, service de premier plan ; l'écran lit `LocationHub` |
| État du ViewModel | la rotation | `ViewModel` |
| Ouverture des réglages | la rotation, la mort du processus | `rememberSaveable` |
| `MapView` | la sortie de composition | `destroyOnDispose = false` par défaut |
| Porteurs d'état d'écran | rien | `remember` seul, et c'est assumé |

---

## 8. Contrats avec les services externes

Aucun de ces services n'offre de garantie de disponibilité. L'architecture traite donc l'échec comme un
cas nominal, jamais comme une exception.

| Service | Usage | URL réglable | Dégradation |
|---|---|---|---|
| Tuiles (IGN, OSM, MapTiler...) | Fond de carte | oui, catalogue | Cache MapLibre, puis fond gris |
| **Valhalla** ou **BRouter** | Itinéraires | oui | `null` ; l'écran annonce l'échec |
| **Photon** | Géocodage direct et inverse | oui | Deux tentatives, puis état `Failed` affiché |
| **DATAtourisme** | Points d'intérêt (France) | oui | Liste vide, cache pris en relais |
| **Overpass** | Points d'intérêt (monde, et restauration en France) | oui | `null` distinct d'une liste vide, frein de 60 s |
| **IGN** / **OpenTopography** | Altitudes manquantes à l'import | oui | Trace importée sans altitude |
| **GitHub Releases** | Mises à jour | non | Vérification silencieuse ; aucun message |

### 8.1 La leçon d'Overpass

Le quota est une préoccupation d'architecture. Relevé sur le terrain : **huit gestes de carte,
vingt-cinq requêtes, vingt-cinq refus de connexion**. Trois mécanismes s'appliquent désormais, et ils
sont indissociables :

1. **Une requête simultanée** au plus (`PoiRepository.OSM_CRENEAUX = 1`), l'instance publique n'en
   accordant que deux par adresse.
2. **Un délai de reprise** de 60 s après échec (`PoiLoading.RETRY_AFTER_FAIL_MS`). Il ne s'applique
   qu'à l'échec : une réponse *tronquée* se redemande au geste suivant, puisque le service a répondu.
3. **`runInterruptible`**, pour que les requêtes d'un chargement annulé cessent réellement.

### 8.2 Distinguer l'absence de la panne

Un principe suivi partout, et dont l'oubli a produit des bugs réels :

| Retour | Signifie |
|---|---|
| liste vide | le service a répondu, il n'y a rien ici |
| `null` | le service n'a pas répondu, on ne sait pas |

`Overpass.around` rend `List<Poi>?` pour cette raison. `TileHttp.Response.status == 0` distingue de même
une panne réseau d'un refus HTTP.

---

## 9. Attributs de qualité

### 9.1 Hors ligne (C1)

- Géométries et profils sur disque, calculés à l'import.
- Tuiles emportables en MBTiles, qui deviennent un fond de carte comme un autre.
- Points d'intérêt emportables avec la zone (`pinned`, exemptés de la purge hebdomadaire).
- Le suivi de position et toute la mesure sur trace fonctionnent sans réseau.

### 9.2 Performance

Points chauds identifiés et traités :

| Point | Traitement |
|---|---|
| Décodage d'un profil (200 Ko de JSON, 2 à 12 s) | Précalcul à l'import, cache mémoire LRU |
| Reconstruction du style MapLibre à chaque geste | Filtre `sameStyleSettings` : seuls trois réglages la déclenchent |
| Rendu de grandes traces | Simplification à 1 m, chargement par URI `file://` |
| Recomposition trop large | Lecture des états au plus près de leur usage |
| Requêtes tierces | Temporisation, emprise élargie, mémoire de ce qui est chargé |

### 9.3 Testabilité

765 tests, 71 fichiers, **tous sur la JVM**, aucun émulateur (contrainte C4 : la CI n'en a pas).

La stratégie est de faire **descendre les règles importantes** dans du code pur pour qu'elles y
deviennent testables. `poiStream` a été extrait de `PoiRepository` uniquement pour cela : il se teste
avec deux fonctions factices, là où le dépôt entier demanderait deux services en ligne et une base.

Non couvert : `MainScreen` (une `MapView` ne se charge pas sur la JVM), le moteur de téléchargement, la
mise à jour.

### 9.4 Vie privée

Aucun compte, aucune télémétrie, aucun identifiant. Ce qui sort de l'appareil, et rien d'autre :

- l'emprise de carte regardée, vers le service de points d'intérêt ;
- les points d'un trajet à calculer, vers le moteur d'itinéraire ;
- le texte saisi, vers le géocodeur (fonction **éteinte par défaut**) ;
- les coordonnées d'un point désigné, pour son adresse.

Le suivi de position ne quitte jamais l'appareil : ni enregistré, ni envoyé, ni conservé après arrêt.

### 9.5 Sécurité

Surface d'attaque réduite : aucune interface entrante, aucune authentification, aucun secret utilisateur.

Deux points d'attention connus :

- `REQUEST_INSTALL_PACKAGES` (C2) : l'application installe ses propres mises à jour. Le manifeste est
  servi par GitHub Releases en HTTPS, et l'APK est signé par la clé de la CI. Une compromission du dépôt
  compromettrait la chaîne.
- Les clés d'API des services de tuiles et de DATAtourisme sont **en dur dans le code**, dépôt public
  compris. C'est un choix assumé pour des clés personnelles à quota gratuit ; il serait inacceptable
  pour une application distribuée en store.

---

## 10. Décisions d'architecture

Format court : contexte, décision, conséquences. Les décisions marquées *(révisée)* ont changé au moins
une fois, et l'entrée dit pourquoi.

### AD-1 : une seule Activity, pas de composant de navigation

**Contexte.** Deux écrans seulement, la carte et les réglages. La carte porte une `MapView`, coûteuse à
créer et qui perd sa position à la recréation.

**Décision.** Pas de `NavHost`. `AppRoot` superpose `SettingsScreen` à `MainScreen` dans une `Box`.

**Conséquences.** La carte n'est jamais détruite, aucun scintillement, aucune position perdue. En
contrepartie, `MainScreen` reste composé sous les réglages, et un troisième écran demanderait de
reconsidérer ce choix. `navigation-compose` figure dans les dépendances sans être utilisé (dette D1).

### AD-2 : Room pour le catalogue, fichiers pour la géométrie

**Contexte.** Une trace fait des milliers de points. MapLibre sait lire un GeoJSON par URI.

**Décision.** La base porte les métadonnées ; le disque porte la géométrie (`.map`) et le profil
précalculé (`.prof`), écrits une fois à l'import.

**Conséquences.** Affichage instantané d'un profil déjà consulté. En contrepartie, deux sources de
vérité à garder cohérentes : supprimer une couche doit supprimer ses fichiers, et une sauvegarde doit
emporter les deux (`data/backup/BackupArchive`).

### AD-3 : pas d'injection de dépendances

**Contexte.** Application mono-module, un développeur, un seul graphe d'objets.

**Décision.** Le dépôt est créé dans `TrailogApp` et récupéré par transtypage depuis les ViewModels.

**Conséquences.** Zéro configuration, zéro génération de code, graphe lisible d'un coup d'œil. En
contrepartie, remplacer le dépôt dans un test demanderait une réécriture : c'est la raison pour laquelle
les tests portent sur des règles pures plutôt que sur des composants montés (dette D2).

### AD-4 : un contrôleur impératif pour la carte

**Contexte.** MapLibre est impératif et antérieur à Compose.

**Décision.** `MapController`, objet ordinaire, détient la `MapLibreMap` et expose des verbes. L'écran
lui parle par des `LaunchedEffect` ; la carte parle à l'écran par des rappels qui incrémentent des
compteurs (`moveTick` à chaque image, `idleTick` à l'arrêt).

**Conséquences.** La frontière entre les deux mondes est en un seul endroit. En contrepartie, tout état
qui doit survivre à un rechargement de style doit figurer dans les clés d'un effet portant `styleTick` :
l'oublier fait disparaître cet élément au prochain changement de fond, silencieusement.

### AD-5 : deux sources de points d'intérêt, partagées géographiquement *(révisée)*

**Contexte.** DATAtourisme est la base publique française du tourisme ; hors de France, la couche était
vide. En France, elle ignore largement ce qui sert sur le terrain.

**Décision.** Hors de France, OpenStreetMap répond seul. En France, DATAtourisme garde l'hébergement et
les loisirs ; OpenStreetMap complète le groupe *pratique* et la *restauration*.

**Révision.** La restauration a d'abord été laissée à DATAtourisme, sur la foi d'une mesure faite sur
les **hôtels** (49 contre 38 autour de Grenoble) étendue aux restaurants sans les mesurer. Relevé sur le
centre d'Albi : **6 restaurants contre 150**, et les 6 étaient des hôtels. La couverture de la base
touristique varie de surcroît d'un facteur 70 selon la région (10 lieux de restauration à Albi, 729 à
Marseille).

**Conséquences.** Deux requêtes Overpass au plus en France au lieu d'une, d'où le réglage
*Compléter avec OpenStreetMap* pour qui préfère une carte immédiate.

### AD-6 : la catégorie d'un lieu est intrinsèque *(révisée)*

**Contexte.** Un lieu de DATAtourisme porte couramment huit classes. Un hôtel-restaurant est à la fois
`Hotel` et `Restaurant`.

**Décision initiale.** La résolution se bornait aux catégories cochées, pour qu'un lieu paraisse sous
celle que l'utilisateur avait demandée.

**Révision.** C'était une promesse fausse. Sous le seul filtre « Restaurants », le centre d'Albi rendait
six marqueurs, et les six étaient des hôtels. Le même défaut ramenait des toilettes en campings dès
qu'on décochait les toilettes.

**Décision.** `PoiCategory.of()` et `ofOsm()` parcourent **toutes** les catégories ;
`visibleDans(c, retenues)` écarte ensuite ce qui est masqué.

**Conséquences.** Un lieu dont la catégorie intrinsèque est masquée disparaît entièrement, même s'il
porte la classe d'une catégorie affichée. C'est le sens voulu du filtre.

### AD-7 : une emprise n'est retenue que si le chargement va à son terme

**Contexte.** Les sources publient chacune à son arrivée. La première suffisait à marquer l'emprise
chargée, alors que DATAtourisme répond en une seconde et Overpass en trois à trente.

**Décision.** `poiStream` émet une **clôture** quand toutes les sources ont répondu ; elle seule porte
`PoiLoad.complete`, et elle seule autorise `PoiState` à retenir l'emprise. `Overpass.around` rend `null`
quand l'instance n'a pas répondu, ce qui ne vaut pas une liste vide.

**Conséquences.** Un chargement interrompu ne laisse aucune trace et le geste suivant redemande. Sans
frein, cela produit une rafale : d'où le délai de 60 s après échec, indissociable de cette décision.
Le paramètre `complete` de `PoiState.publish` n'a **volontairement pas de valeur par défaut**, pour que
son oubli ne compile pas.

### AD-8 : le suivi de position hors de la composition

**Contexte.** Le suivi était porté par l'écran. La composition s'arrête dès que la carte n'est plus
visible, et le suivi s'arrêtait avec elle, au moment précis où il sert.

**Décision.** `LocationService`, service de premier plan, alimente `LocationHub` (un objet de
processus). L'écran n'en est qu'un lecteur, via `ui/location/LocationControls`.

**Conséquences.** Le suivi survit à l'écran éteint et à l'arrière-plan. Il impose une notification
permanente, ce qui est un contrat social autant que technique.

### AD-9 : Valhalla comme moteur par défaut

**Contexte.** Cinq disciplines à servir depuis une instance publique gratuite.

**Décision.** Valhalla, dont les cinq disciplines sortent d'une **seule** instance. OSRM imposerait cinq
serveurs, GraphHopper n'a pas d'instance publique sans clé. BRouter est offert en second choix.

**Conséquences.** `Router` normalise les deux clients derrière un même `RouteResult`. BRouter impose une
conversation en deux temps (dépôt de profil, puis calcul), et donc deux caches mémoire.

---

## 11. Risques et dettes connues

| # | Sujet | Nature | Impact |
|---|---|---|---|
| D1 | `navigation-compose` et `datastore-preferences` déclarés, non utilisés | Dette | Poids inutile, confusion à la lecture. Supprimables. |
| D2 | Aucune abstraction du dépôt | Dette assumée (AD-3) | Un test de composant monté demanderait une réécriture |
| D3 | `data/repo` importe `ui/offline/OfflineDownloadRequest` | Violation de la règle de dépendance | Le type devrait descendre dans `map/offline` |
| D4 | `SettingsScreen.kt` : 2 022 lignes | Dette | Répétitif plus que complexe ; découpable sans risque |
| D5 | Clés d'API en dur, dépôt public | Risque accepté | Quota épuisable par un tiers ; inacceptable en store |
| D6 | `MainScreen` non testé | Limite structurelle | `MapView` ne se charge pas sur la JVM |
| D7 | Dépendance à des instances publiques à quota | Risque externe | Atténué par AD-7 et le réglage d'URL, pas supprimé |
| D8 | `fallbackToDestructiveMigration()` actif | Risque | Une migration oubliée efface les données de l'utilisateur |

---

## 12. Points d'extension

Ce que l'architecture actuelle accueille sans réécriture, et ce qu'elle refuserait.

### Accueilli

| Évolution | Où intervenir |
|---|---|
| Un fond de carte de plus | `data/seed/Providers.kt`, ou l'écran d'ajout. Aucun code. |
| Un moteur d'itinéraire de plus | Une variante de `RouteEngine`, un client rendant `RouteResult`, une branche dans `Router.route`. Le compilateur signale les `when` à compléter. |
| Une source de points d'intérêt de plus | Une fonction dans `PoiRepository.osmSources` et une règle dans `PoiSources`. `poiStream` ne connaît pas le nombre de sources. |
| Une catégorie de points d'intérêt | Une entrée dans `PoiCategory`, avec ses classes et ses étiquettes OSM. Le réglage l'affiche seul, et les filtres enregistrant les catégories *masquées*, elle apparaît d'elle-même chez les utilisateurs existants. |
| Un réglage | Quatre gestes (section 6.3), plus une ligne dans `SettingsScreen`. |
| Une langue | Un dossier `values-xx/`. |

### Demanderait de reconsidérer

| Évolution | Ce qu'elle remet en cause |
|---|---|
| Un troisième écran | AD-1. La superposition ne tient plus, un vrai composant de navigation devient justifié. |
| Enregistrement de trace en direct | `LocationHub` est prêt (c'est écrit dans son en-tête), mais l'écriture périodique demande une stratégie de persistance qui n'existe pas. |
| Synchronisation entre appareils | Toute la section 6 : un modèle sans identifiant stable ni horodatage de modification. |
| Publication sur un store | AD-2 tient, mais C2 tombe, D5 devient bloquant, et `REQUEST_INSTALL_PACKAGES` doit disparaître. |
| Modularisation Gradle | AD-3 devient intenable : un graphe multi-module demande une injection explicite. |
