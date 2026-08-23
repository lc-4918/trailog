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
bibliothèques natives ne se chargeant pas sur la JVM (cf. les pièges plus bas). L'écran principal, lui,
se compose entier : sa surface de carte est passée en paramètre, et c'est la seule chose qu'un test
remplace (cf. plus bas).

## Couverture mesurée

Chiffres réels, produits par Jacoco, non estimés.

| Paquet | Lignes couvertes | % |
|---|---|---|
| `net` | 12/12 | 100 % |
| `data/seed` | 110/110 | 100 % |
| `data` (LocalePrefs) | 26/26 | 100 % |
| `data/backup` | 62/62 | 100 % |
| `domain/model` | 263/269 | 98 % |
| `elevation` | 214/221 | 97 % |
| `data/imp` | 199/216 | 92 % |
| `domain/geo` | 275/301 | 91 % |
| `ui/alert` | 71/83 | 86 % |
| `ui/edit` | 80/96 | 83 % |
| `routing` | 253/309 | 82 % |
| `geocode` | 48/67 | 72 % |
| `data/db` | 246/370 | 66 % |
| `poi` | 183/278 | 66 % |
| `map` (StyleBuilder) | 121/189 | 64 % |
| `ui/poi` | 152/246 | 62 % |
| `ui/planner` | 281/490 | 57 % |
| `ui/location` | 66/126 | 52 % |
| `data/repo` | 311/599 | 52 % |
| **`ui/routes`** | **1168/2972** | **39 %** |
| `ui/mappoint` | 67/179 | 37 % |
| `ui/geocode` | 49/174 | 28 % |
| `ui/measure` | 36/135 | 27 % |
| `map/offline` | 85/361 | 24 % |
| `ui/nav`, `ui/theme`, racine (`MainActivity`, `TrailogApp`) | 16/67 | 24 % |
| `update` | 29/132 | 22 % |
| `location` | 35/190 | 18 % |
| `ui/points` | 82/517 | 16 % |
| `ui/components` | 167/1053 | 16 % |
| `ui/profile` | 29/301 | 10 % |
| `ui/offline` | 8/288 | 3 % |
| `ui/settings` | 34/1760 | 2 % |

Les paquets `ui` de tête - `alert`, `edit`, `poi`, `planner` - le doivent à deux choses : leur **logique
extraite** (placement d'infobulle, transitions d'état, règles de chargement), et leurs **tests
d'interface**, qui atteignent les composables eux-mêmes.

**`ui/routes` est passé de 1 % à 39 %** en une fois, et c'est le seul chiffre de ce tableau qui dise
quelque chose de nouveau. Il portait 3000 lignes couvertes par 44 : l'écran principal ne se composait
pas. Sept tests le composent maintenant en entier, avec ses effets, ses dialogues et son menu. Aucune
ligne de production n'a changé de comportement pour cela - seule la surface de carte est passée en
paramètre.

| Ensemble | Couvert | % |
|---|---|---|
| **Hors UI** (cible des tests unitaires) | 2482/3753 | **66,1 %** |
| UI Compose | 2296/8446 | 27,2 % |
| **Total du code source** | 4778/12199 | **39,2 %** |

Le chiffre à retenir reste celui du code que les tests de logique peuvent atteindre. Le total ne dit
rien de la qualité des tests : il mesure surtout la part de Compose dans l'application.

Les paquets encore bas sont `map/offline` (le moteur de téléchargement, qui parle réseau et SQLite) et
`update` (DownloadManager et installateur système). Les deux relèvent de l'instrumentation plus que du
test unitaire.

## Tests unitaires

**835 tests, 79 fichiers**, tous verts.

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
| `MigrationsTest` | 57 | les migrations, et les défauts d'une installation neuve |

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
| `PoiSourcesTest` | 18 | qui répond où, pour quelles catégories, le découpage par groupe et la réunion des réponses |
| `PoiStreamTest` | 14 | l'ordre d'affichage des sources, et l'aveu d'un affichage partiel |
| `PoiFiltersTest` | 13 | ce qu'on coche, ce que le service reçoit, et la forme enregistrée |
| `PoiLoadingTest` | 15 | quand redemander, et ce que la carte dit quand elle ne peut rien montrer |
| `PoiStateTest` | 10 | le message de zoom, l'attente, et ce qu'une emprise tronquée ou en échec doit redemander |
| `PoiCategoryTest` | 12 | à quelle catégorie revient un lieu qui en porte plusieurs |

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

`PoiSourcesTest` garde la règle de partage : hors de France, OpenStreetMap répond seul ; en France, il
complète les services du terrain **et la restauration**, l'hébergement et les loisirs restant à la base qui
les illustre de photos. Deux tests y gardent la portée du réglage *Compléter avec OpenStreetMap* : éteint, il
rend la France à la base touristique seule, et **il ne vide pas la couche hors de France**, où rien d'autre
ne répondrait. Il verrouille aussi la réunion des deux réponses - un même lieu connu des deux bases n'est
jamais pointé au même mètre, et deux marqueurs superposés se recouvrent sans qu'on puisse ouvrir celui du
dessous.

`PoiCategoryTest` part de deux cas relevés sur le terrain : des toilettes publiques affichées en "Campings et
aires de camping-car" (Souillac, 46), et six hôtels d'Albi affichés en "Restaurants" sous le seul filtre de
la restauration. Leurs classes réelles sont recopiées dans le test, et il verrouille la règle qui règle les
deux : ce qu'un lieu **est** ne dépend pas de ce qu'on a coché. Un hôtel-restaurant reste un hôtel et ne
passe pas le filtre restauration ; un restaurant de quartier, lui, y passe.

`PoiStreamTest` garde aussi l'**émission de clôture**, celle qui dit que toutes les sources sont arrivées et
qui seule autorise à retenir l'emprise : un flux interrompu n'en émet pas, et une source en échec ne la rend
pas complète. Sans cette distinction, un dézoom suivi d'un zoom faisait disparaître les restaurants d'Albi
sans retour.

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
le message de zoom se lève **avant** les points, et l'attente survit à la **première** source. Trois tests y
gardent le frein posé après un échec - une zone qui vient d'échouer attend une minute, le frein ne vaut que
pour elle, et rallumer la couche l'oublie -, sans quoi chaque geste de carte relançait la requête que le
service venait de refuser.

### `data/db` - la chaîne des migrations

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `MigrationChainTest` | 6 | Qu'aucune migration ne manque, ne double, ni ne saute une version |

`MigrationsTest` rejoue chaque migration une par une et vérifie qu'elle fait ce qu'elle annonce. Il ne dit
rien du cas qui détruit vraiment des données : une migration **absente**.

Le défaut visé est facile à commettre seul. On ajoute une colonne, on écrit son SQL, on incrémente la
version, et l'on oublie d'enregistrer la migration. Rien ne le signale : le code compile, les 55 autres
tests passent, l'application démarre sur une base neuve. Elle ne se casse que chez quelqu'un qui avait déjà
des couches importées - et Room se rabattait alors sur un repli destructeur qui supprimait ses tables.

Ces six tests sont ce qui a permis de **borner ce repli** aux seules versions préhistoriques : ils font
échouer la CI avant la mise en ligne, plutôt que le téléphone après. Leur capacité à échouer a été
vérifiée en retirant volontairement une migration de la liste, puis en oubliant volontairement
d'incrémenter la version : trois tests tombent dans le premier cas, deux dans le second.

### `ui/routes` - le calcul extrait des ViewModels

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `StyleSettingsTest` | 5 | Quels réglages imposent de reconstruire le style de carte |
| `TreeReorderTest` | 9 | Où se pose un élément lâché dans une arborescence |
| `DisplayedBasemapsTest` | 12 | Quels fonds sont réellement à l'écran, donc quelle légende proposer |
| `NearestTracksTest` | 9 | Combien de couches ouvrir pour trouver la trace la plus proche |

Ces quatre-là ne testent pas un ViewModel : ils testent ce qu'on en a **sorti**. Un ViewModel orchestre,
et une orchestration se teste mal - un faux dépôt vérifierait qu'un appel a lieu, pas qu'un résultat est
juste. Le calcul, lui, s'extrait et se verrouille.

`TreeReorderTest` a une raison d'être supplémentaire : le calcul était écrit **deux fois**, mot pour mot,
une fois pour le menu latéral et une fois pour le catalogue des fonds. Une correction sur l'un aurait
laissé l'autre en place, et le défaut ne se serait vu que dans un seul des deux écrans.

`DisplayedBasemapsTest` couvre les trois replis d'un composite mal formé - éteint, fond disparu, calque
disparu - plus l'exclusion du relief des deux côtés. Ce qui s'y décide est la légende proposée par le
bouton « info » : une légende qui ne décrit pas ce qu'on regarde est pire qu'une absence de légende, elle
a l'air juste.

`PoiFiltersTest` verrouille un choix qui se lit mal dans le code : ce sont les catégories **masquées** qui
sont enregistrées. Un réglage vierge montre alors tout, et une catégorie ajoutée par une version ultérieure
apparaît d'elle-même au lieu de rester invisible jusqu'à ce que l'utilisateur aille la chercher.

### `location` - suivi de position

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `TrackWatchTest` | 9 | l'alerte d'éloignement : ce qui la déclenche, ce qui la tait, ce qui la réarme |
| `LocationHubTest` | 9 | la différence entre un suivi qu'on arrête et un suivi qui s'arrête |
| `StaleNoticeTest` | 4 | la bannière « position figée » se referme, et ne se tait que pour cette péremption |

`LocationHubTest` **vient du terrain.** Un testeur a fait vingt kilomètres dans le mauvais sens : son
repère avait disparu, il l'avait vu, et rien ne lui a appris que l'application ne savait plus où il était.
Le suivi s'était arrêté tout seul - localisation coupée, ou service tué - et le code traitait cet arrêt-là
exactement comme un tap sur le bouton. Toute la correction tient dans cette distinction : ce qui suit un
arrêt **demandé** est le silence, ce qui suit un arrêt **subi** est une annonce et une reprise.

Deux mutations vérifient que ces tests attrapent bien le défaut d'origine. Faire taire l'arrêt subi - le
comportement exact d'avant - fait tomber **5 tests** ; effacer l'intention à l'arrêt, ce qui supprimerait
la reprise automatique, en fait tomber **3**.

`StaleNoticeTest` (dans `ui/location`) vient d'un appui sans effet : la croix de la bannière « position
figée depuis x » était branchée sur une lambda vide. La bannière occupait le bas de la carte pour toute la
durée du trou de réception - sous un couvert, dans une gorge - et rien ne pouvait l'en déloger. Une alerte
qu'on ne peut pas refermer finit par se lire comme un décor, ce qui est exactement ce qu'une alerte ne doit
pas devenir.

Deux règles s'y décident. Refermer dit **« j'ai lu »**, pas « c'est faux » : le repère garde sa couleur de
péremption sur la carte, le fait restant vrai. Et le silence ne vaut que pour **cette** péremption : la
prochaine mesure le lève, faute de quoi se taire une fois reviendrait à se taire pour le reste de la sortie.

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

**33 tests, 5 fichiers.** Ils composent pour de vrai - taps, focus, recompositions - et vivent avec les
autres dans `app/src/test/`, joués par Robolectric sur la JVM (le pourquoi est plus haut).

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `PoiBubbleUiTest` | 6 | l'infobulle d'un point d'intérêt : nom, catégorie, les trois actions d'itinéraire, le lieu sans nom |
| `OffTrackAlertUiTest` | 9 | la bannière d'alerte et le choix de la trace à suivre |
| `MainScreenUiTest` | 11 | l'écran de carte entier : les réglages, le menu, les modes qui se disputent les taps, et l'annonce d'un suivi interrompu |
| `RoutePlannerBandUiTest` | 5 | la bande du planificateur, dont le retour sur une étape déjà remplie |
| `MainScreenSansReglagesTest` | 2 | la carte pendant que les réglages n'ont pas encore répondu |

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

### La fenêtre où les réglages n'existent pas encore

`MainScreenSansReglagesTest` **vient du terrain**, et il garde un défaut qu'aucun test de logique n'aurait
vu. `MainViewModel.settings` est un `StateFlow<SettingsEntity?>` dont la valeur initiale est `null` : c'est
le premier état de tout ViewModel, et il dure le temps d'ouvrir la base. Après une **mort du processus** -
Android reprend sa mémoire pendant une longue sortie, écran éteint - l'activité et le ViewModel sont
recréés, et cette fenêtre se rouvre en pleine route.

Trois lectures y traitaient l'inconnu comme un « non » alors que le défaut du réglage est « oui ». La carte
ne portait plus alors **que le burger** : ni bouton GPS, ni itinéraire, ni gestionnaire de fonds. Les deux
premiers sont exactement ceux qu'un testeur a décrits comme disparus. Tout le reste du fichier lisait déjà
son défaut (`?:` ou `!= false`) ; c'étaient les trois seules exceptions.

Les deux tests tiennent les deux moitiés : ce dont le défaut est « oui » reste affiché, ce dont le défaut
est « non » reste absent. Tout afficher par précaution serait la faute inverse.

**Une classe à part, et la base vidée.** Le singleton de base est partagé par les méthodes d'une même
classe - et, l'expérience le montre, par les classes successives d'une même exécution. Sans ce ménage,
cette classe héritait des réglages écrits par `MainScreenUiTest`, dont un menu latéral réglé sur le
glissement, et la fenêtre qu'on veut tenir ouverte se refermait.

### L'écran principal, et ce qu'il a fallu pour l'atteindre

`MainScreen` ne calcule presque rien : il **câble**. Aucun test de logique ne peut voir ce câblage, et
rien n'en était vérifié - un seul appel sur 1800 lignes rendait l'écran incomposable, `MapLibreView`
construisant une `MapView` dont les bibliothèques natives n'existent pas sur la JVM. Mesuré plutôt que
supposé : `MapLibre.getInstance` lève `UnsatisfiedLinkError`, `MapView(context)` lève
`MapLibreConfigurationException`.

Deux coutures ont suffi, et aucune ne change ce que fait l'application :

- **la surface de carte passe en paramètre** (`MapSurface`), sa valeur par défaut étant la vraie ;
- **`TrailogApp` isole deux points d'entrée** : l'init du moteur natif, et ce que le démarrage lance en
  tâche de fond. Le second parce que le semis y écrit les réglages sur un autre thread : un test qui pose
  les siens juste après courait contre lui, et perdait une fois sur dix.

Tout le reste est de production - la base, le dépôt, les réglages, le ViewModel, les effets.

**Le fil qu'on tient à la place de MapLibre est `MapController`**, par lequel la carte parle déjà à
l'écran. La surface le reçoit en paramètre, donc le test le capture sans que l'écran ait à l'exposer : ce
qui permet de vérifier à qui reviennent les taps selon le mode actif, une règle qui ne s'écrit qu'ici.

**Ce que ces tests n'atteignent pas**, et pour de bon : le rendu des tuiles, les gestes réels, et tout ce
qui **s'ancre à un point de carte**. La position à l'écran d'une infobulle vient de `controller.screenOf`,
c'est-à-dire de la projection de MapLibre. Un appui long est bien reçu en test, mais sa bulle n'a aucun
endroit où se poser. Falsifier cette projection reviendrait à tester une invention.

Leur capacité à échouer a été vérifiée par trois mutations du code de production. Croiser deux réglages -
la règle obéissant au réglage de la retouche - fait tomber 3 tests ; c'est pour l'attraper que les deux
tests de réglages sont **complémentaires** plutôt que tout-allumé puis tout-éteint : deux boutons qui
échangent leurs réglages apparaissent et disparaissent ensemble, donc toujours au bon moment. Retirer la
capture des taps du mode mesure, ou le retrait du burger en mode glissement, fait tomber 1 test chacun.

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

**`robolectric.properties`** (`app/src/test/resources/`) impose une `Application` neutre par défaut. La
vraie (`TrailogApp`) initialise MapLibre au démarrage, dont les bibliothèques natives ne se chargent pas
sur la JVM : tous les tests Robolectric échouaient en `UnsatisfiedLinkError`. Aucun test unitaire n'a
besoin de la carte.

`MainScreenUiTest`, lui, en a besoin d'une vraie - il monte le dépôt et les réglages. Il déclare donc la
sienne, `@Config(application = TestTrailogApp::class)`, qui hérite de `TrailogApp` et ne neutralise que
deux points d'entrée : le moteur natif, et le démarrage en tâche de fond. Une `Application` déclarée par
classe l'emporte sur `robolectric.properties`.

**`isIncludeNoLocationClasses`** (`app/build.gradle.kts`) rend visibles à Jacoco les classes chargées
par Robolectric. Sans lui, le rapport annonce 0 % sur du code pourtant couvert.

**`isIncludeAndroidResources`** donne aux tests l'accès au manifeste, aux ressources et aux assets
réels de l'app. Sans lui, aucun test ne peut lire une chaîne ou un asset.

Enfin, la base est un **singleton** : les méthodes d'une même classe de test la partagent. Un test qui
suppose un ordre de liste devient dépendant des autres. `TrailogRepositoryTest` vise donc explicitement
la dernière couche insérée par son identifiant.

## Ce qu'une classe de test peut casser chez les autres

Toutes les classes partagent **une seule JVM** (`forkEvery = 0`) et le bac à sable Robolectric qui va
avec. Deux états y sont globaux, et les toucher sans les refermer bloque tout ce qui suit. Les deux ont
été découverts le même jour, par la même classe - `StaleNoticeTest`, qui ne compose pourtant rien.

**L'horloge du bac à sable.** `ShadowSystemClock.advanceBy` avance le temps pour toutes les classes
suivantes. Ce qui se mesure par une différence de dates se simule sur la **donnée**, jamais sur
l'horloge : vieillir la mesure donne exactement le même calcul sans déborder sur personne.

**Les écritures d'état Compose.** Écrire dans un `mutableStateOf` hors de toute composition laisse la
modification en attente dans le snapshot global. Tout test d'interface qui suit y lit « il reste du
travail à faire », et son attente d'inactivité tourne jusqu'à expirer. Ces écritures passent donc par
`Snapshot.withMutableSnapshot { }`, qui les publie.

**Le symptôme est trompeur, et c'est là tout le piège.** L'erreur ne pointe pas la classe fautive mais
une **victime** - un `AppNotIdleException : Compose did not get idle` levé par un tout autre test
d'interface, soixante secondes plus tard. La victime change selon la répartition des classes entre JVM,
ce qui fait passer le défaut pour de l'instabilité aléatoire. Il n'en est rien : il est parfaitement
déterministe, et il se cherche en retirant les classes une à une, pas en relançant la suite.

Ordre de grandeur : ces deux fautes, dans quatre tests, ajoutaient **treize minutes** à une suite qui en
dure une demie, et treize échecs répartis sur trois classes innocentes.
