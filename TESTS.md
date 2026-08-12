# Tests - Trailog

Ce document recense les tests de l'application : ce qu'ils couvrent, ce qu'ils ne couvrent pas, et
pourquoi. Il s'adresse à qui doit ajouter un test ou comprendre pourquoi la couverture affiche le
chiffre qu'elle affiche.

- [Comment lancer les tests](#comment-lancer-les-tests)
- [Ce que couvre chaque niveau](#ce-que-couvre-chaque-niveau)
- [Couverture mesurée](#couverture-mesurée)
- [Tests unitaires](#tests-unitaires)
- [Ce que les tests ont révélé](#ce-que-les-tests-ont-révélé)
- [Pièges de l'infrastructure](#pièges-de-linfrastructure)

## Comment lancer les tests

```bash
./gradlew :app:testDebugUnitTest      # tests unitaires (JVM + Robolectric), aucun appareil requis
./gradlew :app:jacocoTestReport       # couverture réelle -> app/build/reports/jacoco/html/index.html
./gradlew :app:connectedDebugAndroidTest   # instrumentation et e2e (appareil ou émulateur requis)
```

## Ce que couvre chaque niveau

L'application fait 9100 lignes, dont **5293 (58 %) dans des fichiers `@Composable`**. Ces lignes sont
hors d'atteinte d'un test unitaire JVM par construction : elles n'existent qu'à l'exécution, dans un
arbre de composition. Viser 90 % avec des tests unitaires est donc arithmétiquement impossible. La
répartition est la suivante.

| Niveau | Périmètre | Où |
|---|---|---|
| **Unitaire** | logique pure et logique dépendante d'Android (parsing, base, style, calculs) | `app/src/test/` |
| **Instrumentation** | composants Compose isolés, base sur un vrai SQLite | `app/src/androidTest/` |
| **e2e** | parcours utilisateur complets, application réelle | `app/src/androidTest/` |

Les tests unitaires tournent sur la JVM. Ceux qui touchent au framework Android (`android.util.Xml`,
`org.json`, Room, `SharedPreferences`) passent par **Robolectric**, qui en fournit une implémentation
sur la JVM. Ceux qui n'en ont pas besoin s'en passent : ils sont plus rapides.

## Couverture mesurée

Chiffres réels, produits par Jacoco, non estimés.

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

Le chiffre à retenir est **58,8 %**, celui du code que les tests unitaires peuvent atteindre. Le total
de 18,6 % ne dit rien de la qualité des tests : il mesure surtout la part de Compose dans l'app.

Les paquets encore bas sont `map/offline` (le moteur de téléchargement, qui parle réseau et SQLite) et
`update` (DownloadManager et installateur système). Les deux relèvent de l'instrumentation plus que du
test unitaire.

## Tests unitaires

**499 tests, 49 fichiers**, tous verts.

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

Les clés sont persistées en base (`settings.bubblePosition`) : les changer casserait les réglages
existants. Une valeur inconnue doit retomber sur AUTO plutôt que de planter.

### `data/imp` - import de fichiers

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `LayerImporterTest` | 19 | parsing GPX, KML, KMZ et GeoJSON ; fichiers refusés ; fichiers vides |
| `PropertyDetectorTest` | 11 | détection texte / lien / image des propriétés importées |

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
| `MigrationsTest` | 35 | les 28 migrations, et les défauts d'une installation neuve |

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

### `geocode` - recherche de lieu

| Fichier | Tests | Ce qui est verrouillé |
|---|---|---|
| `PhotonTest` | 21 | construction des requêtes (recherche et inverse), lecture de la réponse du géocodeur et découpage de l'adresse en morceaux |
| `ValhallaTest` | 20 | construction de la requête et lecture de la réponse du moteur d'itinéraire |
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
| `ReleaseInfoTest` | 3 | forme du manifeste écrit par la CI, calcul du versionCode |
| `UpdateCheckTest` | 2 | inertie en debug, distinction des trois issues |

`ReleaseInfoTest` garde un contrat entre **deux fichiers qui ne se compilent pas ensemble** : le `jq`
du workflow et le parseur Kotlin. Une divergence n'y produirait aucune erreur visible, seulement des
mises à jour qui cesseraient d'être proposées. Il vérifie aussi qu'un champ ajouté plus tard au
manifeste est ignoré plutôt que fatal, et que l'ordre des `versionCode` est correct.

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
besoin de la carte.

**`isIncludeNoLocationClasses`** (`app/build.gradle.kts`) rend visibles à Jacoco les classes chargées
par Robolectric. Sans lui, le rapport annonce 0 % sur du code pourtant couvert.

**`isIncludeAndroidResources`** donne aux tests l'accès au manifeste, aux ressources et aux assets
réels de l'app. Sans lui, aucun test ne peut lire une chaîne ou un asset.

Enfin, la base est un **singleton** : les méthodes d'une même classe de test la partagent. Un test qui
suppose un ordre de liste devient dépendant des autres. `TrailogRepositoryTest` vise donc explicitement
la dernière couche insérée par son identifiant.
