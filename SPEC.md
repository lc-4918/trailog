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
- préparer un itinéraire par étapes.

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
- **Les commandes se répartissent en trois coins.** En haut à gauche : le menu, l'interrupteur GPS, la
  recherche de lieu (dont la barre de saisie se déplie juste dessous, ce qui la retient là) et la mesure
  sur trace. En bas à droite, à portée du pouce : le recentrage sur la position, la cloche de l'alerte
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
depuis une vue à l'échelle d'un pays ; son infobulle en donne l'adresse, et rien de plus.

## 12. Point quelconque de la carte (appui long)

Un appui long hors d'une trace et d'un marqueur pose une épingle et ouvre une infobulle qui répond à trois
questions : quelle adresse est là, à quelle distance elle est de la position GPS, et à quelle distance
elle est d'un second point désigné d'un tap.

L'adresse vient du **géocodage inverse** de Photon, servi par un chemin frère de la recherche (`/reverse`
là où celle-ci est `/api`) : même instance, même URL réglée. Ce géocodage-ci ne dépend pas de
l'interrupteur de la recherche - il n'y a pas de bouton à cacher, et rien ne part tant que le doigt ne
s'attarde pas.

Les deux mesures donnent la **distance et la durée d'un itinéraire** suivant la voirie, non un vol
d'oiseau, pour l'une des cinq disciplines réglables : vélo de route, gravel, VTC, VTT, à pied. Le moteur
est **Valhalla**, retenu parce que ses cinq disciplines sortent d'une seule instance via `bicycle_type`
(OSRM en exigerait cinq, GraphHopper n'a pas d'instance publique sans clé). Son URL est un réglage, comme
celle du géocodeur. L'itinéraire calculé est tracé sur la carte, sous les épingles et teinté par classe de
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

Les étapes se réordonnent et se suppriment ; le parcours se recalcule à chaque changement, pour la
discipline réglée. Le résultat se dessine sur la carte et porte son propre profil altimétrique, avec le
même zoom et le même curseur que celui d'une trace.

La bande se réduit à un bouton pour rendre la carte entière, et se referme sur une feuille vierge :
rouvrir le planificateur ne doit pas ressortir le trajet précédent.

## 14 bis. Pendant la sortie

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

Une cloche, sous le planificateur, pour être **prévenu** au lieu d'avoir à surveiller. Commandée par un
réglage désactivé par défaut (onglet Carte > Alerte d'éloignement), elle ne s'affiche jamais sans le bouton
de localisation : une alerte n'a que la position pour matière, et l'allumer sans que rien ne montre ni ne
coupe le capteur serait une position qui tourne en cachette. Les deux réglages sont donc liés dans les deux
sens - cocher l'alerte affiche le bouton GPS, décocher le bouton GPS éteint l'alerte.

Un tap sur la cloche demande **quelle trace on suit**, en proposant celles qui passent le plus près de la
position, chacune avec son écart. Ce n'est pas la bibliothèque : suivre une trace, c'est suivre celle sur
laquelle on se tient, et un catalogue de deux cents entrées ne répondrait pas à la question. Les couches
masquées n'y figurent pas, et une couche qui porte plusieurs segments les numérote. Le capteur éteint, la
cloche propose d'abord de l'allumer, puis ouvre son choix une fois la position reçue.

Ensuite, chaque position est projetée sur la trace suivie, et au-delà de l'**écart réglé** (de 20 à 500 m,
par pas de 10) une bannière rouge s'affiche en bas de l'écran, avec la distance et le nom de la trace. Elle
passe par-dessus tout ce qui occupe le bas - profil, bande du planificateur, consignes de saisie : c'est la
seule barre à s'accorder ce droit, les autres accompagnant un geste qu'on vient de faire. Un **son** peut
l'accompagner, choisi parmi les notifications du téléphone par le sélecteur du système ; il sonne à l'entrée
en alerte, une fois, et non tant qu'on est loin.

L'alerte s'allume à l'écart réglé mais ne se lâche qu'à 80 % de celui-ci : sans cette marge, une position
qui oscille autour du seuil - le lot d'un GPS de téléphone sous couvert - rallumerait la bannière et son son
toutes les deux secondes. La croix de la bannière tait l'écart du moment sans arrêter le suivi ; revenir sur
la trace réarme l'alerte suivante. Le suivi, lui, s'arrête depuis la cloche, là où il a commencé - et de
lui-même si le capteur s'éteint ou si la trace quitte la carte.

## 15. Réglages

Quatre onglets :

- **Carte** : boutons affichés sur la carte, échelle, rotation, alerte d'éloignement (son bouton, l'écart
  qui la déclenche, son son), repère de position, gestionnaire de fonds,
  marqueurs et infobulles, apparence du profil. Quatre boutons sont posés sur la carte **par défaut** : le burger, le
  GPS, le gestionnaire de fonds et le planificateur - plus le fond blanc translucide qui les porte. La
  recherche de lieu, la mesure sur trace, la retouche des traces et l'alerte d'éloignement, elles, ne le
  sont pas : ce sont celles qui s'ajoutent volontairement.
- **Tuiles** : fond par défaut, catalogue des fournisseurs et composites, import/export
  (cf. [`BASEMAPS.md`](BASEMAPS.md)).
- **Trajets** : URL du géocodeur, URL du moteur d'itinéraire, discipline par défaut, calcul du profil, et
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
