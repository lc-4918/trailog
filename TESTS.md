# Tests - Trailog

Ce document recense les tests de l'application : ce qu'ils couvrent, ce qu'ils ne couvrent pas, et
pourquoi. Il s'adresse à qui doit ajouter un test ou comprendre pourquoi la couverture affiche le
chiffre qu'elle affiche.

- [Comment lancer les tests](#comment-lancer-les-tests)
- [Ce que couvre chaque niveau](#ce-que-couvre-chaque-niveau)
- [Couverture mesurée](#couverture-mesurée)
- [Tests unitaires](#tests-unitaires)
- [Tests d'interface](#tests-dinterface)
- [Ce que les tests ont révélé](#ce-que-les-tests-ont-révélé)
- [Pièges de l'infrastructure](#pièges-de-linfrastructure)

## Comment lancer les tests

```bash
./gradlew :app:testDebugUnitTest      # tests unitaires (JVM + Robolectric), aucun appareil requis
./gradlew :app:jacocoTestReport       # couverture réelle -> app/build/reports/jacoco/html/index.html
./gradlew :app:connectedDebugAndroidTest   # instrumentation et e2e (appareil ou émulateur requis)
```

## Ce que couvre chaque niveau

L'application fait 26031 lignes, dont **13683 (53 %) dans des fichiers `@Composable`**. Ces lignes-là
n'existent qu'à l'exécution, dans un arbre de composition : aucun test de logique ne les atteint. La
répartition est la suivante.

| Niveau | Périmètre | Où |
|---|---|---|
| **Unitaire** | logique pure et logique dépendante d'Android (parsing, base, style, calculs) | `app/src/test/` |
| **Interface** | composables isolés, joués dans une vraie composition | `app/src/test/` (`*UiTest`) |
| **Instrumentation** | base sur un vrai SQLite | `app/src/androidTest/` |
| **e2e** | parcours utilisateur complets, application réelle | `app/src/androidTest/` |

Les tests unitaires tournent sur la JVM. Ceux qui touchent au framework Android (`android.util.Xml`,
`org.json`, Room, `SharedPreferences`) passent par **Robolectric**, qui en fournit une implémentation
sur la JVM. Ceux qui n'en ont pas besoin s'en passent : ils sont plus rapides.

**Les tests d'interface tournent là aussi**, et non sur un appareil. Le choix a deux raisons. La CI
n'a pas d'émulateur - un test qui n'y tourne pas ne garde rien -, et un développeur seul ne branche pas
un téléphone pour valider une infobulle. `createComposeRule()` compose donc pour de vrai sous
Robolectric : les taps, le focus et les recompositions sont ceux de l'appareil. La contrepartie est
nette et il faut la connaître : **rien de ce qui touche MapLibre ne peut être composé ici**, ses
bibliothèques natives ne se chargeant pas sur la JVM (cf. les pièges plus bas). L'écran principal, qui
porte la carte, reste donc hors d'atteinte ; ses composables, eux, s'appellent un par un.

## Couverture mesurée

Chiffres réels, produits par Jacoco, non estimés.

| Paquet | Lignes couvertes | % |
|---|---|---|
| `net` | 12/12 | 100 % |
| `data/seed` | 110/110 | 100 % |
| `data` (LocalePrefs) | 26/26 | 100 % |
| `data/backup` | 62/62 | 100 % |
| `elevation` | 214/221 | 97 % |
| `domain/model` | 257/266 | 97 % |
| `data/imp` | 199/216 | 92 % |
| `domain/geo` | 275/302 | 91 % |
| `routing` | 253/309 | 82 % |
| `geocode` | 48/67 | 72 % |
| `ui/alert` | 65/97 | 67 % |
| `data/db` | 240/360 | 67 % |
| `poi` | 129/195 | 66 % |
| `ui/edit` | 63/96 | 66 % |
| `map` (StyleBuilder) | 121/189 | 64 % |
| `ui/poi` | 121/199 | 61 % |
| `ui/planner` | 253/436 | 58 % |
| `data/repo` | 303/618 | 49 % |
| `ui/mappoint` | 56/139 | 40 % |
| `ui/geocode` | 49/174 | 28 % |
| `update` | 29/132 | 22 % |
| `map/offline` | 76/350 | 22 % |
| `location` | 31/173 | 18 % |
| `ui/points` | 77/495 | 16 % |
| `ui/profile` | 29/301 | 10 % |
| `ui/measure` | 9/100 | 9 % |
| `ui/components` | 68/1049 | 7 % |
| `ui/offline` | 9/283 | 3 % |
| `ui/settings` | 37/1727 | 2 % |
| `ui/routes` | 44/3113 | 1 % |
| `ui/nav`, `ui/theme`, racine (`MainActivity`, `TrailogApp`) | 0/63 | 0 % |

Les quatre paquets `ui` de tête - `alert`, `poi`, `planner`, `edit` - le doivent à deux choses : leur
**logique extraite** (placement d'infobulle, transitions d'état, règles de chargement), et depuis peu
leurs **tests d'interface**, qui atteignent enfin les composables eux-mêmes. `ui/planner` est passé de
14 à 58 %, `ui/poi` de 23 à 61 %, sans qu'une ligne de production change.

| Ensemble | Couvert | % |
|---|---|---|
| **Hors UI** (cible des tests unitaires) | 2385/3645 | **65,4 %** |
| UI Compose | 880/8235 | 10,7 % |
| **Total du code source** | 3265/11880 | **27,5 %** |

Le chiffre à retenir reste celui du code que les tests de logique peuvent atteindre. Le total ne dit
rien de la qualité des tests : il mesure surtout la part de Compose dans l'application.

Les paquets encore bas sont `map/offline` (le moteur de téléchargement, qui parle réseau et SQLite) et
`update` (DownloadManager et installateur système). Les deux relèvent de l'instrumentation plus que du
test unitaire.

## Tests unitaires

**755 tests, 71 fichiers**, tous verts.

### `domain/geo` - calculs

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `TrackMathTest` | 14 | distance, dénivelé, pente, temps estimé, restant, **point courant interpolé** |
| `TrackEditTest` | 20 | couper, joindre, fusionner et inverser une trace importée |
| `FormatTest` | 14 | formatage des durées, distances et altitudes affichées dans le profil et sur les mesures de géocodage |
| `TrackMeasureTest` | 12 | rabattement d'un tap sur la trace la plus proche, échantillonnage du parcours mesuré |
| `OffTrackTest` | 10 | pré-tri des couches sur leur emprise, et bascule de l'alerte d'éloignement |

`TrackMeasureTest` verrouille les deux bouts de la mesure sur trace : un tap n'a pas à viser la ligne
au pixel (projeté orthogonal, borné aux extrémités), et le parcours mesuré est rendu à pas constant en
un nombre impair de points - c'est ce qui fait de son élément central le milieu exact de la mesure, où
l'infobulle vient s'ancrer.

`TrackMathTest` verrouille aussi le **point courant continu** : il se pose entre deux échantillons, avec
sa position, son altitude et son horaire interpolés. Sans lui, le point sautait d'un échantillon à l'autre
- jusqu'à plusieurs dizaines de mètres sur une longue trace, dont le profil n'affiche que deux mille points
- et la coupe qui s'y fiait ne pouvait tomber qu'entre deux points déjà présents.

`OffTrackTest` verrouille la **zone morte** de l'alerte d'éloignement : elle s'allume à l'écart réglé, mais
ne se lâche qu'à 80 % de celui-ci. Un test y fait osciller la position autour du seuil et compte les
bascules : une seule est admise. Sans cette marge, une position qui tremble - le lot d'un GPS de téléphone
sous couvert - rallumerait la bannière et son son toutes les deux secondes.

`TrackEditTest` couvre les seules opérations qui **modifient** une trace reçue : une faute n'y est pas
réparable, le fichier d'origine n'est plus là. Il verrouille que le point de coupe appartient aux **deux**
morceaux (sinon un trou apparaît à la jointure, compté en moins des deux côtés), qu'une coupe ne donnant
pas deux morceaux parcourables est refusée, que l'inversion **efface les horodatages** - un temps qui
recule n'est pas une trace valide - et que la fusion ne recolle pas les polylignes, ce qui ferait
apparaître entre elles une droite jamais parcourue.

Il verrouille aussi ce qui rend la coupe utilisable : le point visé est **interpolé sur le tronçon** (une
trace tracée à la règle ne porte que ses extrémités - couper "au sommet le plus proche" y revenait à ne
pouvoir couper nulle part), il n'est **pas** dupliqué quand on vise un sommet, et la jonction raccorde les
extrémités **les plus proches**, en retournant au besoin le segment enregistré à l'envers - qui perd alors
ses horodatages, pour la même raison que l'inversion.

Dans `TrackMathTest`, le temps de marche estimé (Tobler) est vérifié sur sa propriété caractéristique :
une légère descente est plus rapide que le plat. C'est ce qu'apporte cette fonction sur une moyenne, et
c'est ce qu'un mauvais signe dans la formule ferait disparaître sans rien casser d'autre.

`FormatTest` couvre notamment les deux reports d'arrondi : 59 min 59 s doit donner "2 h" et non
"1 h 60 min", et le même cas une case au-dessus pour les jours.

### `domain/model` - modèle

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `BubblePositionTest` | 3 | clés stables des 10 positions, repli sur AUTO si la clé est inconnue |
| `PlannerHistoryTest` | 10 | historique des lieux du planificateur : ordre, plafond, oubli, forme enregistrée |

Les clés sont persistées en base (`settings.bubblePosition`) : les changer casserait les réglages
existants. Une valeur inconnue doit retomber sur AUTO plutôt que de planter.

`PlannerHistoryTest` verrouille les trois règles qui font l'utilité de l'historique : le plus récent en
tête, le même lieu qui **remonte** au lieu de se dupliquer - sans quoi quelques allers-retours entre chez
soi et le col voisin rempliraient la liste de deux entrées répétées - et le plafond de huit, appliqué
**des deux côtés**, à l'ajout comme à la relecture : une base écrite par une version au plafond plus
généreux ne doit pas rendre une liste plus longue que ce que l'écran sait montrer.

L'**oubli** y est verrouillé aussi, et c'est le pendant du remplissage automatique : l'historique se remplit
tout seul de ce qu'on consulte, il faut donc pouvoir en retirer ce qu'on n'y a pas mis exprès. Oublier un
lieu inconnu ne retire rien - la croix d'une proposition déjà partie ne doit pas emporter sa voisine.

Le reste tient à la forme enregistrée, et c'est là que se joue la robustesse : le séparateur est la
tabulation parce qu'une adresse porte toujours des virgules (« Mirepoix, 09500 Ariège, France »), et une
ligne illisible est ignorée plutôt que fatale. L'historique est un confort ; il ne doit jamais empêcher le
planificateur de s'ouvrir. Mieux vaut perdre l'historique que le trajet.

### `data/imp` - import de fichiers

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `LayerImporterTest` | 19 | parsing GPX, KML, KMZ et GeoJSON ; fichiers refusés ; fichiers vides |
| `PropertyDetectorTest` | 11 | détection texte / lien / image des propriétés importées |
| `ImportInboxTest` | 8 | fichiers reçus d'une autre application : où lire l'URI, et ne l'importer qu'une fois |

`LayerImporterTest` s'appuie sur de **vrais exports** placés dans `app/src/test/resources/fichiers/`
(Wikiloc, OruxMaps, Locus, Google Earth), et non sur des extraits fabriqués : c'est la seule façon
d'attraper les particularités de chaque producteur. Les assertions portent sur le contenu réel de ces
fichiers (nombre de points, présence d'altitude et d'horodatage, plage de coordonnées).

Cas notables :

- `test_locus.gpx` n'a **aucune** balise `<ele>`. C'est le cas 2D : il doit s'importer sans altitude,
  et c'est lui qui déclenche la bannière "Parcours sans altimétrie" à l'affichage du profil.
- Le KMZ doit produire exactement la même couche que le KML qu'il contient.
- Un fichier **vide** (lisible, sans géométrie) et un fichier **mal formé** sont deux cas distincts :
  l'utilisateur n'a rien à corriger dans le premier.

### `data/backup` - sauvegarde

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `BackupArchiveTest` | 8 | contenu de l'archive, restauration en deux temps, archives refusées |

Ce sont, avec les migrations, les seuls tests dont l'échec se paie en **données perdues** : une archive
incomplète ne se découvre qu'au moment où l'on en a besoin - quand l'original n'existe plus.

Points vérifiés :

- Une sauvegarde décrit un **état complet** : ce qui n'y est pas doit disparaître à la restauration, sans
  quoi une couche supprimée avant la sauvegarde reviendrait après.
- Les **journaux** de l'ancienne base (`-wal`, `-shm`) sont retirés : ils décrivent des pages qui
  n'existent plus dans la base posée, et corromperaient celle-ci dès sa première ouverture.
- Un zip **quelconque** n'est pas une sauvegarde, et ne touche à rien : c'est l'en-tête qui les distingue.
- Une entrée nommée `../autre.db` ne doit pas faire écrire hors du dossier de travail. Une archive vient
  de l'extérieur, et c'est une faiblesse connue des lecteurs de zip.

### `data/repo` - persistance

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `LayerGeoJsonTest` | 13 | aller-retour du format de stockage des couches |
| `TrailogRepositoryTest` | 9 | import bout en bout, refus, amorçage |

`LayerGeoJsonTest` protège le format de persistance : une régression y rendrait illisibles des traces
déjà importées. Il vérifie l'aller-retour des trois types de propriété, l'ordre des clés, l'image de
garde épinglée, et que la simplification du fichier de rendu allège la géométrie **sans déplacer les
extrémités** de la trace.

`TrailogRepositoryTest` vérifie qu'un fichier refusé ne laisse **aucune** trace en base ni sur le
disque, et que l'amorçage ne ressuscite pas un fond que l'utilisateur a supprimé.

### `data/db` - base de données

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `MigrationsTest` | 55 | les migrations, et les défauts d'une installation neuve |

**Ce sont les tests les plus critiques du lot.** Une migration fautive ne casse pas le build : elle
détruit les couches importées de l'utilisateur, en silence, au premier lancement.

Le SQL n'est pas recopié dans les tests. Il vit dans `MigrationSql` (`data/db/AppDatabase.kt`), que
les migrations et les tests lisent tous deux : une copie dans le test validerait une version qui n'est
plus celle du code.

Un test y décrit ce qu'aucune migration ne dit : **les défauts d'une installation neuve**. Aucune migration
ne s'y exécute - Room crée les tables depuis l'entité, et la ligne de réglages vient des valeurs par défaut
de Kotlin. Une valeur retournée là ne casserait donc aucun test de migration, et personne ne verrait qu'un
nouvel utilisateur découvre un bouton qu'on voulait discret.

Points vérifiés :

- **18 vers 19** insère le fond AF3V. Sans cet INSERT, il n'existerait que sur une installation neuve,
  la table n'étant semée qu'à vide. Son `sortOrder` doit se placer après les existants sans les
  heurter, et un rejeu ne doit pas le dupliquer.
- **20 vers 21** convertit la transparence du panneau en opacité. Sans conversion, un panneau réglé à
  20 serait relu comme 20 % d'opacité et deviendrait quasi invisible. Le test balaie les 101 valeurs
  possibles pour vérifier qu'aucune ne sort de la plage 30-100 du slider.
- **33 vers 34** sort l'ombrage du relief du `enabled` de son fond DEM, qui ne dit plus que sa présence
  dans le gestionnaire. L'ordre des trois requêtes compte : la reprise de l'état lit le `enabled` que la
  suivante écrase. Le test rejoue les deux états de départ, et le cas d'une base sans fond DEM - un
  `SELECT` sans résultat y laisserait un NULL dans une colonne `NOT NULL`.
- **38 vers 39** retire la colonne du thème de la bande du planificateur. La seule migration qui
  **refasse une table** : SQLite ne sait supprimer une colonne qu'à partir d'Android 14, et
  l'application descend jusqu'à Android 7. Le test part donc d'une table v38 **remplie** et relit de
  part et d'autre une colonne de chaque type - une recopie qui oublie une colonne ne se voit qu'à
  l'usage, sur un réglage revenu à sa valeur par défaut.
- **40 vers 41** ramène les trois interrupteurs des boutons affichés par défaut - GPS, gestionnaire de
  fonds, planificateur - sur une base déjà en place. Le gestionnaire de fonds est celui qui manquait : la
  migration 30 vers 31 avait réglé les deux autres, jamais lui. Le test verrouille aussi ce que la
  migration ne touche **pas** : le mode d'ouverture du menu latéral, dont dépend le burger - c'est un
  geste, pas un bouton absent.
- **30 vers 31** affiche d'office les trois commandes de carte (bouton GPS, planificateur, fond des
  boutons) et donne aux fonds leur force de rendu. Le test verrouille que les TROIS réglages y passent :
  en oublier un ne casserait rien de visible, et personne ne le remarquerait avant de chercher le bouton
  manquant.
- **45 vers 46** pose les préférences de tracé, une colonne par discipline, **déjà réglées** sur les voies
  vertes - à rebours des migrations qui posent une nouveauté éteinte, parce que celle-ci répare un calcul
  qui envoyait sur la départementale longeant la voie verte. Le test compare colonne par colonne le défaut
  du SQL et celui de Kotlin : s'ils divergeaient, une base migrée et une installation neuve calculeraient
  deux itinéraires différents, et rien à l'écran ne le dirait.

### `data` - préférences

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `LocalePrefsTest` | 8 | langue de l'interface, repli, noms natifs et drapeaux |

La langue est lue avant la création de la base, d'où son stockage à part en `SharedPreferences`.

### `data/seed` - catalogue de fonds

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `ProvidersTest` | 14 | intégrité du catalogue livré avec l'app |

Une faute ici ne se voit qu'à l'exécution, sur une installation neuve, sous forme de carte grise. Le
test vérifie l'unicité des identifiants et des ordres de tri, que toutes les URL sont en **https**
(Android bloque le trafic en clair), que chaque gabarit est développable par `TileUrl`, qu'un jeton
`{s}` s'accompagne de sous-domaines et un `{KEY}` d'un champ de clé, et que le fond par défaut existe.

### `map` - style et icônes

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `StyleBuilderTest` | 11 | construction du style MapLibre |
| `BasemapIconsTest` | 4 | drapeaux des fonds nationaux, chemins des assets |
| `CompositeBasemapsTest` | 3 | aller-retour des identifiants de composite |

Une faute dans `StyleBuilder` donne une carte grise, sans message d'erreur. Le test vérifie l'ordre
d'empilement (fond, surcouches, puis relief en dernier), que l'opacité d'une surcouche se retrouve
bien dans le style, et qu'un vectoriel seul est délégué par URL alors qu'avec une surcouche il faut
construire un style.

`BasemapIconsTest` vérifie que **chaque** fond du groupe "Pays" a son drapeau : la table est tenue à
la main, un fond ajouté sans son entrée passerait silencieusement au globe générique.

### `ui` - totaux d'un dossier

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `FolderStatsTest` | 5 | totaux récursifs, et la durée qu'on n'annonce pas |

Deux fautes qui passeraient inaperçues : un total qui oublie les **sous-dossiers** ne correspond à rien de
ce que l'écran montre - le dossier applique déjà tout le reste à ce qu'il contient -, et une durée
**partielle** est plus petite que le temps réellement passé, ce qui se lit comme une sortie plus rapide
qu'elle ne l'a été. D'où la règle : pas de durée du tout si une seule trace n'est pas horodatée.

### `map/offline` - hors-ligne

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `TileMathTest` | 11 | pavage des tuiles, estimation de taille |
| `TileCorridorTest` | 8 | tuiles qui bordent un parcours, sans trou ni doublon |
| `TileUrlTest` | 8 | développement des gabarits d'URL |
| `OfflineDownloadStateTest` | 2 | modèle de la demande de téléchargement |

`TileCorridorTest` garde le téléchargement **le long d'une trace**. Ses deux fautes possibles ne se
découvrent qu'après coup, hors réseau : un couloir **troué** laisse des carrés gris au milieu de la
randonnée - d'où le parcours échantillonné par demi-tuile, faute de quoi deux sommets distants sauteraient
tout ce qui les sépare -, et un couloir qui compte **deux fois** les mêmes tuiles annonce un poids faux et
télécharge en double, ce qu'une trace qui revient sur elle-même provoque immanquablement.

Le test mesure aussi ce que le couloir fait gagner, et **comment ce gain dépend du zoom** : au zoom 14 une
tuile fait près de deux kilomètres de côté et le couloir n'économise que la moitié ; au zoom 16, où l'on
télécharge vraiment pour marcher, il coûte cinq fois moins que le rectangle englobant - et c'est là que se
trouve l'essentiel du poids.

`TileMath` annonce à l'utilisateur combien de tuiles et de Mo il va télécharger. `TileUrl` est partagé
par le téléchargement et les miniatures ; son test vérifie notamment que le gabarit
`{bbox-epsg-3857}` se développe dans l'ordre `minx,miny,maxx,maxy` qu'impose WMS 1.3.0.

### `ui` - logique extraite des composables

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `BubblePlacementTest` | 17 | placement de l'infobulle autour du marqueur, bornes d'écran, choix du coin pour le géocodage |
| `BasemapHoverTargetTest` | 5 | cible de dépôt du drag & drop du gestionnaire de fonds |
| `GeocodeSearchStateTest` | 5 | transitions de la recherche de lieu (barre de saisie, lieu retenu) |
| `MapPointStateTest` | 9 | transitions du point désigné par un appui long (adresse, mesures, mode de saisie) |
| `LayersUnderTest` | 4 | ce sur quoi porte une action de dossier : ses couches, sous-dossiers compris |
| `MeasureAnchorTest` | 5 | ancrage de l'infobulle de mesure : le milieu s'il est visible, sinon le point visible le plus proche |

Ces logiques ont été **extraites** de leur composable pour devenir testables. Le drag & drop du
gestionnaire n'était pas couvert du tout.

`BubblePlacementTest` couvre deux règles distinctes. Celle d'un marqueur : la position réglée est
respectée, et la carte se décale de ce qu'il faut quand elle ne tient pas. Celle du géocodage (lieu
cherché, point désigné du doigt) : des quatre coins possibles, on retient celui qui déplace le moins la
carte - donc aucun déplacement dès qu'un coin tient, ce qui est le cas ordinaire.

Ce que le géocodage doit faire tenir à l'écran, c'est l'épingle **et** la bulle : l'épingle monte de sa
hauteur au-dessus du point, et un appui long contre le bord haut la laissait coupée en deux. Deux cas
n'ont pas de "plus petit décalage" qui vaille et sont donc centrés : le point hors de la carte (un lieu
trouvé loin de la vue courante, qui finirait collé à un bord) et l'ensemble trop grand pour l'écran.

Ces deux états portent sur les transitions, pas sur les valeurs : ce sont elles qui se trompent sans
rien casser. Une infobulle laissée affichée recouvre la carte au moment de choisir un point ; une mesure
oubliée au changement de point affiche une distance calculée vers un autre endroit ; une épingle qui
survit à son infobulle ne se retire plus par aucun geste. `MapPointStateTest` verrouille aussi le figeage
de l'origine de la mesure depuis la position : la suivre ferait partir une requête d'itinéraire à chaque
point GPS, soit une toutes les deux secondes.

`MeasureAnchorTest` verrouille une recherche qu'on ne voit jamais échouer à moitié : elle s'écarte du
milieu des deux côtés à la fois, donc le premier point retenu est bien le plus proche du milieu, et
elle n'interroge qu'un seul point quand le milieu est déjà visible - le cas courant, sur lequel on ne
veut pas projeter des centaines de points à chaque mouvement de carte.

`LayersUnderTest` verrouille une décision, pas un calcul : une action de dossier - l'oeil, la couleur
commune, le cadrage - porte aussi sur ses sous-dossiers. S'arrêter aux couches directes ne casse rien
et ne se voit pas en test : cela laisse seulement, sous un dossier qu'on vient de colorer d'un bloc,
des sous-dossiers d'une autre couleur.

### `poi` - points d'intérêt

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `DatatourismeTest` | 18 | requête et lecture de la réponse de DATAtourisme, table des 27 catégories |
| `OverpassTest` | 13 | requête et lecture de la réponse d'OpenStreetMap, la seconde source |
| `PoiSourcesTest` | 16 | qui répond où, pour quelles catégories, le découpage par groupe et la réunion des réponses |
| `PoiStreamTest` | 12 | l'ordre d'affichage des sources, et l'aveu d'un affichage partiel |
| `PoiFiltersTest` | 13 | ce qu'on coche, ce que le service reçoit, et la forme enregistrée |
| `PoiLoadingTest` | 15 | quand redemander, et ce que la carte dit quand elle ne peut rien montrer |
| `PoiStateTest` | 7 | le message de zoom, l'attente, et ce qu'une emprise tronquée doit redemander |
| `PoiCategoryTest` | 9 | à quelle catégorie revient un lieu qui en porte plusieurs |

Ces trois-là gardent une fonction dont **aucune faute ne se voit** : un filtre mal traduit, une condition mal
écrite ou un chemin de champ inexact ne lèvent rien - ils rendent zéro point d'intérêt, et une carte sans
marqueur ressemble à une carte sans marqueur.

`DatatourismeTest` verrouille les quatre écarts entre la documentation du service et sa réponse réelle
(cf. [`CONTEXT.md`](CONTEXT.md)), chacun relevé sur un appel véritable : le ` AND ` entre conditions, le
chemin `hasTheme.key`, le paramètre `fields` sans lequel ni le thème ni l'image n'arrivent, et les champs
qui sont tantôt un objet, tantôt un tableau d'un objet. Ce dernier avait vidé la carte entière au premier
essai, sans le moindre message.

`PoiLoadingTest` garde les règles qui décident du **nombre d'appels au service** - le seul endroit d'où peut
venir un dépassement de quota. La plus importante n'est pas le délai d'attente mais la comparaison
d'emprises : tant que la vue reste dans ce qui a été chargé, il n'y a rien à redemander.

`OverpassTest` fait pour la seconde source ce que le premier fait pour DATAtourisme, et les deux fautes
muettes y sont symétriques : l'emprise s'écrit `(sud,ouest,nord,est)`, soit **l'inverse** de l'ordre de
l'autre service, et une surface dessinée en contour n'a de coordonnées que dans son `center`. Chacune, prise
seule, rend une carte vide sans lever.

`PoiSourcesTest` garde la règle de partage : hors de France, OpenStreetMap répond seul ; en France, il ne
complète que les services du terrain, le tourisme restant à la base qui l'illustre de photos. Il verrouille
aussi la réunion des deux réponses - un même lieu connu des deux bases n'est jamais pointé au même mètre, et
deux marqueurs superposés se recouvrent sans qu'on puisse ouvrir celui du dessous.

`PoiCategoryTest` part d'un cas relevé sur le terrain : des toilettes publiques affichées en "Campings et
aires de camping-car" (Souillac, 46). Leurs huit classes réelles sont recopiées dans le test, et il
verrouille les deux moitiés de la règle - le groupe pratique passe devant, **et un hôtel-restaurant reste un
hôtel**, ce qu'une priorité plus large aurait cassé.

`PoiStreamTest` garde ce qu'aucune liste finale ne montre : l'**ordre d'affichage**. Deux sources bidon dont
on choisit l'ordre d'arrivée suffisent à vérifier que chacune publie dès qu'elle répond, que la lente
n'efface pas la rapide, que chaque réponse est mise au cache sans attendre l'autre, et qu'une émission a
lieu même quand il n'y a rien - c'est elle qui apprend à l'écran que l'emprise est chargée. Depuis le
découpage d'OpenStreetMap par groupe, il verrouille aussi que le **même objet rendu par deux groupes** ne
pose qu'un marqueur, et toujours sous la même catégorie : un hôtel-restaurant répond à la requête des
hébergements comme à celle de la restauration, et doit rester un hôtel que l'une ou l'autre réponde
d'abord. Et il verrouille l'**aveu d'un affichage partiel** : une source qui bute sur son plafond le dit,
le drapeau ne se défait pas à la réponse suivante, et une zone bien rendue ne l'allume jamais.

`PoiStateTest` verrouille trois états qui se ressemblent à l'écran et ne veulent pas dire la même chose :
trop loin, en train de charger, chargé. Les deux transitions signalées comme trompeuses à l'usage y sont :
le message de zoom se lève **avant** les points, et l'attente survit à la **première** source.

`PoiFiltersTest` verrouille un choix qui se lit mal dans le code : ce sont les catégories **masquées** qui
sont enregistrées. Un réglage vierge montre alors tout, et une catégorie ajoutée par une version ultérieure
apparaît d'elle-même au lieu de rester invisible jusqu'à ce que l'utilisateur aille la chercher.

### `location` - suivi de position

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `TrackWatchTest` | 9 | l'alerte d'éloignement : ce qui la déclenche, ce qui la tait, ce qui la réarme |

Cet état vit **hors de l'écran** depuis que le suivi tourne dans un service de premier plan : c'est lui, et
non la composition, qui décide qu'il faut sonner. Une faute ici ne se voit pas à l'écran - elle se constate
à dix kilomètres, quand rien n'a prévenu.

Deux règles y sont plus fines qu'il n'y paraît. La **zone morte** : l'alerte se déclenche à l'écart réglé et
ne se lâche qu'à 80 % de celui-ci, sans quoi une position qui oscille autour du seuil - le lot d'un GPS de
téléphone sous couvert - rallumerait la bannière et son son toutes les deux secondes. Et l'**annonce unique** :
seule l'entrée en alerte se signale, le retour sous le seuil réarmant la suivante. Le son dit un
franchissement ; il ne sonne pas tant qu'on est loin.

### `geocode` - recherche de lieu

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `PhotonTest` | 21 | construction des requêtes (recherche et inverse), lecture de la réponse du géocodeur et découpage de l'adresse en morceaux |
| `ValhallaTest` | 29 | construction de la requête et lecture de la réponse du premier moteur d'itinéraire |
| `BrouterTest` | 22 | requête, réponse et table de traduction du second moteur |
| `BrouterProfilesTest` | 5 | les cinq profils réellement livrés, confrontés à la table qui les règle |
| `PolylineTest` | 5 | décodage des polylignes encodées, dont la précision propre à Valhalla |
| `GpxWriterTest` | 11 | le seul format par lequel une trace ressort de l'application |

Les deux seuls endroits où une faute serait **muette** : une URL mal formée ou un champ mal lu ne lève
rien, la liste de propositions sort simplement vide - indiscernable d'un service qui ne trouve pas.
Le test vérifie l'encodage du texte cherché, qu'une URL de base déjà paramétrée (instance derrière un
proxy) reçoit bien un `&` et non un `?`, qu'une langue que Photon ne sert pas retombe sur l'anglais
(il répond 400 au lieu de l'ignorer, ce qui rendrait la recherche entièrement muette), et qu'une
réponse illisible donne une liste vide plutôt qu'une exception.

Le géocodage **inverse** y ajoute ses propres pièges, tous muets : il n'est pas servi par le même chemin
que la recherche (`/reverse` là où celle-ci est `/api`), et ses coordonnées ne doivent pas suivre la
locale de l'appareil - une virgule décimale française séparerait à la fois les décimales et les deux
valeurs, et le service lirait tout autre chose.

Il verrouille surtout l'**absence** des paramètres `lat`/`lon`, que Photon accepte pourtant : ils
réordonnent les résultats par proximité, si bien qu'un hameau voisin passerait devant la ville du même
nom. Les rajouter paraît une amélioration ; c'en est le contraire.

`ValhallaTest` couvre la même surface pour les itinéraires, et deux pièges qui lui sont propres : les
coordonnées passent par `toString()` et non `format()`, faute de quoi une locale française écrirait
`44,56` et rendrait le JSON invalide - une faute qu'un poste anglophone ne verrait jamais ; et un total
incomplet (longueur sans durée, ou l'inverse) doit valoir "aucun itinéraire" plutôt qu'une distance de
zéro. Il verrouille aussi la correspondance des cinq disciplines avec les modèles de coût, seul endroit
du code qui parle le vocabulaire du moteur.

`BrouterTest` couvre la même surface pour le second moteur, plus deux pièges qui lui sont propres : ses
coordonnées partent en **lon,lat**, l'ordre inverse de celui de toute l'application, et le réglage n'y
tient pas dans la requête mais dans le **texte du profil** qu'elle accompagne. `BrouterProfilesTest`, lui,
ne vérifie pas du code mais des **données** - comme `DemoAssetsTest`, et pour la même raison : une variable
renommée en amont, ou mal orthographiée dans la table, laisse l'itinéraire se calculer. L'utilisateur bouge
alors ses trois réglages sans que rien ne se passe, et rien à l'écran ne le dit.

Il y garde surtout la **monture** demandée au moteur, qui ne suit pas la discipline seule mais la
discipline et le revêtement accepté. C'est elle, et non l'option qui semble faite pour cela, qui décide
si une voie verte gravillonnée est empruntée ou fuie : elle fixe la vitesse prêtée au cycliste, et cette
vitesse-là ne se règle par aucune option. Les valeurs voisines ont été mesurées une à une, sur des
trajets cités dans le code ; le test existe pour qu'on ne les "simplifie" pas sans refaire les mesures.

`GpxWriterTest` garde la sortie. Sa faute type est muette **de l'autre côté** : le fichier s'écrit sans
rien lever et ne se découvre qu'à l'ouverture, dans une autre application, souvent une fois le téléphone
rangé. Il verrouille l'ordre des éléments d'un waypoint - le schéma GPX 1.1 l'impose, et un lecteur strict
refuse le fichier entier pour une inversion -, le fait qu'une photo ou un lien ne se glisse pas dans un
champ texte sous forme de son objet Kotlin, et le point décimal en locale française.

`PolylineTest` garde la géométrie affichée sur la carte. Sa faute possible est entièrement muette :
Valhalla encode au **millionième** de degré là où l'algorithme d'origine travaille au cent-millième, et
décoder au mauvais facteur ne lève rien - le tracé s'affiche, dix fois trop loin de l'équateur. Le test
décode l'exemple canonique de la documentation Google en précision 5, puis vérifie que le défaut du code
vaut bien dix fois cela.

### `elevation` - altimétrie manquante

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `AsciiGridTest` | 9 | lecture d'une grille de terrain et interpolation entre ses cellules |
| `ElevationSourcesTest` | 14 | requêtes et réponses des services IGN et OpenTopography, calcul des emprises |
| `ElevationFillerTest` | 21 | qui est interrogé, dans quel ordre, et ce qui est écrit au bout |

Toute faute y est **muette** : la couche s'importe alors sans altitude, ce qui est exactement ce qui
arrive quand le fichier n'en portait pas.

`AsciiGridTest` garde la géométrie de la grille. Une grille lue à l'envers, ou décalée d'une
demi-cellule, ne lève rien : elle pose des altitudes voisines, et rend un profil plausible et faux. Le
test verrouille donc le sens du nord (la première ligne du fichier est la plus haute), le demi-pas qui
sépare le coin déclaré du centre de sa cellule, et le refus des trous du modèle - y compris quand la
grille ne déclare pas sa valeur d'absence, ce qui est le cas courant : les grilles Copernicus servies par
OpenTopography arrivent **sans ligne `NODATA_value`**, et un vide y passerait pour une altitude de trente
mille mètres sous la mer.

`ElevationFillerTest` remplace les services par des réponses écrites dans le test. Ce qu'il vérifie n'est
pas qu'ils répondent, mais les deux règles qui ne se voient pas à l'usage et se paient cher :

- **Le tout ou rien d'une trace.** Un point sans altitude vaut zéro dans le calcul du profil
  (`TrackMath.compute`) : une trace à laquelle il manque trois points plongerait au niveau de la mer trois
  fois, et son D+ deviendrait absurde. Une trace incomplète est donc laissée telle quelle - sans entraîner
  les traces voisines, ni les waypoints, qui se complètent chacun pour son compte.
- **L'abandon de l'IGN dès le premier paquet vide.** Le service ne connaît pas ses frontières autrement
  qu'en répondant "pas de donnée" : sans cette règle, une trace étrangère de mille points lui coûterait
  cinq requêtes pour rien avant de passer au modèle mondial.

Il verrouille aussi ce qui n'est **pas** demandé : les points qui portent déjà une altitude, le fichier
déjà complet qui ne déclenche aucune requête, et la trace trop longue pour le découpage en emprises, qui
renonce avant le premier appel plutôt que d'épuiser le quota de la clé pour un profil que la règle du tout
ou rien refuserait au bout.

### `ui` - recherche dans l'arborescence

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `TreeSearchTest` | 6 | recherche par fragment, insensible à la casse et aux accents |

Une recherche qui ne trouve pas est une faute **muette** : elle ressemble en tout point à une couche
absente, et l'utilisateur en conclut qu'il l'a supprimée. Le test couvre les huit langues de
l'application par leurs accents, la recherche sur un fragment quelconque du nom - on se souvient du
"Ventoux" bien avant du "2019-07-14 - Mont Ventoux" que l'appareil a nommé - et la recherche vide, qui ne
filtre rien.

### `ui/edit` - placement du marqueur de coupe

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `CutBubblePlacementTest` | 15 | côté libre autour du point, et maintien de la bulle à l'écran |

Deux fautes possibles, toutes deux **muettes** : une bulle posée sur la trace masque l'endroit même qu'on
examine avant de couper, et une bulle poussée hors de l'écran ne se voit pas du tout. Rien ne lève dans
l'un ni l'autre cas - l'utilisateur constate seulement qu'il ne voit pas ce qu'il vise.

Le choix se fait par **recouvrement réel** : on pose les quatre rectangles candidats et l'on compte les
tronçons de trace qui les traversent. Deux versions ont échoué avant celle-ci, et les tests gardent
maintenant leurs deux leçons :

- juger sur l'**orientation** de la trace au point visé ne vaut que pour une trace droite ; dans un lacet
  ou une boucle, le côté "opposé à la direction" tombe en plein sur une autre portion du tracé ;
- le test doit porter sur le seul **corps** de la bulle, pointe exclue. La trace passe par définition *par*
  le point de coupe : la pointe la touche donc toujours, et la compter donnait quatre côtés occupés, donc
  un choix sans objet.

Il vérifie aussi qu'un long tronçon **traversant** un candidat est vu alors qu'aucun de ses sommets n'y est,
que la bulle tient dans l'écran **dans les deux sens à la fois** lorsque le point est dans un coin, et que
son encombrement suit le côté retenu - la pointe s'ajoute en largeur sur les côtés, en hauteur en haut et
en bas.

### `net` - portée des services

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `ServiceUrlTest` | 4 | réseau local ou service externe, pour l'avertissement d'absence de connexion |

`needsInternet` décide si l'absence de connexion doit être signalée avant d'ouvrir une recherche ou de
lancer une mesure. Son test balaie les plages privées (10/8, 172.16/12 et ses deux bornes, 192.168/16,
boucle locale, lien-local, `.local`, nom de machine seul) : les compter comme externes priverait de la
fonction celui qui héberge ses propres services et se trouve en wifi sans sortie Internet, c'est-à-dire
exactement le cas que l'auto-hébergement sert à couvrir.

### `update` - mises à jour

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `ReleaseInfoTest` | 8 | forme du manifeste écrit par la CI, calcul du versionCode, choix de l'APK selon l'architecture |
| `SweepDownloadsTest` | 8 | ménage des APK de mise à jour laissés sur le disque |
| `UpdateCheckTest` | 2 | inertie en debug, distinction des trois issues |

`ReleaseInfoTest` garde un contrat entre **deux fichiers qui ne se compilent pas ensemble** : le `jq`
du workflow et le parseur Kotlin. Une divergence n'y produirait aucune erreur visible, seulement des
mises à jour qui cesseraient d'être proposées. Il vérifie aussi qu'un champ ajouté plus tard au
manifeste est ignoré plutôt que fatal, et que l'ordre des `versionCode` est correct. Depuis les splits
ABI, il verrouille aussi le choix de l'APK par architecture : une clé mal orthographiée côté workflow
(`apkurls`) laisserait la lecture réussir, la table vide, et **tous** les appareils retomberaient à
jamais sur l'APK universel - deux fois plus lourd - sans que rien ne le signale.

`SweepDownloadsTest` répare une fuite du même genre, invisible depuis l'application : `DownloadManager`
dépose l'APK d'une mise à jour dans le dossier privé, et rien ne l'en retirait. Relevé sur l'appareil
avant correction - **trois APK, 176 Mo**, près de trois fois le poids de l'application, dont deux d'une
version abandonnée. Le test garde aussi ce qu'on ne doit **pas** supprimer : les fichiers de
l'utilisateur, et le téléchargement d'une version plus récente pas encore installée.

## Tests d'interface

**20 tests, 3 fichiers.** Ils composent pour de vrai - taps, focus, recompositions - et vivent avec les
autres dans `app/src/test/`, joués par Robolectric sur la JVM (le pourquoi est plus haut).

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `PoiBubbleUiTest` | 6 | l'infobulle d'un point d'intérêt : nom, catégorie, les trois actions d'itinéraire, le lieu sans nom |
| `OffTrackAlertUiTest` | 9 | la bannière d'alerte et le choix de la trace à suivre |
| `RoutePlannerBandUiTest` | 5 | la bande du planificateur, dont le retour sur une étape déjà remplie |

`RoutePlannerBandUiTest` existe **à cause d'un plantage** : toucher le champ de départ d'un itinéraire
calculé fermait l'application, net. Une étape remplie n'affiche pas son champ mais un cadre, qui demandait
le focus à un champ non composé - le `FocusRequester` n'avait aucun noeud à saisir et levait. Aucun test de
logique ne pouvait voir cela : la faute n'était ni dans l'état, ni dans le calcul, mais dans l'**ordre de
composition**. C'est ce qu'un test d'interface attrape, et lui seul.

`PoiBubbleUiTest` garde le cas devenu courant depuis qu'OpenStreetMap complète la couche : un lieu **sans
nom**. Une fontaine ou des toilettes n'en portent presque jamais, et ce sont justement les lieux qu'on
cherche - l'infobulle prend alors le nom de la catégorie plutôt que de s'ouvrir sur un titre vide.

`OffTrackAlertUiTest` couvre l'autre moitié de l'alerte, celle que `TrackWatchTest` n'atteint pas : que la
distance et le nom de la trace arrivent bien sous les yeux, dans les unités réglées, et que les gestes de la
boîte de dialogue mènent où ils annoncent.

Ce qui reste hors d'atteinte : l'écran principal lui-même, qui porte la `MapView`. Ses composables
s'appellent un par un, mais l'assemblage - la carte, ses gestes, ses couches - demande un appareil.

## Ce que les tests ont révélé

Écrire ces tests a mis au jour trois choses.

**La mesure de couverture était fausse.** Jacoco annonçait `LayerImporter` à 0 % alors que ses 19 tests
passaient. Robolectric charge les classes par son propre chargeur, sans emplacement source, et l'agent
Jacoco les ignorait. Sans `isIncludeNoLocationClasses`, le rapport aurait été un mensonge, plus
nuisible qu'une absence de mesure. Corrigé, `LayerImporter` passe à 89,8 %.

**Un KML tronqué ne lève pas d'exception.** Il rend une couche vide, et l'utilisateur lit "le fichier
est vide" là où la spécification demande "invalide". Le parseur XML atteint la fin du flux sans se
plaindre des balises non fermées. Un GPX tronqué, lui, lève : sa coupure tombe en général en plein
attribut. Le comportement réel est verrouillé par le test
`kml tronque rend une couche vide au lieu de lever`, pour qu'il ne dérive pas en silence. Corriger
demanderait de vérifier que la racine s'est refermée.

**Le drag & drop du gestionnaire de fonds est correct.** Sa logique a été extraite et testée lors de
la recherche d'un bug de déplacement de dossier signalé par l'utilisateur. Les tests montrent que le
calcul de la cible de dépôt est juste, y compris pour un dossier imbriqué déplacé vers un autre
dossier. Le bug est donc ailleurs (geste, défilement, ou visibilité de la cible à l'écran).

## Pièges de l'infrastructure

Trois réglages, sans lesquels rien ne fonctionne.

**`robolectric.properties`** (`app/src/test/resources/`) impose une `Application` neutre. La vraie
(`TrailogApp`) initialise MapLibre au démarrage, dont les bibliothèques natives ne se chargent pas sur
la JVM : tous les tests Robolectric échouaient en `UnsatisfiedLinkError`. Aucun test unitaire n'a
besoin de la carte - et c'est la même limite qui borne les tests d'interface : un composable qui embarque
une `MapView` ne peut pas être joué ici, quelle que soit l'`Application`.

**`isIncludeNoLocationClasses`** (`app/build.gradle.kts`) rend visibles à Jacoco les classes chargées
par Robolectric. Sans lui, le rapport annonce 0 % sur du code pourtant couvert.

**`isIncludeAndroidResources`** donne aux tests l'accès au manifeste, aux ressources et aux assets
réels de l'app. Sans lui, aucun test ne peut lire une chaîne ou un asset.

Enfin, la base est un **singleton** : les méthodes d'une même classe de test la partagent. Un test qui
suppose un ordre de liste devient dépendant des autres. `TrailogRepositoryTest` vise donc explicitement
la dernière couche insérée par son identifiant.
