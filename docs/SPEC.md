# Trailog - Spécification fonctionnelle

> Ce document décrit l'application **telle qu'elle est**. Il a d'abord décrit le périmètre v1 tel que
> défini au démarrage du projet ; ces intentions d'origine restent dans l'historique git, où elles sont
> plus utiles qu'en tête d'un document qu'on lit pour savoir ce que fait l'application aujourd'hui.

| Pour | Lire |
|---|---|
| se servir de l'application | [`README.fr.md`](README.fr.md) |
| la compiler, s'y repérer | [`DEVELOPER.md`](DEVELOPER.md) |
| comprendre **pourquoi** c'est ainsi | [`CONTEXT.md`](CONTEXT.md) |
| les fonds de carte en détail | [`BASEMAPS.md`](BASEMAPS.md) |
| ce que les tests verrouillent | [`TESTS.md`](TESTS.md) |
| la CI et les mises à jour | [`WORKFLOW.md`](WORKFLOW.md) |

## 1. Périmètre

Trailog garde des traces et des points d'intérêt **localement**, les affiche sur une carte avec un profil
altimétrique synchronisé, et sait travailler sans réseau.

Ce qu'elle fait :

- consulter des traces importées (carte + profil), rangées en dossiers ;
- importer GPX, GeoJSON, KML/KMZ ; exporter en GPX ou en GeoJSON ;
- afficher un large choix de fonds de carte, y compris hors ligne ;
- télécharger une zone pour l'emporter sans réseau ;
- décrire un point : adresse, distances, mesures le long d'une trace ;
- préparer un itinéraire par étapes ;
- afficher les points d'intérêt du parcours (hébergement, restauration, loisirs, services).

Ce qu'elle n'est pas : un enregistreur de trace (pas de suivi GPS temps réel), un éditeur de tracé à la
main, ni un service en ligne - aucun compte, aucune synchronisation, aucune télémétrie.

## 2. Pile technique

Kotlin, **Jetpack Compose** (Material 3), une seule `Activity`, pas de `NavHost`. Carte **MapLibre
Native**. Catalogue en **Room**, géométrie en fichiers. Profil altimétrique dessiné au `Canvas`.
Architecture dépôt -> ViewModel (`StateFlow`) -> écrans Compose. Détail dans
[`DEVELOPER.md`](DEVELOPER.md#7-architecture).

## 3. Modèle de données

```
Folder(id, name, parentId?, sortOrder)             // arborescence, racine = parentId null
LayerEntity(id, name, folderId, link?, description?,
      source, importedAt, geometryFile,            // fichier de géométrie stocké
      distance, ascent, descent, minEle, maxEle, movingTime?,
      hasZ, hasTime, hasLine, hasPoints,           // ce que la couche porte réellement
      visible, color, sortOrder, west, south, east, north)
ProviderEntity(...)                                // cf. BASEMAPS.md
CompositeEntity(id, name, backgroundProviderId, foregroundProviderId, foregroundOpacity, ...)
SettingsEntity(...)                                // tous les réglages, une seule ligne
```

Une couche importée peut porter des traces **et** des points dans un même fichier (`hasLine` /
`hasPoints`) : c'est le fichier qui décide, pas l'application.

Chaque couche s'écrit en **trois fichiers** : la géométrie complète (référence pour l'export et
l'édition), un GeoJSON allégé pour le rendu, un profil précalculé pour le graphique. Le pourquoi est dans
[`CONTEXT.md`](CONTEXT.md#trois-fichiers-par-couche).

## 4. Fonds de carte

Registre éditable : URL, clé, activation, dossiers, composites, sources locales. Voir
[`BASEMAPS.md`](BASEMAPS.md).

Le **relief** est un fond comme les autres (un DEM converti en ombrage), et non une option à part : il
figure dans le gestionnaire s'il est coché dans les réglages, comme n'importe quel fond, et un tap sur lui
allume ou éteint son ombrage. Ces deux états sont distincts (cf. [`CONTEXT.md`](CONTEXT.md#le-relief-nest-pas-un-fond)).
La force de l'ombrage se règle dans sa fiche.

## 5. Cartes hors ligne

- **Cache ambiant** MapLibre : les tuiles déjà consultées restent disponibles (taille réglable).
- **Zone téléchargée** : deux façons de dire ce qu'on emporte, proposées au départ. Une **emprise
  rectangulaire** dessinée sur la carte - pour un massif, une vallée, un secteur qu'on ne connaît pas
  encore. Ou **le long d'une trace** : seules les tuiles qui bordent le parcours, sur une largeur réglable
  de chaque côté ; une trace qui revient sur elle-même ne fait pas retélécharger les mêmes tuiles. Sur une
  diagonale, le rectangle emporte l'essentiel pour rien.
  Puis plage de zoom choisie, et téléchargement dans un **MBTiles** produit par l'application, qui devient
  un fond local. Vaut pour tous les types de fonds. L'écran de configuration annonce le nombre de tuiles et
  la taille avant de lancer, et les deux suivent la largeur du couloir.
- Les traces, elles, sont toujours disponibles : elles vivent en base et en fichiers.

## 6. Import et rangement

- Bouton *Importer* -> sélecteur de fichiers -> lecture GPX / GeoJSON / KML / KMZ -> aperçu (nom,
  distance, D+/D-, présence d'altitude et d'horodatage) -> choix de la destination.
- Destination : dossier existant, ou nouveau dossier (racine ou sous-dossier).
- **Ouvrir un fichier depuis ailleurs** : Trailog figure dans le "Ouvrir avec" et le "Partager vers" du
  téléphone pour un GPX, un KML, un KMZ ou un GeoJSON. Un fichier reçu par courriel, posé dans un
  gestionnaire de fichiers ou téléchargé par le navigateur s'importe donc sans passer par le bouton, et
  plusieurs d'un coup si l'on en partage plusieurs. Le chemin est ensuite le même que celui du bouton :
  l'application demande le dossier d'accueil, puis ouvre le menu sur l'import en cours - la couche arrive
  dans un dossier que rien à l'écran ne montrerait autrement. Renoncer au dossier renonce à l'import.
- Les **photos des waypoints GPX** (OruxMaps, OsmAnd, Locus, Garmin) sont récupérées et copiées dans le
  stockage de l'application.
- **Altimétrie manquante** (réglage, désactivé par défaut) : un fichier sans altitude en reçoit une, tirée
  d'un modèle de terrain - l'**IGN** sur la France (RGE ALTI, au mètre), **OpenTopography** ailleurs
  (Copernicus GLO-90, à 90 m). Aucune frontière n'est
  codée : on demande d'abord à l'IGN, qui répond lui-même là où il ne sait pas. Une trace est complétée
  **entièrement ou pas du tout** - un profil auquel il manque des points plongerait au niveau de la mer et
  fausserait le D+ ; les waypoints, eux, se complètent chacun pour son compte. Un échec (service muet,
  réseau absent) laisse la couche s'importer telle quelle, sans altitude. Pendant cette attente - la seule
  de l'import qui dépende du réseau - la ligne du dossier annonce **"Calcul de l'altimétrie"** au lieu de
  l'import : un import qui semble bloqué n'est alors qu'un import qui attend une réponse.
- Renommer, déplacer, supprimer dossiers et couches. Une action de dossier - l'oeil, la couleur commune,
  le cadrage - porte sur **tout** ce qu'il contient, sous-dossiers compris.
- **Exporter une couche**, l'enregistrer où l'on veut ou l'**envoyer** à une autre application (montre,
  messagerie). Le format se choisit à part, chacun disant ce qu'il vaut : le **GPX** est lu partout mais ne
  garde que les champs standard d'un waypoint - photos, liens et champs libres n'y ont pas de place - ; le
  **GeoJSON** est le format de stockage de l'application et ne perd rien, au prix d'une diffusion moindre.
  **Un export qui échoue le dit** - fichier non écrit, aucune application capable de recevoir ce qu'on
  partage. Il ne laissait rien auparavant : ni fichier, ni message, un tap qui n'avait servi à rien. La
  pire des deux issues n'est pas de croire l'application cassée, c'est de croire le fichier écrit et de
  s'en apercevoir sur le terrain. La même règle vaut pour le téléchargement d'un parcours calculé et pour
  l'ouverture du site d'un point d'intérêt.
- **Retoucher une trace** : voir la section 6 bis. Ce ne sont pas des outils de dessin - ce sont les
  gestes qu'on fait sur un fichier reçu.

## 6 bis. Retoucher une trace

Les retouches vivent dans une **barre d'outils verticale** posée sur la carte, ouverte par un bouton de la
colonne de gauche, sous le burger - et **absente par défaut** : ses outils modifient des traces importées,
et son mode détourne les taps de la carte ; qui ne retouche pas ses traces n'a aucune raison de croiser ce
bouton (réglage, onglet Carte). La barre s'ouvre du même côté que son bouton, centrée verticalement, et
descend sous les commandes du haut quand le milieu de l'écran tombe dedans. Elles ne portent pas sur une couche mais sur un **segment** - parfois deux, parfois dans deux
couches différentes -, et un segment se désigne du doigt : un menu d'arborescence n'a aucun moyen de dire
"celui-là, pas l'autre". Tant qu'un outil est allumé, les taps sur une trace lui reviennent et n'ouvrent
plus de profil.

- **Inverser** (flèche circulaire) : tapez la trace. Les horodatages tombent - un temps qui recule n'est
  pas une trace valide -, ce qui est demandé avant quand la trace en porte.
- **Couper** (ciseaux) : tapez la trace à l'endroit voulu. Un **marqueur** s'y pose, **n'importe où sur le
  parcours** et pas seulement sur un sommet : le point visé est le projeté du doigt sur le tronçon, inséré
  dans la trace au moment de la coupe. Un nouveau tap le déplace ; une barre demande confirmation. Le marqueur est une **bulle dont la pointe
touche le point**, posée du côté - gauche, haut, droite ou bas - dont le corps **ne recouvre aucune portion de trace** - on regarde cet endroit pour
décider si l'on coupe là, une pastille posée dessus le masquait. Au bord de l'écran, la carte se décale
pour la montrer entière, avec une marge. Le
  premier morceau reste dans la couche, le second devient sa voisine.
- **Joindre** (maillon) : tapez le premier segment, puis le second - d'une même couche ou de deux couches.
  Trois façons de les relier : **ligne droite**, **parcours réaliste** (le moteur d'itinéraire, dans la
  discipline réglée) ou **sans jonction** (les deux segments se retrouvent dans la même couche, distincts).
  Les deux segments sont raccordés par leurs extrémités les plus proches, celui qui a été enregistré à
  l'envers étant retourné pour l'occasion. Faute d'itinéraire, la jonction se fait en ligne droite et le dit.
- **Annuler** : défait la **dernière** retouche, y compris une couche créée ou supprimée, qui retrouve son
  identité. Un seul niveau : une retouche fautive se voit tout de suite, sur la carte.

## 7. Menu latéral

Le menu d'un dossier donne ses **statistiques** : nombre de couches et de traces, distance, D+, D- et durée,
**sous-dossiers compris** - comme l'oeil, la couleur et le cadrage, qui portent déjà sur tout ce qu'il
contient. La durée n'apparaît que si toutes ses traces sont horodatées : un total partiel serait plus petit
que le temps réellement passé.

L'arborescence des dossiers et des couches, pleine largeur, avec les cases d'affichage. Ouverture par
bouton, par balayage, ou les deux (réglage). L'avatar en tête ouvre les réglages. Le gestionnaire de fonds
y est accessible.

La bande de geste du **balayage** occupe les 24 dp du bord gauche, mais **s'arrête au-dessus de la bande du
planificateur** : sur toute la hauteur, elle recouvrait le centre du bouton « Réduire » de son en-tête, qui
est justement collé au bord gauche, et prenait le tap. Le doigt tombant rarement pile au milieu, le bouton
répondait une fois sur deux - le genre de défaut qu'on met sur le compte de sa propre maladresse.

Un **champ de recherche** filtre les couches par leur nom, sans tenir compte de la casse ni des accents, et
sur un fragment quelconque du nom. Il remplace l'arbre par la liste à plat de ce qu'il trouve, plutôt que de
déplier les dossiers autour des résultats : une couche cherchée est une couche qu'on veut voir maintenant,
son rangement est justement ce qu'on ne voulait pas parcourir.

## 8. Carte et gestes

- **Tap** : sélectionne un marqueur, sinon une trace, sinon rien. L'interrogation se fait sur une boîte
  centrée sur le doigt, dont le rayon est réglable **séparément** pour les points et pour les traces : on
  ne vise pas une épingle comme on vise une ligne.
- Un marqueur est sélectionné **dès le lever du doigt**, sans attendre la confirmation du double-tap :
  c'est 300 ms de moins avant son infobulle. Les traces et le vide restent sur le chemin confirmé, pour
  qu'un double-tap de zoom n'ouvre pas un profil.
- **Appui long** sur un endroit quelconque : voir la section 12.
- Rotation activable, barre de statut transparente en option.
- Les boutons posés sur la carte portent un fond blanc translucide dont la **taille se règle**, du carré
  qui ne fait que la taille de l'icône au bouton Material plein. Seul le carré dessiné change : la zone
  tactile reste aux 48 dp que Material impose, quel que soit le réglage.
- **Un bouton allumé se lit à la couleur de son dessin**, jamais à un aplat qui le distinguerait de ses
  voisins, et cette couleur est le bleu du repère de position - la même pour le GPS, la retouche et ses
  outils. L'accent du thème, un vert sombre, allumait un trait de 2 dp sans qu'on le remarque au-dessus
  d'une carte, là où ce bleu ne se confond avec aucun fond topographique.
- Le **repère de position** se choisit : la puce bleue à contour blanc (par défaut), une flèche de
  navigation au trait, la même pleine, ou une croix traversante à cercle central. Couleur et taille
  réglables ; les deux flèches suivent l'orientation du téléphone, dans le repère de la carte - elles
  visent le nord vrai, déclinaison magnétique corrigée, et tournent donc avec la carte. Le halo de
  l'imprécision reste dans tous les cas, à la couleur du symbole : c'est l'imprécision de CE repère-là.
- Le bouton de **recentrage sur la position** n'apparaît que lorsque la position n'est pas au milieu de la
  carte : centrée, il n'a rien à faire et s'efface plutôt que de proposer un geste sans effet - il
  disparaît donc de lui-même au bout du recentrage qu'on vient de lui demander. Le disque au centre de son
  icône porte le bleu du point de position. La comparaison se fait à l'écran et non sur les coordonnées :
  la question est "la position est-elle au milieu de ce que je vois", et sa réponse doit valoir à tout
  zoom. Le planificateur, sous lui, ne bouge pas pour autant : la colonne est alignée en bas.
- **Le gris dit que le repère n'est plus suivi**, et non qu'il est immobile. Il passait au gris au-delà de
  trente secondes sans mesure : l'idée était qu'un repère figé est visuellement identique à un repère
  juste. Mais un cycliste s'arrête - un col, un pique-nique, une réparation - et la couleur annonçait alors
  un doute qui n'existait pas, sur ce qui est le plus regardé de la carte. Un réglage (Carte > Position,
  éteint par défaut) garde désormais la **dernière position mesurée** sur la carte quand le suivi s'arrête :
  elle y reste grise et immobile, on retrouve d'où l'on vient sans la confondre avec un repère vivant.
- **La flèche montre la direction du DÉPLACEMENT**, non l'orientation du téléphone. Elle tirait son cap de
  la boussole, si bien qu'elle tournait sur elle-même dès qu'on prenait l'appareil en main, et s'affichait
  de biais sur la trace suivie - un téléphone sur un guidon pointe où son support le tient. Le cap du GPS
  commande dès qu'on avance ; la boussole ne reprend qu'à l'arrêt, lissée, où elle est seule à dire quelque
  chose.
- **L'allumage de la localisation ne change jamais le zoom.** Il sautait à la position au zoom 15 : on
  regardait une vallée pour préparer la suite, on touchait le bouton pour se situer, et l'on se retrouvait
  au coin d'une rue. Le recentrage lui-même est un réglage, éteint par défaut - « où suis-je » et
  « emmène-moi » sont deux questions distinctes.
- **Suivre ma position** (réglage, onglet Carte, allumé par défaut) recentre la carte à chaque mesure, et se
  tait cinq secondes après chaque geste : regarder plus loin sur l'itinéraire est un besoin aussi réel que
  le suivi, et une carte qui revient sous les doigts est inutilisable. Trois choses le **suspendent**, parce
  qu'elles se servent de la carte ailleurs qu'à l'endroit où l'on se tient : la bande du planificateur
  **déployée**, le profil d'une trace ouvert, une infobulle ouverte. Aucune ne l'éteint - le suivi reprend
  en les fermant. La bande **réduite**, elle, ne suspend rien : c'est l'état dans lequel on roule en suivant
  un parcours calculé, et la carte doit alors suivre comme partout ailleurs.
- **Une rotation ne défait pas le suivi.** Le système recrée l'écran à chaque quart de tour, et la carte
  s'y replaçait comme à l'ouverture : sur le dernier cadrage enregistré, ou - faute de cadrage - sur
  l'emprise de toutes les couches, ce qui la faisait dézoomer. On décentrait la carte, on tournait l'écran,
  et elle sautait ailleurs. Le suivi allumé et le capteur en marche, elle se replace désormais **sur la
  position**, au zoom qu'on avait : la rotation n'est pas une demande de changer d'endroit ni d'échelle.
- **Les commandes se répartissent en trois coins.** En haut à gauche : le menu, l'interrupteur GPS, la
  recherche de lieu (dont la barre de saisie se déplie juste dessous, ce qui la retient là) et la mesure
  sur trace. En bas à droite, à portée du pouce : le recentrage sur la position, le bouton de suivi de trace
  d'éloignement et le planificateur, ce dernier au plus près du coin - c'est celui qui reste, les autres
  n'apparaissant que le capteur allumé ou le réglage coché, et un bouton qui change de place au gré du GPS
  se chercherait à chaque fois. En bas à gauche : l'échelle graphique.
- **L'échelle se décale, les boutons non.** L'échelle se range au-dessus de la consigne de saisie qui
  occupe le bas, et s'efface quand le profil ou la bande du planificateur y sont : c'est une lecture, elle
  doit rester lisible. Les boutons du coin bas-droit gardent leur place, à la barre de navigation près, et
  attendent sous le panneau qui les couvre : c'est une cible, elle doit rester où la main l'a laissée.

## 9. Profil altimétrique

- Synchronisé avec la carte dans les deux sens : un tap sur l'un place le curseur sur l'autre.
- **Zoom** sur une portion, jusqu'à trois niveaux imbriqués, aux doigts (écartement ou double-tap).
- **Lissage** réglable (1 à 100 m) avant calcul, pour les traces GPS à points espacés.
- **Échelle verticale** : automatique (le profil remplit la hauteur) ou absolue en mètres par centimètre
  physique, pour qu'une pente de 2 % n'apparaisse pas comme un mur.
- Aire colorée par classe de pente, avec sa légende, et infos du point courant configurables.
- Une trace sans altitude s'affiche quand même, et le dit.

## 10. Infobulles

- **Marqueur** : ses propriétés, éditables (titre, champs texte, lien, image, ajout de champs, suppression
  du point). Alias des clés GPX usuelles (`ele` avec son unité, `desc`, `cmt`) ; `sym` et `type` sont
  masqués mais conservés à l'enregistrement. Image de garde épinglable, affichée au-dessus du titre.
- **Placement** configurable : automatique, ou l'une des 9 positions autour du marqueur. Quand la position
  demandée ne tient pas à l'écran, c'est la **carte** qui se décale : le marqueur ne bouge pas, et
  l'infobulle est bien là où elle a été réglée.
- Ce réglage vaut pour les marqueurs d'une couche, **non pour les infobulles du géocodage** (lieu cherché,
  point désigné par un appui long). Celles-ci se posent dans celui des quatre coins du point qui **déplace
  le moins la carte**, donc sans la déplacer du tout dès qu'un coin tient. Un marqueur est posé par
  l'utilisateur, qui sait ce qui l'entoure et veut retrouver sa bulle au même endroit ; un point désigné du
  doigt tombe n'importe où, souvent près d'un bord, et une position imposée y demanderait un recadrage à
  chaque fois. Le geste compte alors plus que l'habitude : la carte reste où elle est.
- Ce qui doit tenir à l'écran, c'est **l'épingle et la bulle** : l'épingle est posée par sa pointe sur le
  point et monte de sa hauteur au-dessus de lui, un appui long contre un bord la laisserait coupée. Deux
  cas n'ont pas de plus petit décalage qui vaille et sont donc **centrés** : le point hors de la carte (un
  lieu trouvé loin de la vue courante finirait collé à un bord) et l'ensemble trop grand pour l'écran.
- Ces deux infobulles restent **collées à leur épingle** pendant qu'on déplace la carte : leur position est
  recalculée à chaque image du geste, et non à la seule immobilisation - sinon la bulle restait sur place
  pendant que son épingle glissait, puis la rejoignait d'un saut. Seul son coin peut changer en route.
- Toutes les infobulles passent **au-dessus de l'échelle graphique** : une infobulle répond à ce qu'on
  vient de demander, l'échelle est là en permanence.
- L'adresse s'affiche d'un seul tenant tant qu'elle tient sur une ligne, sinon **un morceau par ligne** :
  l'intitulé, la voie, la commune. Les coupures sont choisies et non subies - laissées au retour à la
  ligne, elles sépareraient aussi bien un code postal de sa ville qu'un nom de voie en deux. Le géocodeur
  rend donc l'adresse en morceaux. Coller l'intitulé et la voie sur une même ligne économiserait une
  ligne, mais couperait la voie dès que le couple déborde - or c'est justement le cas où l'on replie.
  Tronquée quand même, l'adresse se relit en entier d'un appui long.
- La croix de fermeture est dans l'angle haut-droit, **par-dessus** le contenu et sans lui prendre de
  place : l'adresse dispose de toute la largeur. Une première ligne assez longue pour l'atteindre descend
  d'une bande et lui laisse le haut ; la croix ne recouvre donc jamais de texte, et la bulle ne grandit que
  dans ce cas.
- Taille de police, opacité et couleur de fond réglables, pour toutes.

## 11. Recherche de lieu / adresse

À contre-courant du "hors ligne d'abord" : la fonction exige le réseau, elle est donc **désactivée par
défaut** et s'active dans *Réglages / Carte*. Le service est **Photon**, choisi parmi les géocodeurs OSM
parce qu'il est conçu pour l'autocomplétion au clavier (Nominatim l'interdit dans sa politique d'usage) et
qu'il est **auto-hébergeable** : son URL est un réglage, une instance personnelle ne demande donc pas une
nouvelle version de l'application.

Les propositions sont classées par importance du lieu, sans biais de proximité : Photon accepte un centre
de recherche, mais il ferait remonter un hameau voisin devant la ville du même nom.

Le lieu choisi se marque d'une épingle noire, avec un zoom minimal garanti (12) pour qu'il reste situable
depuis une vue à l'échelle d'un pays ; son infobulle en donne l'adresse, et rien de plus. Il entre aussi
dans l'**historique du planificateur** (section 14) : l'avoir cherché est déjà dire qu'il nous intéresse.

L'infobulle porte les **trois actions d'itinéraire** - départ, arrivée, étape de plus - les mêmes que celle
d'un point d'intérêt et celle d'un appui long. Chercher un lieu pour aller quelque part est le geste le plus
courant qui soit ; l'infobulle qui le trouvait ne savait pourtant rien en faire, et il fallait rouvrir le
planificateur pour y retaper ce qu'on avait sous les yeux.

## 12. Point quelconque de la carte (appui long)

Un appui long hors d'une trace et d'un marqueur pose une épingle et ouvre une infobulle qui répond à trois
questions : quelle adresse est là, à quelle distance elle est de la position GPS, et à quelle distance
elle est d'un second point désigné d'un tap.

L'adresse vient du **géocodage inverse** de Photon, servi par un chemin frère de la recherche (`/reverse`
là où celle-ci est `/api`) : même instance, même URL réglée. Ce géocodage-ci ne dépend pas de
l'interrupteur de la recherche - il n'y a pas de bouton à cacher, et rien ne part tant que le doigt ne
s'attarde pas.

L'adresse trouvée entre dans l'**historique du planificateur** (section 14), aux coordonnées **désignées**
et non à celles que rend le géocodeur : l'épingle est restée sur le point qu'on a montré, l'adresse n'en
est que le nom. Un point sans adresse - plein champ, forêt, lac - n'y entre pas : « 44.56, 6.08 » ne dirait
rien à personne trois jours plus tard.

L'infobulle se lit en deux temps, séparés d'un trait : **au-dessus** l'adresse, seule chose que le
géocodage sait dire de ce point ; **au-dessous** ce qu'on peut en faire - les deux mesures, puis les trois
actions d'itinéraire, cinq lignes d'un même gabarit, pictogramme et libellé. Les mesures ont longtemps été
deux boutons à bordure posés dans l'en-tête, du temps où elles y étaient seules ; elles s'y donnaient l'air
d'appartenir à la description du point, alors qu'elles demandent un calcul comme les autres. Le résultat
d'une mesure s'inscrit sous son libellé - à droite, il ne tiendrait pas, et la durée se coupait au milieu.

Les **trois actions d'itinéraire** sont offertes **même sans adresse** : le point
a des coordonnées, c'est tout ce qu'il faut pour y aller, et l'étape porte alors les coordonnées pour nom.
Un point au milieu d'un bois est une étape parfaitement légitime - c'est peut-être le départ du sentier -,
et n'accepter que les endroits qui ont une adresse reviendrait à ne planifier qu'en ville.

Les deux mesures donnent la **distance et la durée d'un itinéraire** suivant la voirie, non un vol
d'oiseau, pour l'une des cinq disciplines réglables : vélo de route, gravel, VTC, VTT, à pied. Le moteur
se règle lui aussi, entre **Valhalla** et **BRouter**. Chacun garde **sa** propre URL de service, comme le
géocodeur a la sienne : basculer pour comparer ne fait donc pas perdre l'adresse de l'autre, et n'envoie
jamais la requête d'un moteur au serveur du voisin - une faute qui échouerait en silence.

Valhalla est le moteur d'origine : ses cinq disciplines sortent d'une seule instance via `bicycle_type`
(OSRM en exigerait cinq, GraphHopper n'a pas d'instance publique sans clé). Son modèle de coût est figé
dans son code, dont il n'expose qu'une poignée de curseurs - et c'est ce qui plafonne le résultat.
BRouter, lui, lit un **profil** envoyé avec la requête, un texte qui décrit le coût tag par tag :
l'application dépose celui de la discipline, réglé sur les trois préférences, et calcule sous
l'identifiant rendu. Il sait pour cette raison ce que l'autre ne peut pas dire - privilégier un sentier
de randonnée au marcheur - et rend l'altitude avec la géométrie, sans second appel.

**BRouter est le moteur par défaut**, non par préférence mais par mesure : à pied, Moulin-Neuf - Mirepoix
passe de 15 à 87 % de voies douces, la voie verte étant enfin empruntée. Valhalla reste offert d'un tap -
il calcule plus vite sur les longues distances, son graphe étant hiérarchique. Le réglage existe pour les
**comparer sur le terrain** : on demande la même chose aux deux, dans le même vocabulaire de disciplines
et de préférences, et l'on bascule sur le même trajet. L'itinéraire calculé est tracé sur la carte, sous les épingles et teinté par classe de
pente : sa géométrie arrive dans la même réponse que le total, encodée en polyligne.

Chaque discipline arrive avec ce qu'elle demande, réglable en trois questions - quelles voies, quel
relief, quel revêtement - mais déjà juste sans y toucher : le **vélo de route** reste sur la route et
c'est la seule à exiger le revêtu ; le **gravel** accepte les chemins et le dénivelé et privilégie les
chemins ; le **VTC** accepte les chemins et privilégie les voies vertes, sans chercher le dénivelé ; le
**VTT** privilégie les chemins plus fort que tous, et accepte le dénivelé ; la **marche** accepte les
chemins, y compris les sentiers de montagne, et le dénivelé - qui la raccourcit au lieu de l'allonger,
le détour évitant la côte coûtant plus cher que la côte.

Accepter les chemins n'est pas un détail de confort : c'est ce qui donne au vélo, dans le vocabulaire du
moteur, la monture capable d'emprunter les voies vertes françaises. Tracées pour la plupart sur d'anciennes
voies ferrées et déclarées gravillonnées dans OpenStreetMap, elles sont sinon fuies au profit de la
départementale qui les longe - le moteur y prêtant au cycliste une vitesse dérisoire.

La mesure depuis la position n'est proposée que le capteur allumé, et son origine est **figée** à la
première position reçue : la suivre lancerait une requête toutes les deux secondes.

## 13. Mesure d'une distance sur une trace

Un bouton de la carte, commandé par un réglage **désactivé par défaut**, ouvre une bande de consigne qui
demande deux points sur une trace affichée et rend la distance qui les sépare.

Cette mesure-ci ne demande **rien au réseau** : la distance suit le parcours de la trace déjà importée.
Elle se lit sur le **kilométrage cumulé des profils précalculés**, calculé sur la géométrie complète avant
décimation, et non sur la ligne allégée du rendu : la valeur est celle du parcours réel.

Chaque tap est rabattu sur son **projeté orthogonal** : il n'est pas nécessaire de viser la ligne au
pixel, et un tap au-delà d'un bout de trace se pose sur ce bout. Le second point est contraint au segment
du premier - mesurer suppose un parcours commun, et deux traces distinctes n'en offrent aucun.

Seules les traces **effectivement dessinées sous le doigt** sont lues, désignées par l'index de rendu de
la carte : lire le profil de toutes les couches visibles rendait l'attente proportionnelle à leur nombre,
alors que la réponse ne pouvait venir que d'une trace visible sous le tap.

L'infobulle du résultat s'ancre sur la portion mesurée, **au plus près de son milieu tout en restant dans
l'emprise de la carte** : on zoome pour poser le second point, et le milieu est alors souvent hors écran.
L'ancre glisse le long du parcours jusqu'au premier point visible, et revient au milieu dès qu'un
déplacement le ramène à l'écran.

Fermer la bande après un seul point efface ce point : un point unique ne mesure rien, et son marqueur
resterait sur la carte sans que rien ne l'explique. La croix de l'infobulle retire tout ; la mesure
disparaît aussi si sa trace est masquée, ou si le réglage qui l'a fait naître est coupé.

## 14. Planificateur d'itinéraire

Commandé lui aussi par un réglage désactivé par défaut, il ouvre une bande qui tient la liste des étapes -
au moins deux, jusqu'à 25, au-delà desquelles le moteur refuse la requête. Chaque étape est un lieu
cherché par son nom, ou **la position du porteur**, résolue au moment du calcul et non au moment du choix :
on pose ses étapes, puis on part de là où l'on est.

Un **historique des lieux** est proposé au focus d'un champ d'étape vide, au même moment et au même
endroit que la position du porteur : jusqu'à **huit** endroits, le plus récent en tête. Il s'efface dès la
première frappe - ce qu'on tape prime toujours sur ce qu'on a fait hier, et deux listes superposées
au-dessus d'un clavier ne se lisent pas. Un lieu déjà posé ailleurs dans le trajet n'y figure pas : le
choisir donnerait deux étapes au même endroit, donc un tronçon de longueur nulle.

**Quatre gestes le remplissent**, et non la seule saisie d'étape : une étape retenue ici, un lieu trouvé
par la recherche (section 11), un point d'intérêt dont on a ouvert l'infobulle (section 14 ter), l'adresse
d'un appui long sur la carte (section 12). Tous disent la même chose - voilà un endroit qui intéresse celui
qui tient le téléphone - et un trajet se compose rarement dans la foulée : on regarde la carte, on
consulte, et c'est plus tard qu'on veut y aller. Réduire l'historique aux champs du planificateur, c'était
ne se souvenir que des trajets déjà faits, jamais de celui qu'on prépare.

Ce sont des **lieux**, jamais du texte frappé : ce qui entre là porte des coordonnées, et se repose donc
dans une étape sans redemander quoi que ce soit au géocodeur. Une saisie abandonnée en cours de frappe n'y
a rien à faire. L'historique survit à la fermeture de l'application (colonne `settings.plannerHistory`, une
ligne par lieu, séparateurs tabulés - une adresse porte toujours des virgules, jamais de tabulation).

**L'historique s'efface**, et c'est la contrepartie de son remplissage automatique : ce qui s'inscrit sans
qu'on le demande doit pouvoir se retirer. Une croix sur chaque proposition en retire le lieu ; *Réglages /
Itinéraires* porte le compte des lieux retenus et le bouton qui vide tout. Une croix visible plutôt qu'un
appui long : un geste que rien n'annonce n'existe pas pour qui ne le connaît pas déjà. Sans confirmation -
huit lieux se reconstituent en une promenade, la demander serait du cérémonial.

Le plafond de huit est un compromis d'écran : la liste s'affiche sous un champ, au-dessus du clavier, et
au-delà elle chasserait de l'écran les propositions du géocodeur. Il était de cinq quand seules les saisies
d'étape le remplissaient ; les quatre sources l'épuisent bien plus vite - parcourir la carte avec la couche
des points d'intérêt allumée chassait cinq entrées en autant de taps.

Les étapes se réordonnent et se suppriment ; le parcours se recalcule à chaque changement, pour la
discipline réglée. Le résultat se dessine sur la carte et porte son propre profil altimétrique, avec le
même zoom et le même curseur que celui d'une trace.

La bande **se réduit** pour rendre la carte entière - c'est le geste attendu quand on veut regarder le tracé
qu'on vient de calculer -, et se referme sur une feuille vierge : rouvrir le planificateur ne doit pas
ressortir le trajet précédent.

Réduite, elle ne pose **aucun bouton à elle** : c'est le bouton habituel du calcul d'itinéraire, au coin
bas-droit, qui reparaît et redéploie le trajet en cours. Elle posait auparavant son propre bouton au coin
bas-gauche pendant que l'autre s'effaçait, si bien qu'un itinéraire en cours en affichait un à chaque bout
de l'écran, sans que rien ne dise lequel faisait quoi. Le bouton passe au bleu tant qu'un trajet est en
cours, comme le suivi de position et la couche des points d'intérêt : il ne dit plus seulement « calculer
un itinéraire », il dit aussi « il y en a un rangé là-dessous ».

**Le retour Android ne fait pas perdre un trajet d'un seul geste.** Bande déployée, il la replie ; bande
déjà réduite, il **demande** - « Un itinéraire est en cours de création / Voulez-vous annuler ? », *Non* ou
*Oui*. C'est le même geste que celui qui quitte l'application, donc celui qu'on fait sans y penser, et un
trajet composé étape par étape ne se perd pas comme cela. La croix de l'en-tête, elle, ferme sans rien
demander : c'est un geste visé, posé sur le bouton qui dit « fermer ».

**Une rotation ne le perd pas non plus.** Le travail en cours de la carte - le trajet composé étape par
étape, la mesure dont le premier point vient d'être posé, l'emprise hors-ligne à moitié tracée, le mode
retouche - survit à un quart de tour du poignet. Il partait jusqu'ici avec l'activité, que le système
recrée à chaque changement d'orientation, et le contraste était devenu gênant : le retour Android
*demandait* avant de perdre un itinéraire, pendant qu'un mouvement du poignet l'emportait sans un mot.

## 14 bis. Pendant la sortie

**Le suivi ne s'arrête pas avec l'écran.** Tant que la position est allumée, elle continue d'être reçue
l'écran éteint, l'application en arrière-plan, le téléphone en poche - c'est là que le suivi sert, et il
s'arrêtait jusqu'ici à la première mise en veille. Une **notification permanente** l'annonce, dit l'écart à
la trace suivie quand il y en a une, et porte un bouton d'arrêt ; le bouton de position de la carte reste le
second. Le suivi s'arrête aussi de lui-même si la localisation est éteinte dans le téléphone, ou si
l'application est balayée des tâches récentes : c'est une fonction de l'application, pas un agent qui lui
survit.

**La veille survit à la mort du processus.** Android reprend sa mémoire quand il en manque, et tue
l'application même pendant une sortie - il relance ensuite le service, qui est fait pour cela. Le capteur
repartait donc, et la notification aussi ; mais la trace suivie, elle, vivait en mémoire et revenait à
rien : l'**alerte d'éloignement se retrouvait désarmée sans un mot**, téléphone en poche et notification
affichée. Elle est désormais gardée sur le disque et reprise au redémarrage, parcours du planificateur
compris. Elle n'y reste que le temps du suivi : l'arrêter l'efface, sans quoi un suivi arrêté à midi
reprendrait tout seul le soir.

**Garder l'écran allumé** est un réglage à part (onglet Carte, éteint par défaut), et il ne sert plus à ce
qu'on croit : le suivi n'en a plus besoin pour vivre. Il sert à garder la carte **lisible** - téléphone sur
un guidon, consulté d'un coup d'oeil sans y toucher toutes les minutes. Le drapeau n'est posé que **pendant
le suivi** : allumer indéfiniment l'écran de qui consulte ses traces chez lui n'aurait aucun sens, et
l'écran se rendort dès que la position s'arrête.

Le point courant du profil se déplace **de façon continue** le long du parcours : il se pose entre deux
sommets, à l'endroit exact visé, et non d'échantillon en échantillon comme avant.

Le capteur allumé et un profil ouvert, la position est **projetée sur la trace affichée** (réglage, onglet
Carte > Profil altimétrique > Affichage) : la barre du
profil annonce ce qui reste à parcourir et à monter jusqu'au bout - le D+ restant, parce que la distance
seule ne dit pas si les trois derniers kilomètres sont une descente ou le mur du col. Au-delà de 50 m
d'écart, l'écart à la trace est dit lui aussi : le "restant" ne décrit alors plus le chemin qu'on suit.

Une trace **sans horodatage** reçoit un temps de marche estimé, marqué d'un `~`, calculé d'après la pente
(fonction de Tobler) : cinq kilomètres de plat et cinq kilomètres de raide ne se marchent pas dans le même
temps, et c'est la question qu'on se pose devant une trace inconnue.

### Alerte d'éloignement

Un bouton **Suivi de trace**, sous le planificateur, pour être **prévenu** au lieu d'avoir à surveiller.
Il porte le dessin d'une route et non plus une cloche : une cloche disait l'alerte, quand ce bouton ouvre
le suivi d'une trace dont l'alerte n'est qu'une conséquence. Commandée par un
réglage désactivé par défaut (onglet Carte > Alerte d'éloignement), elle ne s'affiche jamais sans le bouton
de localisation : une alerte n'a que la position pour matière, et l'allumer sans que rien ne montre ni ne
coupe le capteur serait une position qui tourne en cachette. Les deux réglages sont donc liés dans les deux
sens - cocher l'alerte affiche le bouton GPS, décocher le bouton GPS éteint l'alerte.

Un tap sur le bouton demande **quelle trace on suit**, en proposant celles qui passent le plus près de la
position, chacune avec son écart. Ce n'est pas la bibliothèque : suivre une trace, c'est suivre celle sur
laquelle on se tient, et un catalogue de deux cents entrées ne répondrait pas à la question. Les couches
masquées n'y figurent pas, et une couche qui porte plusieurs segments les numérote.

**Le bouton n'exige pas le repère affiché.** Il consultait le bouton de localisation de la carte, qui ne
commande que l'affichage, et refusait la liste alors que le téléphone savait parfaitement où l'on était.
Seule la localisation du système, coupée, empêche - elle mène alors droit aux réglages, et la liste
demandée s'ouvre au retour. Choisir une trace **allume le suivi tout seul** : c'est ce qu'on venait
demander, et l'alerte a besoin du service pour mesurer l'écart écran éteint.

**Une trace suivie remplace la liste par un tableau de bord.** La fenêtre consacrait tout son espace à la
seule chose dont on n'a plus besoin - choisir - quand la question devenue urgente est combien reste-t-il, et
combien de montée. La trace suivie occupe un en-tête, et dessous neuf indicateurs à pictogrammes : vitesse,
temps écoulé et restant, distance parcourue et restante, dénivelé positif et négatif faits et devant. Un
appui long en donne le nom. Le dénivelé est interpolé aux deux bouts, sans quoi il sauterait à chaque sommet
franchi ; le temps restant part de la vitesse **moyenne** tenue, l'instantanée tombant à zéro à chaque arrêt
et annonçant alors l'infini. L'arrêt du suivi rend la liste.

**Le parcours du planificateur y figure en premier**, dès qu'il en existe un, et sans avoir été importé
dans la bibliothèque : c'est le cas le plus courant - on compose son trajet, on part, et on veut être
prévenu si on le quitte. L'importer d'abord serait un détour, et laisserait derrière soi une couche dont on
ne voulait pas. Il passe en tête hors classement, une trace de la bibliothèque qui passerait dix mètres
plus près ne répondant pas à la question qu'on pose en touchant ce bouton. Le suivi s'arrête si le calcul
d'itinéraire est fermé - le parcours quitte alors la carte, comme une couche qu'on masque ; un simple
recalcul, lui, ne l'interrompt pas.

Ensuite, chaque position est projetée sur la trace suivie, et au-delà de l'**écart réglé** (de 20 à 500 m,
par pas de 10) une bannière rouge s'affiche en bas de l'écran, avec la distance et le nom de la trace. Elle
passe par-dessus tout ce qui occupe le bas - profil, bande du planificateur, consignes de saisie : c'est la
seule barre à s'accorder ce droit, les autres accompagnant un geste qu'on vient de faire. Un **son** peut
l'accompagner, choisi parmi les notifications du téléphone par le sélecteur du système ; il sonne à l'entrée
en alerte, une fois, et non tant qu'on est loin.

L'alerte s'allume à l'écart réglé mais ne se lâche qu'à 80 % de celui-ci : sans cette marge, une position
qui oscille autour du seuil - le lot d'un GPS de téléphone sous couvert - rallumerait la bannière et son son
toutes les deux secondes. La croix de la bannière tait l'écart du moment sans arrêter le suivi ; revenir sur
la trace réarme l'alerte suivante. Le suivi, lui, s'arrête depuis son bouton, là où il a commencé - et de
lui-même si le capteur s'éteint ou si la trace quitte la carte.

## 14 ter. Points d'intérêt

Une couche de **points d'intérêt** se pose sur la carte, tirée de **deux sources** : **DATAtourisme**, la
base publique française du tourisme (Licence Ouverte Etalab 2.0), et **OpenStreetMap** (licence ODbL), toutes
deux mentionnées dans l'onglet Trajets.

**Qui répond, et où.** Hors de France, DATAtourisme n'a rien à dire : OpenStreetMap répond seul, pour toutes
les catégories cochées. En France, DATAtourisme garde **l'hébergement et les loisirs** - qu'il décrit mieux et
illustre de photos - et OpenStreetMap complète le groupe *pratique*, celui des services, **et la
restauration**. Le partage n'a rien d'arbitraire : sur un écran de carte autour de Grenoble, DATAtourisme rend
49 hôtels contre 38 à OSM, mais **zéro** point d'eau, zéro toilettes publiques, zéro aire de pique-nique et
zéro borne de recharge, là où OSM en porte plusieurs centaines. Sur le centre d'Albi, il rend **6 restaurants
contre 150**, et les six sont des hôtels : un restaurant de quartier n'est pas un objet touristique, il
n'entre dans cette base que s'il est adossé à un hébergement. Un même lieu connu des deux ne paraît qu'une
fois. Un groupe limité au thème vélo, lui, reste à DATAtourisme où qu'on soit : OpenStreetMap ne porte pas
l'équivalent de ce thème, et montrer des hébergements quelconques sous un filtre "vélo" serait promettre ce
qu'on ne sait pas.

**Le complément se coupe.** Une requête OpenStreetMap est longue, et un réglage - *Compléter avec
OpenStreetMap* - rend la France à la base touristique seule, pour qui préfère une carte immédiate à une carte
complète. Il ne touche que le complément : hors de France, OpenStreetMap répond quoi qu'il arrive, faute de
quoi la couche serait vide sans que rien ne l'explique.

**Un lieu est ce qu'il est, pas ce qu'on a coché.** Un hôtel-restaurant est un hôtel, et ne s'affiche pas
sous le seul filtre "Restaurants" ; des toilettes publiques que la base classe aussi en camping-car restent
des toilettes, et disparaissent si l'on masque les toilettes. Les filtres décident de ce qu'on **voit**,
jamais de ce qu'un lieu **est** - faute de quoi la carte promet des restaurants et montre des hôtels.

Un lieu d'OpenStreetMap n'a **pas toujours de nom** - une fontaine ou des toilettes n'en portent presque
jamais - et son infobulle prend alors le nom de sa catégorie. Son bouton n'apparaît sur
la carte que si un réglage l'y met - comme la recherche de lieu ou la mesure : c'est une commande qui
s'ajoute volontairement, et elle interroge un service tiers à chaque déplacement de carte.

**Vingt-sept catégories, en quatre groupes** - hébergement, restauration, loisirs, pratique - reprises du
planificateur de France Vélo Tourisme. Le réglage les décline en cases à cocher par groupe, avec un « tout
sélectionner » et un filtre **thème vélo** propre à chaque groupe : on veut des hébergements qui accueillent
les cyclistes sans exiger la même chose des points d'eau. Ce sont les catégories **masquées** qui sont
enregistrées, si bien qu'un réglage vierge montre tout, et qu'une catégorie ajoutée plus tard apparaît
d'elle-même.

Chaque lieu est un **marqueur en forme de goutte**, à la couleur de son groupe et portant le pictogramme de
sa catégorie - une tente pour un camping, un panier pour un marché, une goutte pour un point d'eau. Un tap
ouvre son infobulle : sa **photo en image de garde** quand il en publie une (un lieu sur trois environ), son
nom par-dessus sur fond blanc, cliquable vers son site s'il en a un, et le badge de sa catégorie. Puis les
**trois actions d'itinéraire** - définir comme point de départ, comme point d'arrivée, ajouter l'étape - qui
remplissent le planificateur et l'ouvrent. **Ouvrir l'infobulle suffit** à inscrire le lieu dans
l'historique du planificateur (section 14), sans attendre l'une des trois : on regarde d'abord, on compose
ensuite, parfois bien plus tard.

**Les sources sont interrogées en parallèle, et chacune s'affiche dès qu'elle répond.** DATAtourisme rend en
une seconde ; OpenStreetMap est découpé **par groupe** - hébergement, restauration, loisirs, pratique - et
chaque groupe part de son côté. Mesuré sur une ville dense : trois secondes pour les hôtels et les
restaurants, seize pour les loisirs, vingt-trois pour les services, là où une requête unique mettait trente
secondes sans rien montrer avant la fin. Le bouton de la couche porte l'attente : son pictogramme cède la
place à un rond qui tourne tant qu'une source travaille.

Le chargement suit la carte : les lieux de la **zone visible**, un demi-instant après le dernier geste, et
rien de redemandé tant que la vue reste dans ce qui a déjà été chargé. En deçà d'un certain zoom, rien n'est
demandé du tout - l'écran porterait des milliers de lieux dont le service ne rendrait qu'une poignée, prise
au hasard - et la carte le dit. **Au-dessus de ce zoom, si une source connaît plus de lieux qu'elle n'en
rend, la carte le dit aussi** : le taire donnait un affichage qui avait l'air juste et dont les marqueurs
changeaient d'un déplacement au suivant. Une emprise ainsi tronquée n'est **pas** retenue comme chargée :
tout geste la redemande, zoom compris - et c'est justement le zoom qui rend la réponse complète, puisqu'il
resserre le cadre. **Une emprise n'est d'ailleurs retenue que si le chargement va à son terme**, toutes
sources arrivées : les sources publient chacune à son arrivée, et retenir l'emprise sur la première laissait
la carte figée sur elle dès qu'un geste annulait le reste - à Albi, un dézoom suivi d'un zoom faisait
disparaître les restaurants sans retour. Une source qui **n'a pas répondu** met en revanche la zone au repos
une minute avant qu'on la redemande : sans ce frein, chaque geste relançait la requête que le service venait
de refuser, et il la refusait d'autant plus fort. **Ce message-là se tape**, et amène la carte au zoom minimum qui
charge, autour du centre courant : une consigne qu'on peut exécuter soi-même est une consigne de trop. Il
disparaît **dès que le zoom est suffisant**, sans attendre les points : le laisser pendant le chargement
faisait zoomer encore et encore, croyant n'être jamais assez près.
Il porte pour cette raison la couleur des commandes, là où les deux autres messages du même bandeau - pas
de réseau, points du cache - restent en texte ordinaire : ce sont des constats, que rien ni personne ne
lève d'un doigt. Ce qui a été vu une fois est **gardé une semaine** : sans réseau, la
couche montre les derniers points connus et l'annonce ; si elle ne connaît rien de cette zone, elle réclame
une connexion plutôt que de laisser croire à une région sans un seul café.

**Une zone téléchargée hors ligne peut emporter ses lieux** avec ses tuiles, par une case de l'écran de
configuration - présente seulement si la couche est allumée, cochée d'office si elle l'est. C'est ce qui
manquait pour tenir la promesse du hors-ligne : le cache ordinaire ne retient que ce qu'on a survolé
**connecté**, si bien que la couche était vide précisément là où l'on n'était jamais allé avec du signal -
l'endroit où elle sert le plus. Chercher un point d'eau à 18 h dans une vallée sans réseau est exactement le
cas d'usage.

**Le cache se vide à la demande**, sous les catégories dans *Réglages / Trajets* : il porte le compte des
lieux retenus et la corbeille qui les efface. C'est ce qui manquait quand une source rend une fiche fausse -
rien ne permettait alors de la forcer à redemander, et il fallait attendre la semaine de péremption. Les
lieux **emportés** avec une zone hors ligne, eux, ne sont pas touchés, et l'écran le dit : un cache se refait
tout seul à la première zone survolée avec du réseau, une provision non.

Ces lieux-là sont **marqués** et échappent au ménage hebdomadaire : une zone emportée pour un séjour de
quinze jours se viderait sinon au huitième, sans réseau pour la refaire. Le semis a lieu après les tuiles et
seulement si elles ont abouti ; s'il échoue, la carte reste acquise et l'écran de fin le dit en une ligne,
sans se transformer en erreur.

## 15. Réglages

Quatre onglets :

- **Carte** : boutons affichés sur la carte, échelle, rotation, suivi de la position et écran maintenu
  allumé pendant celui-ci, alerte d'éloignement (son bouton, l'écart
  qui la déclenche, son son), repère de position, gestionnaire de fonds,
  marqueurs et infobulles, apparence du profil. Quatre boutons sont posés sur la carte **par défaut** : le burger, le
  GPS, le gestionnaire de fonds et le planificateur - plus le fond blanc translucide qui les porte. La
  recherche de lieu, la mesure sur trace, la retouche des traces et l'alerte d'éloignement, elles, ne le
  sont pas : ce sont celles qui s'ajoutent volontairement.
- **Tuiles** : fond par défaut, catalogue des fournisseurs et composites, import/export
  (cf. [`BASEMAPS.md`](BASEMAPS.md)).
- **Trajets** : URL du géocodeur, URL du moteur d'itinéraire, discipline par défaut, catégories de points
  d'intérêt et vidage de leur cache, calcul du profil, et
  le complètement de l'altimétrie manquante avec ses deux services (cf. section 6).
- **Système** : dossiers d'import et des MBTiles, **sauvegarde et restauration**, menu latéral, tolérances
  de tap, simplification du rendu,
  unités, mises à jour, langue, thème, avatar, remise à zéro.

Tout tient dans une seule ligne de base, écrite à chaque changement.

## 16. Mises à jour

L'application lit un manifeste publié par sa propre CI, compare les versions, puis télécharge et lance
l'installation. Réglable en automatique ou manuel. Détail dans
[`WORKFLOW.md`](WORKFLOW.md#6-mises-à-jour-automatiques).

## 17. Langues

Interface en français, anglais, allemand, espagnol, catalan, basque, italien et portugais. La langue se
choisit dans les réglages, indépendamment de celle du système.
