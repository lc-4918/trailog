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
# domain/ ne connaît ni Android ni Compose
grep -rn "^import android" app/src/main/java/fr/lc4918/trailog/domain/

# aucune couche sous ui/ ne remonte vers ui/
grep -rn "^import fr.lc4918.trailog.ui\." \
  app/src/main/java/fr/lc4918/trailog/{domain,data,map,poi,routing,geocode,elevation,location,net,update}/

# une seule classe ouvre la base
grep -rn "AppDatabase.get(" --include=*.kt app/src/main/java | grep -v data/repo
```

Les trois rendent aujourd'hui zéro ligne. Toute ligne qu'elles rendraient est une régression
d'architecture, pas un détail de rangement : une couche basse qui remonte vers l'interface rend son
test impossible sans émulateur, et son extraction impossible sans réécriture.

**Les deux premières ne voient que les imports.** Une couche haute qui construit elle-même ce qui
appartient à une couche basse leur échappe, l'import étant alors légitime dans ce sens. C'est ainsi qu'un
composable a longtemps ouvert la base sans qu'aucune vérification ne le dise, d'où la troisième commande,
qui cherche un appel et non un import.

Les autres dépendances autorisées :

| Depuis | Vers | Autorisé |
|---|---|---|
| `ui/` | tout | oui |
| `data/repo` | `data/db`, `data/imp`, `domain/`, `map/offline`, `elevation/`, `routing/` | oui |
| `data/db` | `domain/` | oui |
| services (`poi/`, `routing/`...) | `domain/`, `map/offline/TileHttp` | oui |
| `domain/` | *rien* | **non** |
| `data/*`, services | `ui/*` | **non**, sans exception |

### 3.3 Le cas particulier des règles pures dans `ui/`

Trois fichiers vivent sous `ui/` sans dépendre de Compose ni d'Android, et c'est délibéré :

- `ui/poi/PoiLoading.kt` : quand demander des points d'intérêt (zoom minimal, temporisation, marge).
- `domain/model/PoiFilters.kt`, `PoiCategory.kt` : ce qu'on demande, et la catégorie d'un lieu.
- `poi/PoiSources.kt` : quelle source répond où.

Ces trois-là décident du **nombre de requêtes envoyées aux services tiers** (contrainte C5). Ils sont
séparés de leurs effets Compose (`ui/poi/PoiEffects.kt`) exactement pour être testables en JUnit sans
émulateur. C'est le motif à reproduire pour toute règle dont une modification silencieuse aurait un
coût externe.

Le même geste vaut pour le calcul enfermé dans un ViewModel. Quatre fichiers en sont sortis, tous purs et
tous testés, et c'est ce qui rend l'absence d'injection tenable (AD-3) :

| Fichier | Ce qu'il décide |
|---|---|
| `ui/routes/StyleSettings` (`sameStyleSettings`) | Quels réglages imposent de reconstruire le style de carte |
| `ui/routes/TreeReorder` (`droppedOrder`) | Où se pose un élément lâché dans une arborescence. Le calcul était **écrit deux fois**, pour le menu et pour le catalogue des fonds |
| `ui/routes/DisplayedBasemaps` | Quels fonds sont réellement à l'écran, donc quelle légende proposer |
| `ui/routes/NearestTracks` | Combien de couches ouvrir pour trouver la trace la plus proche, et combien en proposer |

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

Façade vers la persistance : la base et le système de fichiers.

**Invariants.**
- **Seule classe qui ouvre la base.** Room rend un singleton, si bien qu'ouvrir ailleurs ne dupliquait
  rien - mais trois classes de l'interface le faisaient, dont un composable, et la couche haute
  connaissait alors Room aussi bien que la couche basse. La règle se vérifie mécaniquement
  (cf. section 3.2).
- **La façade n'est pas étanche, et c'est assumé** : les DAO sont offerts tels quels, plutôt que
  réécrits en trente méthodes de délégation. Ce qui compte est qu'un seul objet *ouvre* la base et
  possède la disposition des fichiers ; qui lit une table ensuite le fait par lui.
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
      Valhalla.route                          Brouter.route  (LE DEFAUT)
      1 requête HTTP                          1) POST /profile -> id (mis en cache)
      coût figé dans le serveur               2) GET  itinéraire sous cet id
      altitudes demandées (pas 30 m)          coût dans le profil envoyé
                                              altitudes déjà dans la géométrie
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

**Le repli destructeur est borné.** `fallbackToDestructiveMigrationFrom(1..15)` ne couvre plus que les
versions antérieures à la première migration écrite, pour lesquelles aucun chemin n'existe et aucun ne
sera écrit. Partout ailleurs, un chemin manquant fait désormais **échouer l'ouverture** au lieu d'effacer
en silence : un plantage au démarrage se corrige par une mise à jour, des couches effacées ne reviennent
pas.

Ce durcissement n'est tenable que parce qu'un test garde la chaîne. `MigrationChainTest` vérifie qu'aucune
version ne manque entre la plus ancienne et celle que déclare le schéma, qu'aucune ne se déclare deux fois,
et que la chaîne atteint bien la version courante. Il attrape les deux oublis symétriques - la migration
écrite mais non enregistrée, et la migration enregistrée sans incrémenter la version - qui sont exactement
ce qui menait au repli destructeur.

La version du schéma est pour cela une constante nommée (`DB_VERSION`) et non un littéral dans
l'annotation : `@Database` a une rétention binaire, sa valeur n'est pas lisible à l'exécution, donc pas
vérifiable.

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
| Suivi de position | l'écran éteint, l'app en arrière-plan, le capteur coupé puis revenu | `LocationService`, service de premier plan, seul propriétaire du capteur ; l'écran lit `LocationHub` |
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
| **BRouter** (défaut) ou **Valhalla** | Itinéraires | oui | `null` ; l'écran annonce l'échec |
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

847 tests, 79 fichiers, **tous sur la JVM**, aucun émulateur (contrainte C4 : la CI n'en a pas).

La stratégie est de faire **descendre les règles importantes** dans du code pur pour qu'elles y
deviennent testables. `poiStream` a été extrait de `PoiRepository` uniquement pour cela : il se teste
avec deux fonctions factices, là où le dépôt entier demanderait deux services en ligne et une base.

`MainScreen` échappait à cette stratégie, parce qu'il ne calcule presque rien : il **câble**. Les réglages
aux ornements de la carte, les gestes de la carte aux infobulles, les modes de saisie exclusifs entre eux.
Rien de cela ne descend nulle part, et rien n'en était vérifié - un seul appel, `MapLibreView`, rendait
les 1800 lignes incomposables sur la JVM. La surface de carte est passée en paramètre (`MapSurface`) et
l'écran entier se compose désormais en test, avec sa vraie base, ses vrais réglages et son vrai ViewModel.

Ces 1800 lignes sont depuis retombées à moins de mille, réparties par sujet : les grappes de boutons
(`MapControls`), les infobulles (`MapBubbles`), les bannières (`MapNoticeLayer`), l'aiguillage des taps
(`MapTapRouting`), les `BackHandler` et leur ordre (`MapBackHandlers`), les battements de la caméra
(`MapCameraCallbacks`). Ce qui n'a **pas** été extrait est aussi instructif : la bande du planificateur et
le panneau de profil sont des appels uniques de quinze à vingt arguments nommés, et les envelopper aurait
déplacé ces arguments en ajoutant autant de paramètres, pour un gain nul.

Reste hors d'atteinte, et pour de bon : le rendu des tuiles, les gestes réels, et tout ce qui **s'ancre à
un point de carte** - la position à l'écran vient de la projection de MapLibre, qui n'existe pas sans
carte. Le moteur de téléchargement et la mise à jour ne sont pas couverts non plus.

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

Chaque décision est présentée dans le même ordre : **le problème** qui force un choix, **les options**
qu'on avait, **la décision**, ce qu'elle **coûte**, et **ce qui la ferait tomber**.

Ces entrées décrivent l'état **courant**. Plusieurs de ces décisions en ont remplacé une autre ; le
cheminement, les mesures qui l'ont imposé et les fautes relevées en route sont dans
[`CONTEXT.md`](CONTEXT.md), dont c'est l'objet.

Les problèmes qui viennent d'Android sont expliqués : ce document doit se lire sans connaître la
plateforme.

### AD-1 : une seule Activity, pas de composant de navigation

**Le problème.** Une `Activity` est un écran, au sens d'Android : le système la crée, la met en pause, la
détruit et la recrée à sa guise - une simple rotation d'écran suffit. Une application à plusieurs écrans
utilise donc d'ordinaire un composant de navigation, qui empile et dépile ces écrans.

Trailog a deux écrans : la carte et les réglages. Mais la carte porte une `MapView`, un objet lourd du SDK
cartographique, coûteux à créer et qui perd sa position quand on le recrée.

**Les options.** Un vrai composant de navigation (`navigation-compose`), qui détruit l'écran quitté. Ou
superposer le second écran au premier, qui reste vivant dessous.

**Décision.** La superposition. `AppRoot` empile `SettingsScreen` par-dessus `MainScreen` dans une `Box`,
qui est l'équivalent d'un `FrameLayout` : les enfants se recouvrent, le dernier déclaré au-dessus.

**Ce qu'elle coûte.** `MainScreen` reste composé sous les réglages, donc en mémoire, et ses effets
continuent de tourner. C'est le prix d'une carte qui ne scintille pas et ne perd pas sa position.

**Ce qui la ferait tomber.** Un troisième écran. À deux, une `Box` se lit ; à trois, on réinvente une pile
de navigation en moins bien.

### AD-2 : Room pour le catalogue, fichiers pour la géométrie

**Le problème.** Une trace GPX fait des milliers de points. Où les ranger ?

Room est la couche de correspondance objet-relationnel d'Android, l'équivalent réduit de JPA au-dessus de
SQLite. Y mettre les points imposerait, à chaque affichage, une requête, une désérialisation et un tri -
alors que le moteur de carte sait lire un fichier GeoJSON directement, par son chemin.

**Les options.** Tout en base, avec le coût de lecture à chaque affichage. Ou séparer : les métadonnées en
base, la géométrie en fichier.

**Décision.** La séparation. La base porte le catalogue - nom, dossier, couleur, emprise, statistiques.
Le disque porte deux fichiers dérivés par couche, écrits **une seule fois, à l'import** : un GeoJSON prêt
pour la carte (`.map`) et un profil altimétrique précalculé (`.prof`).

**Ce qu'elle coûte.** Deux sources de vérité à garder cohérentes. Supprimer une couche doit supprimer ses
fichiers ; une sauvegarde doit emporter les deux (`data/backup/BackupArchive`). Un fichier orphelin ne
casse rien mais occupe de la place ; une ligne sans fichier casse l'affichage.

**Ce qui la ferait tomber.** Un besoin d'interroger la géométrie - « quelles traces passent dans ce
rectangle » se répond aujourd'hui par l'emprise stockée en base, ce qui suffit. Une vraie recherche
spatiale demanderait une extension SQLite.

### AD-3 : pas d'injection de dépendances

**Le problème, et il vient d'Android.** Sur un serveur, un conteneur comme Spring construit vos objets et
remplit leurs constructeurs. Sur Android, **c'est le système qui instancie vos classes**, et il appelle
des constructeurs **sans argument** :

```
Activity a = MainActivity.class.newInstance();   // en substance
a.onCreate(...);
```

Il en va de même pour les `Service` et pour les `ViewModel`, ces derniers étant créés par le framework
pour survivre à la rotation de l'écran. On ne peut donc pas écrire `MainActivity(repository)` : personne
n'appellerait ce constructeur.

C'est exactement le trou que Dagger et Hilt, les conteneurs d'injection d'Android, viennent boucher : ils
génèrent le code qui va chercher les dépendances et les pose dans des champs, faute de pouvoir passer par
un constructeur.

**Les options.** Ajouter Hilt - un processeur d'annotations, des modules de configuration, du code
généré. Ou se passer de conteneur et câbler à la main.

**Décision.** À la main. Il existe un objet qu'Android crée **avant tout le reste et une seule fois par
processus** : la classe `Application`. Le dépôt y vit, et les quatre classes qui en ont besoin l'y
prennent par transtypage :

```kotlin
// dans TrailogApp.onCreate
repository = TrailogRepository(this)

// dans MainViewModel, SettingsViewModel, MainActivity, LocationService
private val repo = (app as TrailogApp).repository
```

Quatre lignes dans tout le projet.

**Ce qu'elle coûte, et la mesure qui a tranché.** L'argument pour un conteneur est la testabilité :
pouvoir substituer un faux dépôt, comme un `@MockBean`. Mesuré, il ne tient pas ici :

- le dépôt **est** testé, directement et bout en bout. `TrailogRepositoryTest` monte une vraie base et de
  vrais fichiers sous Robolectric, sans la moindre abstraction ;
- ce qui mérite un test dans un `ViewModel` n'est jamais l'orchestration mais le **calcul** qu'elle
  contient - et un calcul s'extrait au lieu de se simuler. `sameStyleSettings`, `droppedOrder`,
  `displayedProviders` et `layersToScan` ont quitté `MainViewModel` pour cette raison, et sont testés sans
  aucune doublure.

Un test avec faux dépôt aurait vérifié qu'un appel a lieu. Les tests obtenus vérifient qu'un résultat est
juste, ce qui n'est pas le même travail.

**Ce qui la ferait tomber.** Un découpage en plusieurs modules Gradle - `TrailogApp` cesserait d'être
visible depuis les modules bas, et le câblage à la main deviendrait impossible. Une seconde implémentation
du dépôt. Ou un besoin avéré de simuler une orchestration, et non un calcul.

### AD-4 : un contrôleur impératif pour la carte

**Le problème.** L'interface est écrite en Jetpack Compose, qui est **déclaratif** : on décrit à quoi
l'écran doit ressembler pour un état donné, et le framework rappelle cette description quand l'état
change. On ne met jamais rien à jour soi-même.

Le moteur de carte, lui, est une bibliothèque Android classique, **impérative** et antérieure à Compose :
on lui donne des ordres - ajoute cette couche, déplace la caméra. Et sa vue est coûteuse : la recréer
coûte un écran noir et une position perdue.

**Les options.** Redessiner la carte à chaque changement d'état, ce que son coût interdit. Ou isoler la
frontière entre les deux mondes en un seul endroit.

**Décision.** Un `MapController` : un objet ordinaire, sans annotation Compose, qui détient la carte et
expose des verbes (`setLayers`, `setCursor`, `fitTo`). Les deux sens sont explicites :

| Sens | Mécanisme |
|---|---|
| Écran vers carte | un effet, relancé quand son état change, qui appelle le contrôleur |
| Carte vers écran | des rappels posés sur le contrôleur, qui incrémentent un compteur d'état |

Le compteur mérite un mot : `moveTick` et `idleTick` ne portent aucune information, ils **changent**, et
ce changement suffit à déclencher ce qui doit l'être. Le premier s'incrémente à chaque image d'un
déplacement, le second seulement à l'arrêt.

**Ce qu'elle coûte.** Tout état qui doit survivre à un rechargement de style doit figurer dans les clés de
l'effet qui le pose, avec le compteur `styleTick`. Changer de fond de carte reconstruit le style, qui
emporte avec lui toutes les couches posées dessus : oublier ce compteur dans un seul effet fait
disparaître cet élément-là, silencieusement, au prochain changement de fond.

**Ce qui la ferait tomber.** Un moteur de carte nativement compatible Compose. Il n'y en a pas.

### AD-5 : deux sources de points d'intérêt, partagées géographiquement

**Le problème.** DATAtourisme est la base publique du tourisme **français**. Hors de France, la couche
était vide, sans que rien ne l'explique. En France même, elle ignore largement ce qui sert sur le terrain,
et sa couverture varie d'un facteur 70 selon la région : pour la même question, 10 lieux de restauration à
Albi, 729 à Marseille. On ne peut donc s'y fier ni partout, ni pour tout.

**Les options.** Une seule source, avec ses trous. Ou deux, et il faut alors dire qui répond où - sans
quoi on paie deux requêtes pour le même lieu, ou l'on affiche deux marqueurs pour un seul endroit.

**Décision.** Hors de France, OpenStreetMap répond seul. En France, DATAtourisme garde l'hébergement et
les loisirs ; OpenStreetMap complète le groupe *pratique* et la *restauration*. Les doublons sont écartés
à la fusion, par catégorie et par distance (50 m).

**Ce qui fonde le partage**, mesuré à emprise égale pour les deux sources :

| Catégories | DATAtourisme | OpenStreetMap |
|---|---|---|
| hôtels (Grenoble) | 49 | 38 |
| points d'eau, toilettes, pique-nique (Grenoble) | 0 | 200 |
| restaurants (centre d'Albi) | **6**, tous des hôtels | **150** |
| bars, cafés (centre d'Albi) | 1 | 23 |

Un restaurant de quartier n'est pas un objet touristique : il n'entre dans cette base que s'il est adossé
à un hébergement. Les hôtels et les loisirs, eux, y sont bien décrits et illustrés de photos.

**Ce qu'elle coûte.** Deux requêtes Overpass au plus en France au lieu d'une. D'où le réglage
*Compléter avec OpenStreetMap*, pour qui préfère une carte immédiate à une carte complète.

**Ce qui la ferait tomber.** Une amélioration nette de la couverture de DATAtourisme - vérifiable en
rejouant la mesure d'Albi.

### AD-6 : la catégorie d'un lieu est intrinsèque

**Le problème.** Un lieu de DATAtourisme porte couramment huit classes à la fois. Un hôtel-restaurant est
`Hotel` **et** `Restaurant` ; des toilettes publiques y sont aussi `CampingAndCaravanning`. L'application
n'affiche qu'une catégorie par marqueur : laquelle ?

**Les options.** Choisir celle que l'utilisateur a demandée - le lieu paraît alors sous le filtre actif.
Ou choisir selon un ordre fixe, indépendant du filtre.

La première est tentante et elle est fausse : sous le seul filtre « Restaurants », le centre d'Albi rend
six marqueurs, et **les six sont des hôtels** - n'ayant plus que cette issue, ils s'affichent en
restaurants. Le même raisonnement ramène des toilettes en campings dès qu'on décoche les toilettes.

**Décision.** L'ordre fixe. `PoiCategory.of()` parcourt **toutes** les catégories, puis `visibleDans()`
écarte ce qui est masqué. Ce qu'un lieu **est** ne dépend plus de ce qu'on a demandé.

**Ce qu'elle coûte.** Un lieu dont la catégorie intrinsèque est masquée disparaît entièrement, même s'il
porte la classe d'une catégorie affichée. C'est le sens qu'on veut donner au filtre : masquer les
restaurants masque les restaurants, et rien d'autre ne vient prendre leur place.

**Ce qui la ferait tomber.** Rien de prévisible. L'ordre de résolution, lui, peut évoluer et est documenté
dans `PoiCategory.ORDRE_DE_RESOLUTION`.

### AD-7 : une emprise n'est retenue que si le chargement va à son terme

**Le problème.** Les deux sources publient chacune dès qu'elle répond, pour que la carte se peuple sans
attendre la plus lente. Mais l'écran retient l'emprise chargée pour ne pas la redemander au moindre geste
- et il la retenait **dès la première réponse**. Or DATAtourisme répond en une seconde et Overpass en met
trois à trente.

Conséquence relevée sur Albi : un geste de plus annulait le chargement entre les deux, la vue suivante
était contenue dans l'emprise « chargée », et plus rien n'était redemandé. La carte restait sur les seuls
lieux de la source rapide, définitivement.

**Les options.** Ne jamais retenir d'emprise, et payer une requête à chaque geste. Ou distinguer un
chargement terminé d'un chargement en cours.

**Décision.** Une **émission de clôture** : quand toutes les sources ont répondu, le flux émet une
dernière fois, et elle seule porte `PoiLoad.complete`. Seule cette émission autorise à retenir l'emprise.
En complément, `Overpass.around` rend `null` quand l'instance n'a pas répondu, ce qui ne vaut pas une
liste vide.

**Ce qu'elle coûte, et le piège qu'elle a ouvert.** Une emprise en échec n'étant plus retenue, chaque
geste relançait la requête que le service venait de refuser. Relevé sur le terrain, traces à l'appui :
**huit gestes de carte, vingt-cinq requêtes, vingt-cinq refus de connexion**. Le service ne refusait pas
une requête trop lourde, il refusait l'appelant. La décision est donc indissociable de son frein : 60 s
avant de redemander une zone en échec, et une seule requête simultanée.

Le paramètre `complete` de `PoiState.publish` n'a **volontairement pas de valeur par défaut** : son oubli
ne compile pas.

**Ce qui la ferait tomber.** Une source à quota illimité, qui rendrait le frein inutile - pas la clôture,
qui reste juste.

### AD-8 : le suivi de position hors de la composition

**Le problème, et il vient d'Android.** L'interface Compose vit dans une *composition*, qui s'arrête dès
que l'écran n'est plus visible. Le suivi de position y était porté : téléphone en poche, écran éteint, il
s'arrêtait - au moment précis où il sert.

Android n'offre qu'une façon de faire tourner du travail en arrière-plan sans se faire tuer : un **service
de premier plan**, qui doit afficher une notification permanente et non masquable.

**Les options.** Garder le suivi dans l'écran et demander à l'utilisateur de ne pas éteindre. Ou sortir le
suivi de l'interface.

**Décision.** `LocationService`, service de premier plan, écoute le capteur et publie dans `LocationHub`,
un objet de processus. L'écran n'en est plus qu'un **lecteur**, via `ui/location/LocationControls`.

**Ce qu'elle coûte.** Une notification permanente, que l'utilisateur ne peut pas masquer. C'est un contrat
social autant que technique : il voit en permanence que quelque chose tourne, et peut l'arrêter d'un tap.

**Ce qui la ferait tomber.** Rien. C'est ce montage qui rendra possible l'enregistrement de trace, comme
le dit l'en-tête de `LocationHub`.

**Ce qu'elle ne réglait pas, et qu'un essai sur le terrain a montré.** Le service tient le suivi en vie ;
il ne disait pas ce qui arrivait quand il s'arrêtait quand même. Un testeur a fait **vingt kilomètres dans
le mauvais sens** : son repère avait disparu, il l'avait vu, et rien ne lui a appris que l'application ne
savait plus où il était. Quatre règles en découlent, désormais tenues par le code.

**Un arrêt demandé et un arrêt subi ne sont pas la même chose.** `LocationHub` porte l'intention
(`wanted`) à côté de l'état (`tracking`). Ce qui suit un tap sur le bouton est le silence ; ce qui suit une
localisation coupée ou un service tué est une **annonce** - une notification qui sonne, sur son propre
canal, parce que la bannière de la carte attend qu'on rallume l'écran, c'est-à-dire après les vingt
kilomètres.

**Le capteur appartient au service, de bout en bout.** L'écran coupait lui-même le suivi quand la
localisation s'éteignait, et personne ne le rallumait : une coupure d'une seconde - l'économie d'énergie
d'un Samsung en fin de batterie - l'arrêtait pour le reste de la sortie. Le service **attend** le capteur
au lieu de renoncer, et se rebranche seul. Il ne rend plus `START_NOT_STICKY` sur une absence de capteur,
qui est un état passager ; il ne le fait plus que sur l'autorisation manquante, qui n'en est pas un.

**Un réglage qui n'a pas encore répondu n'est pas un réglage éteint.** `settings` vaut `null` le temps
d'ouvrir la base, et cette fenêtre se rouvre à chaque recréation du ViewModel - donc après une mort du
processus, en pleine sortie. Trois lectures y traitaient l'inconnu comme un « non » quand le défaut du
réglage est « oui » : la carte ne portait plus que le burger, ni bouton GPS ni itinéraire. C'est l'autre
moitié du témoignage, celle que le service n'explique pas.

**Un repère figé est un repère qui ment.** `Fix` porte son heure de réception, sur l'horloge de l'appareil
et non l'heure murale. Au-delà de trente secondes sans mesure, le repère passe au gris et la carte le dit.

### AD-9 : deux moteurs d'itinéraire, BRouter par défaut

**Le problème.** Calculer un itinéraire demande un graphe routier et un modèle de coût, soit bien plus que
ce qu'un téléphone peut porter. Il faut donc un service distant, et l'application doit servir **cinq
disciplines** : route, gravel, VTC, VTT, à pied.

**Les options, et ce qui les départage.**

| Moteur | Écarté ou retenu |
|---|---|
| OSRM | Écarté : impose **un serveur par profil**, soit cinq à héberger |
| GraphHopper | Écarté : pas d'instance publique sans clé d'API |
| **Valhalla** | Retenu : les cinq disciplines sortent d'une **seule** instance |
| **BRouter** | Retenu ensuite, et devenu le défaut |

Les deux retenus ne répondent pas à la même question. Valhalla porte son modèle de coût **dans son code**
et n'en expose qu'une poignée de curseurs. BRouter lit un **profil** - un texte qu'on écrit et qu'on
envoie avec la requête, décrivant le coût étiquette par étiquette.

**Décision.** Les deux sont offerts, et **BRouter est le défaut**, y compris sur une installation en place
(la migration écrit `DEFAULT 'brouter'`).

**Pourquoi BRouter est le défaut**, et c'est une mesure et non une préférence : à pied, Moulin-Neuf -
Mirepoix passe de **15 à 87 %** de voies douces, la voie verte étant enfin empruntée, et Revel - Sorèze de
**24 à 57 %** de chemins. Ce que Valhalla interdit de dire - privilégier un sentier au marcheur, accepter
une voie verte gravillonnée - s'écrit dans un profil BRouter.

**Ce qu'elle coûte.** Une conversation en deux temps avec BRouter : on dépose le texte du profil, le
service rend un identifiant, puis on demande l'itinéraire sous cet identifiant. D'où deux caches mémoire,
l'un pour les textes de profils, l'autre pour les identifiants rendus. Et cinq profils à tenir, un par
discipline. Valhalla reste offert d'un tap : son graphe est hiérarchique, donc plus rapide sur les longues
distances.

`Router` normalise les deux clients derrière un même `RouteResult`, ce qui rend la comparaison honnête :
on demande la même chose aux deux, dans le vocabulaire de l'utilisateur.

**Ce qui la ferait tomber.** Un modèle de coût configurable chez Valhalla, ou une instance BRouter
publique qui cesserait de répondre - le réglage d'URL permettant alors d'en viser une autre.

## 11. Risques et dettes connues

Les entrées ci-dessous sont **ouvertes**. Ce qui a été corrigé n'y figure plus : le détail des
corrections vit dans l'historique git et, pour ce qui portait une leçon, dans [`CONTEXT.md`](CONTEXT.md).

Les numéros ne sont pas réattribués. Une dette fermée laisse son numéro vacant, ce qui évite qu'un renvoi
écrit ailleurs ne désigne un jour autre chose que ce qu'il visait.

| # | Sujet | Nature | Impact |
|---|---|---|---|
| D2 | Aucune abstraction du dépôt | **Compromis, pas dette** | Le dépôt est testé bout en bout (`TrailogRepositoryTest`) et la logique des ViewModels est extraite plutôt que simulée : l'absence d'abstraction ne coûte aujourd'hui aucun test. Redevient une dette aux trois conditions listées en AD-3. |
| D5 | Clés d'API en dur, dépôt public | Risque accepté | Quota épuisable par un tiers ; inacceptable en store |
| D7 | Dépendance à des instances publiques à quota | Risque externe | Atténué par AD-7 et le réglage d'URL, pas supprimé |

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
