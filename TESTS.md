# Tests - Trailog

Ce document recense les tests de l'application : ce qu'ils couvrent, ce qu'ils ne couvrent pas, et
pourquoi. Il s'adresse a qui doit ajouter un test ou comprendre pourquoi la couverture affiche le
chiffre qu'elle affiche.

- [Comment lancer les tests](#comment-lancer-les-tests)
- [Ce que couvre chaque niveau](#ce-que-couvre-chaque-niveau)
- [Couverture mesuree](#couverture-mesuree)
- [Tests unitaires](#tests-unitaires)
- [Ce que les tests ont revele](#ce-que-les-tests-ont-revele)
- [Pieges de l'infrastructure](#pieges-de-linfrastructure)

## Comment lancer les tests

```bash
./gradlew :app:testDebugUnitTest      # tests unitaires (JVM + Robolectric), aucun appareil requis
./gradlew :app:jacocoTestReport       # couverture reelle -> app/build/reports/jacoco/html/index.html
./gradlew :app:connectedDebugAndroidTest   # instrumentation et e2e (appareil ou emulateur requis)
```

## Ce que couvre chaque niveau

L'application fait 9100 lignes, dont **5293 (58 %) dans des fichiers `@Composable`**. Ces lignes sont
hors d'atteinte d'un test unitaire JVM par construction : elles n'existent qu'a l'execution, dans un
arbre de composition. Viser 90 % avec des tests unitaires est donc arithmetiquement impossible. La
repartition est la suivante.

| Niveau | Perimetre | Ou |
|---|---|---|
| **Unitaire** | logique pure et logique dependante d'Android (parsing, base, style, calculs) | `app/src/test/` |
| **Instrumentation** | composants Compose isoles, base sur un vrai SQLite | `app/src/androidTest/` |
| **e2e** | parcours utilisateur complets, application reelle | `app/src/androidTest/` |

Les tests unitaires tournent sur la JVM. Ceux qui touchent au framework Android (`android.util.Xml`,
`org.json`, Room, `SharedPreferences`) passent par **Robolectric**, qui en fournit une implementation
sur la JVM. Ceux qui n'en ont pas besoin s'en passent : ils sont plus rapides.

## Couverture mesuree

Chiffres reels, produits par Jacoco, non estimes.

| Paquet | Lignes couvertes | % |
|---|---|---|
| `data/seed` | 67/67 | 100 % |
| `data` (LocalePrefs) | 26/26 | 100 % |
| `domain/geo` | 98/101 | 97 % |
| `data/imp` | 181/199 | 91 % |
| `domain/model` | 52/59 | 88 % |
| `data/db` | 115/142 | 81 % |
| `map` (StyleBuilder) | 62/96 | 65 % |
| `data/repo` | 249/425 | 59 % |
| `map/offline` | 43/282 | 15 % |
| `update` | 14/115 | 12 % |
| `ui/points` | 26/367 | 7 % |
| `ui/components` | 28/617 | 5 % |
| `ui/offline` | 6/188 | 3 % |
| `ui/routes`, `ui/settings`, `ui/profile`, `ui/nav`, `ui/theme` | 0/2473 | 0 % |

| Ensemble | Couvert | % |
|---|---|---|
| **Hors UI** (cible des tests unitaires) | 907/1543 | **58,8 %** |
| UI Compose (cible de l'instrumentation) | 60/3645 | 1,6 % |
| **Total du code source** | 967/5188 | **18,6 %** |

Le chiffre a retenir est **58,8 %**, celui du code que les tests unitaires peuvent atteindre. Le total
de 18,6 % ne dit rien de la qualite des tests : il mesure surtout la part de Compose dans l'app.

Les paquets encore bas sont `map/offline` (le moteur de telechargement, qui parle reseau et SQLite) et
`update` (DownloadManager et installateur systeme). Les deux relevent de l'instrumentation plus que du
test unitaire.

## Tests unitaires

**159 tests, 20 fichiers**, tous verts.

### `domain/geo` - calculs

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `TrackMathTest` | 3 | distance, denivele positif et negatif, pente |
| `FormatTest` | 10 | formatage des durees, distances et altitudes affichees dans le profil |

`FormatTest` couvre notamment les deux reports d'arrondi : 59 min 59 s doit donner "2 h" et non
"1 h 60 min", et le meme cas une case au-dessus pour les jours.

### `domain/model` - modele

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `BubblePositionTest` | 3 | cles stables des 10 positions, repli sur AUTO si la cle est inconnue |

Les cles sont persistees en base (`settings.bubblePosition`) : les changer casserait les reglages
existants. Une valeur inconnue doit retomber sur AUTO plutot que de planter.

### `data/imp` - import de fichiers

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `LayerImporterTest` | 19 | parsing GPX, KML, KMZ et GeoJSON ; fichiers refuses ; fichiers vides |
| `PropertyDetectorTest` | 11 | detection texte / lien / image des proprietes importees |

`LayerImporterTest` s'appuie sur de **vrais exports** places dans `app/src/test/resources/fichiers/`
(Wikiloc, OruxMaps, Locus, Google Earth), et non sur des extraits fabriques : c'est la seule facon
d'attraper les particularites de chaque producteur. Les assertions portent sur le contenu reel de ces
fichiers (nombre de points, presence d'altitude et d'horodatage, plage de coordonnees).

Cas notables :

- `test_locus.gpx` n'a **aucune** balise `<ele>`. C'est le cas 2D : il doit s'importer sans altitude,
  et c'est lui qui declenche la banniere "Parcours sans altimetrie" a l'affichage du profil.
- Le KMZ doit produire exactement la meme couche que le KML qu'il contient.
- Un fichier **vide** (lisible, sans geometrie) et un fichier **mal forme** sont deux cas distincts :
  l'utilisateur n'a rien a corriger dans le premier.

### `data/repo` - persistance

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `LayerGeoJsonTest` | 13 | aller-retour du format de stockage des couches |
| `TrailogRepositoryTest` | 9 | import bout en bout, refus, amorcage |

`LayerGeoJsonTest` protege le format de persistance : une regression y rendrait illisibles des traces
deja importees. Il verifie l'aller-retour des trois types de propriete, l'ordre des cles, l'image de
garde epinglee, et que la simplification du fichier de rendu allege la geometrie **sans deplacer les
extremites** de la trace.

`TrailogRepositoryTest` verifie qu'un fichier refuse ne laisse **aucune** trace en base ni sur le
disque, et que l'amorcage ne ressuscite pas un fond que l'utilisateur a supprime.

### `data/db` - base de donnees

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `MigrationsTest` | 11 | les 5 migrations, rejouees sur un vrai SQLite |

**Ce sont les tests les plus critiques du lot.** Une migration fautive ne casse pas le build : elle
detruit les couches importees de l'utilisateur, en silence, au premier lancement.

Le SQL n'est pas recopie dans les tests. Il vit dans `MigrationSql` (`data/db/AppDatabase.kt`), que
les migrations et les tests lisent tous deux : une copie dans le test validerait une version qui n'est
plus celle du code.

Points verifies :

- **18 vers 19** insere le fond AF3V. Sans cet INSERT, il n'existerait que sur une installation neuve,
  la table n'etant semee qu'a vide. Son `sortOrder` doit se placer apres les existants sans les
  heurter, et un rejeu ne doit pas le dupliquer.
- **20 vers 21** convertit la transparence du panneau en opacite. Sans conversion, un panneau regle a
  20 serait relu comme 20 % d'opacite et deviendrait quasi invisible. Le test balaie les 101 valeurs
  possibles pour verifier qu'aucune ne sort de la plage 30-100 du slider.

### `data` - preferences

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `LocalePrefsTest` | 8 | langue de l'interface, repli, noms natifs et drapeaux |

La langue est lue avant la creation de la base, d'ou son stockage a part en `SharedPreferences`.

### `data/seed` - catalogue de fonds

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `ProvidersTest` | 14 | integrite du catalogue livre avec l'app |

Une faute ici ne se voit qu'a l'execution, sur une installation neuve, sous forme de carte grise. Le
test verifie l'unicite des identifiants et des ordres de tri, que toutes les URL sont en **https**
(Android bloque le trafic en clair), que chaque gabarit est developpable par `TileUrl`, qu'un jeton
`{s}` s'accompagne de sous-domaines et un `{KEY}` d'un champ de cle, et que le fond par defaut existe.

### `map` - style et icones

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `StyleBuilderTest` | 11 | construction du style MapLibre |
| `BasemapIconsTest` | 4 | drapeaux des fonds nationaux, chemins des assets |
| `CompositeBasemapsTest` | 3 | aller-retour des identifiants de composite |

Une faute dans `StyleBuilder` donne une carte grise, sans message d'erreur. Le test verifie l'ordre
d'empilement (fond, surcouches, puis relief en dernier), que l'opacite d'une surcouche se retrouve
bien dans le style, et qu'un vectoriel seul est delegue par URL alors qu'avec une surcouche il faut
construire un style.

`BasemapIconsTest` verifie que **chaque** fond du groupe "Pays" a son drapeau : la table est tenue a
la main, un fond ajoute sans son entree passerait silencieusement au globe generique.

### `map/offline` - hors-ligne

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `TileMathTest` | 11 | pavage des tuiles, estimation de taille |
| `TileUrlTest` | 8 | developpement des gabarits d'URL |
| `OfflineDownloadStateTest` | 2 | modele de la demande de telechargement |

`TileMath` annonce a l'utilisateur combien de tuiles et de Mo il va telecharger. `TileUrl` est partage
par le telechargement et les miniatures ; son test verifie notamment que le gabarit
`{bbox-epsg-3857}` se developpe dans l'ordre `minx,miny,maxx,maxy` qu'impose WMS 1.3.0.

### `ui` - logique extraite des composables

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `BubblePlacementTest` | 9 | placement de l'infobulle autour du marqueur, bornes d'ecran |
| `BasemapHoverTargetTest` | 5 | cible de depot du drag & drop du gestionnaire de fonds |

Ces deux logiques ont ete **extraites** de leur composable pour devenir testables. Le drag & drop du
gestionnaire n'etait pas couvert du tout.

### `update` - mises a jour

| Fichier | Tests | Ce qui est verrouille |
|---|---|---|
| `ReleaseInfoTest` | 3 | forme du manifeste ecrit par la CI, calcul du versionCode |
| `UpdateCheckTest` | 2 | inertie en debug, distinction des trois issues |

`ReleaseInfoTest` garde un contrat entre **deux fichiers qui ne se compilent pas ensemble** : le `jq`
du workflow et le parseur Kotlin. Une divergence n'y produirait aucune erreur visible, seulement des
mises a jour qui cesseraient d'etre proposees. Il verifie aussi qu'un champ ajoute plus tard au
manifeste est ignore plutot que fatal, et que l'ordre des `versionCode` est correct.

## Ce que les tests ont revele

Ecrire ces tests a mis au jour trois choses.

**La mesure de couverture etait fausse.** Jacoco annoncait `LayerImporter` a 0 % alors que ses 19 tests
passaient. Robolectric charge les classes par son propre chargeur, sans emplacement source, et l'agent
Jacoco les ignorait. Sans `isIncludeNoLocationClasses`, le rapport aurait ete un mensonge, plus
nuisible qu'une absence de mesure. Corrige, `LayerImporter` passe a 89,8 %.

**Un KML tronque ne leve pas d'exception.** Il rend une couche vide, et l'utilisateur lit "le fichier
est vide" la ou la specification demande "invalide". Le parseur XML atteint la fin du flux sans se
plaindre des balises non fermees. Un GPX tronque, lui, leve : sa coupure tombe en general en plein
attribut. Le comportement reel est verrouille par le test
`kml tronque rend une couche vide au lieu de lever`, pour qu'il ne derive pas en silence. Corriger
demanderait de verifier que la racine s'est refermee.

**Le drag & drop du gestionnaire de fonds est correct.** Sa logique a ete extraite et testee lors de
la recherche d'un bug de deplacement de dossier signale par l'utilisateur. Les tests montrent que le
calcul de la cible de depot est juste, y compris pour un dossier imbrique deplace vers un autre
dossier. Le bug est donc ailleurs (geste, defilement, ou visibilite de la cible a l'ecran).

## Pieges de l'infrastructure

Trois reglages, sans lesquels rien ne fonctionne.

**`robolectric.properties`** (`app/src/test/resources/`) impose une `Application` neutre. La vraie
(`TrailogApp`) initialise MapLibre au demarrage, dont les bibliotheques natives ne se chargent pas sur
la JVM : tous les tests Robolectric echouaient en `UnsatisfiedLinkError`. Aucun test unitaire n'a
besoin de la carte.

**`isIncludeNoLocationClasses`** (`app/build.gradle.kts`) rend visibles a Jacoco les classes chargees
par Robolectric. Sans lui, le rapport annonce 0 % sur du code pourtant couvert.

**`isIncludeAndroidResources`** donne aux tests l'acces au manifeste, aux ressources et aux assets
reels de l'app. Sans lui, aucun test ne peut lire une chaine ou un asset.

Enfin, la base est un **singleton** : les methodes d'une meme classe de test la partagent. Un test qui
suppose un ordre de liste devient dependant des autres. `TrailogRepositoryTest` vise donc explicitement
la derniere couche inseree par son identifiant.
