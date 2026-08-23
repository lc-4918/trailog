# Contexte - Trailog

Ce document explique **pourquoi** l'application est faite ainsi. Les autres documents disent autre
chose : [`README.fr.md`](README.fr.md) ce qu'elle fait pour l'utilisateur, [`DEVELOPER.md`](DEVELOPER.md)
comment la compiler et s'y repérer, [`SPEC.md`](SPEC.md) ce qu'elle fait en détail,
[`BASEMAPS.md`](BASEMAPS.md) ses fonds de carte, [`WORKFLOW.md`](WORKFLOW.md) comment elle est
distribuée, [`TESTS.md`](TESTS.md) comment elle est vérifiée.

Il s'adresse à qui reprend le code et se demande pourquoi tel choix a été fait plutôt qu'un autre.

- [D'où vient le projet](#doù-vient-le-projet)
- [Ce que l'application est, et n'est pas](#ce-que-lapplication-est-et-nest-pas)
- [Les contraintes qui expliquent tout le reste](#les-contraintes-qui-expliquent-tout-le-reste)
- [Décisions structurantes](#décisions-structurantes)
- [Pièges connus](#pièges-connus)

## D'où vient le projet

Trailog est le portage Android natif d'une application web personnelle (`tefeciste/2024`, dont
`js/map.js` a fourni le registre de fonds de carte initial, et dont le profil altimétrique
réimplémente la logique de `ol-elevation-profile`). Le dépôt démarre le 11 juillet 2026.

C'est une application **personnelle**, écrite pour un usage de randonnée et de vélo, publiée sous
**GPL v3**. Elle n'a ni utilisateurs à satisfaire, ni feuille de route commerciale, ni store à
ménager. Cela explique plusieurs choix qui seraient discutables ailleurs.

## Ce que l'application est, et n'est pas

**Est** : un consultateur de traces. On importe des fichiers GPX, GeoJSON, KML et KMZ, on les range en
dossiers, on les regarde sur une carte avec un profil altimétrique synchronisé, et on emporte le tout
hors ligne.

**N'est pas** : un enregistreur GPS, un service en ligne. Il n'y a ni compte, ni synchronisation, ni
télémétrie. Tout vit sur l'appareil.

**Est devenu**, en cours de route, un retoucheur de traces : couper, joindre, fusionner, inverser un
segment désigné du doigt (cf. [`SPEC.md`](SPEC.md#6-bis-retoucher-une-trace)). C'était un non-objectif au
démarrage, et la nuance vaut d'être dite - retoucher ce qu'on a importé n'est pas le dessiner : il faut
une trace pour commencer, l'outil ne fabrique rien à partir de rien, et la fonction reste **absente par
défaut**. Elle s'allume dans les réglages, comme tout ce qui détourne les taps de la carte.

Le dessin à la main et l'**enregistrement** d'une trace, eux, étaient explicitement hors périmètre au
démarrage (cf. [`SPEC.md`](SPEC.md#1-périmètre)) et le restent. La position, elle, est bien suivie en temps
réel depuis longtemps - le repère, l'alerte d'éloignement, le restant du profil - et depuis peu hors de
l'écran, dans un service qui survit à sa mise en veille : c'est ce qui manquerait à un enregistreur si l'on
en voulait un un jour, mais l'application n'écrit toujours rien de ce qu'elle reçoit.

## Les contraintes qui expliquent tout le reste

**Hors ligne d'abord.** L'usage cible est le terrain, sans réseau. Tout ce qui peut être précalculé
l'est, tout ce qui peut être stocké localement l'est. Une fonctionnalité qui exige le réseau doit
dégrader proprement, pas échouer - et si elle ne peut pas s'en passer du tout, comme la recherche
d'adresse, elle reste optionnelle et éteinte par défaut.

**Aucun store.** La distribution passe par GitHub Releases. Il n'y a donc personne pour prévenir
l'utilisateur qu'une version existe : l'app doit s'en charger elle-même. Cela justifie la permission
`REQUEST_INSTALL_PACKAGES`, qui vaudrait un refus sur le Play Store.

**minSdk 24** (Android 7.0). Plusieurs API confortables ne sont pas disponibles : l'autorisation
d'installer par application n'existe que depuis Android 8, `Modifier.blur` depuis Android 12. Chaque
usage doit prévoir le chemin ancien.

**Un seul développeur.** Pas de revue de code, pas de garde-fou humain. D'où l'insistance sur les
tests des zones où une faute ne se voit pas : migrations de base, contrat entre la CI et l'app,
intégrité du catalogue de fonds.

## Décisions structurantes

### Room pour le catalogue, fichiers pour la géométrie

Les dossiers, couches, fonds et réglages vivent dans Room. La **géométrie** des traces vit dans des
fichiers GeoJSON séparés, référencés par `LayerEntity.geometryFile`.

Pourquoi : une trace fait couramment plusieurs milliers de points. Les stocker en base rendrait chaque
requête lourde, alors que la géométrie n'est lue qu'au moment de l'afficher. MapLibre sait par ailleurs
lire un fichier GeoJSON par URI `file://` sur son propre thread, sans passer par nous.

### Trois fichiers par couche

Pour une couche importée, on écrit :

| Fichier | Rôle |
|---|---|
| `<nom>` | GeoJSON source, géométrie complète. Référence pour le profil, l'édition et l'export |
| `<nom>.map` | GeoJSON prêt pour la carte : lignes simplifiées, points annotés pour le tap |
| `<nom>.prof` | profil précalculé (échantillons décimés et statistiques), par segment |

Pourquoi : recalculer le profil ou simplifier la géométrie à chaque affichage saturait le processeur
sur les grosses traces. Le coût est payé une fois, à l'import.

La simplification (Douglas-Peucker, tolérance 1 m) ne touche **que** le fichier de rendu. Les
statistiques et le profil restent calculés sur la géométrie brute : simplifier avant de mesurer
fausserait les distances et le dénivelé.

### Migrations explicites, jamais destructives

Chaque évolution du schéma passe par une `Migration` écrite à la main
(cf. [`TESTS.md`](TESTS.md#datadb---base-de-données)). `fallbackToDestructiveMigration` reste déclaré
en dernier recours, mais aucune migration ne doit l'atteindre.

Pourquoi : une recréation de base effacerait les couches importées de l'utilisateur, en silence, au
premier lancement d'une nouvelle version. C'est la faute la plus coûteuse possible ici, et la moins
visible : le build passe, les tests passent, et les données disparaissent chez l'utilisateur.

### Le catalogue de fonds n'est semé qu'à vide

`TrailogRepository.ensureSeed` n'insère les fonds par défaut que si la table est vide.

Pourquoi : l'utilisateur peut supprimer un fond, en modifier l'URL ou la clé. Un seed rejoué à chaque
lancement les ressusciterait ou écraserait ses modifications.

**Conséquence à connaître** : ajouter un fond à `Providers.defaults()` ne suffit pas. Il n'apparaîtra
que sur une installation neuve. Pour une base déjà en place, il faut l'insérer par une migration
(cf. la 18-19, qui ajoute le fond AF3V).

### L'import entre aussi par une intention

Le sens sortant existait depuis longtemps - partager une trace en GPX -, mais l'entrant passait
obligatoirement par le bouton *Importer* : un GPX reçu par courriel ne pouvait pas s'ouvrir dans Trailog.
Le manifeste déclare donc les filtres qui font apparaître l'application dans le "Ouvrir avec" et le
"Partager vers".

Deux filtres et non un, parce que les fournisseurs de fichiers ne s'accordent pas : les uns annoncent un type
MIME propre, les autres retombent sur `application/octet-stream` ou `text/xml` et ne laissent que l'extension
pour trancher. Le second n'est délibérément **pas** un `*/*` général : sans son `pathPattern`, Trailog se
proposerait pour tout fichier du téléphone.

Deux conséquences à connaître. L'activité passe en `singleTask` : sans cela, un fichier ouvert pendant que
l'application tourne empilerait une seconde instance, carte et position recréées à côté de celles qui
tournent déjà. Et l'intention est **marquée une fois lue** (`data/imp/ImportInbox`) : l'activité la relit à
chaque recréation, et une rotation d'écran aurait réimporté le même fichier.

### Version dérivée du tag git

`versionCode` et `versionName` sont calculés par `build.gradle.kts` à partir de `git describe`, jamais
codés en dur.

Pourquoi : une version codée en dur finit oubliée, et toutes les releases se déclarent alors avec le
même `versionCode`. Android refuse alors la mise à jour, sans expliquer pourquoi.

**Conséquence** : un build hors tag porte un suffixe descriptif (`0.2.0-23-gabc1234`). C'est pour cela
que la comparaison des versions porte sur `versionCode`, un entier, et jamais sur `versionName` : un
découpage numérique de ce suffixe lirait `0.2.0-23` comme plus récent que `0.2.0`.

### Pas de NavHost

`AppRoot` superpose les réglages à la carte dans une `Box`, au lieu de naviguer.

Pourquoi : détruire et recréer la `MapView` réinitialise le zoom et provoque un scintillement. La
carte doit survivre à l'ouverture des réglages.

### La langue est à part

Tous les réglages vivent dans la table `settings`, sauf la langue, qui est dans des
`SharedPreferences` (`LocalePrefs`).

Pourquoi : elle est lue dans `attachBaseContext`, avant que la base ne soit ouverte.

### Le relief n'est pas un fond

Un provider de type `DEM` fournit des tuiles d'altitude brutes, illisibles telles quelles. Il n'est
jamais sélectionnable comme fond visuel : un tap sur lui, dans le gestionnaire, allume ou éteint son
ombrage au lieu de remplacer le fond courant.

**Deux états, et non un.** Son `enabled` ne dit que sa présence dans le gestionnaire, exactement comme
pour les autres fonds, et se règle au même endroit qu'eux (*Réglages / Tuiles / Fournisseurs*).
L'ombrage lui-même vit dans `settings.hillshadeOn`, et c'est le tap qui le bascule.

Pourquoi les séparer : confondus, un tap qui éteignait le relief le retirait du gestionnaire, sans plus
aucun moyen de le rallumer. Les garder confondus imposait donc de l'y laisser visible quoi qu'il arrive
- une exception dans le filtre, et un fond décoché qui s'affichait quand même.

Conséquence à connaître : le style ne pose l'ombrage que si les **deux** sont vrais. Retiré du
gestionnaire, le relief s'éteint aussi sur la carte : l'y laisser allumé le rendrait indélogeable, son
interrupteur ayant disparu.

### Le géocodage est optionnel, et son service interchangeable

Chercher une adresse exige un service en ligne : rien n'est géocodable hors ligne à cette échelle. La
fonction est donc **désactivée par défaut** et s'active dans *Réglages / Carte*.

Le service retenu est **Photon**, et non Google. Google impose une clé facturée, et ses conditions
réservent l'affichage des résultats à une carte Google, alors que l'application rend les siennes avec
MapLibre. Nominatim, l'autre géocodeur OSM connu, **interdit** l'autocomplétion au clavier dans sa
politique d'usage. Photon est le seul à la fois prévu pour la frappe et **auto-hébergeable**.

C'est pour cela que son URL est un réglage et non une constante : le jour où une instance personnelle
tourne, il suffit de la désigner. Le défaut vide, et non l'URL publique écrite en base, évite qu'un
changement de défaut dans le code laisse les installations existantes sur l'ancienne adresse.

Le moteur d'itinéraire suit la même règle. **Valhalla** y est retenu pour une raison qui lui est propre :
ses cinq disciplines (route, gravel, VTC, VTT, marche) sortent d'une **seule** instance, via le
`bicycle_type` de son modèle de coût. OSRM demanderait un serveur par profil, soit cinq à héberger, et
GraphHopper n'offre pas d'instance publique sans clé. BRouter, seul à savoir router hors ligne, resterait
le choix du jour où l'on voudrait s'affranchir du réseau : il faudrait alors gérer ses tuiles `.rd5`.

BRouter l'a rejoint depuis, puis lui a pris la place de moteur par défaut, et pour une raison précise : le modèle de coût de
Valhalla est compilé dans son code, et l'instance publique n'en expose qu'une poignée de curseurs. Deux
choses s'y sont révélées impossibles, mesures à l'appui - privilégier un sentier au MARCHEUR, dont le
modèle piéton ignore les relations d'itinéraire et ne connaît que trottoirs et chemins d'exploitation, et
corriger la vitesse dérisoire qu'il prête au cycliste sur les voies vertes gravillonnées. BRouter reçoit
au contraire le profil entier avec la requête ; ce qu'on ne peut pas dire à l'autre s'y écrit. Le prix
est symétrique : cinq profils à tenir, repris du dépôt BRouter et réglés ligne par ligne, et une
conversation en deux temps - déposer le profil, puis calculer sous l'identifiant rendu. Valhalla reste
réglable d'un tap, et garde un avantage propre : son graphe hiérarchique répond plus vite sur les longues
distances, là où BRouter explore à plat.

Conséquence à connaître : une mesure de distance est une **requête réseau**, là où le vol d'oiseau ne
coûtait rien. L'origine de la mesure depuis la position est donc figée à la première position reçue et ne
suit pas le capteur : la suivre lancerait une requête toutes les deux secondes.

### L'adresse d'un point n'attend pas le réglage du géocodage

L'appui long sur la carte cherche l'adresse du point touché (géocodage **inverse**) même si la recherche
par le nom est éteinte, et vise alors l'instance par défaut.

Pourquoi cette exception : le réglage sert à ne pas poser sur la carte un bouton de recherche dont la
frappe n'aboutirait à rien. Ici il n'y a pas de bouton, et rien ne part tant que le doigt ne s'attarde pas
sur un endroit précis - le geste EST la demande. Le cacher derrière un interrupteur reviendrait à rendre
muet un appui long, sans que rien à l'écran ne dise pourquoi.

Le prix à connaître : c'est le seul endroit où l'application interroge un service tiers sans que
l'utilisateur l'ait autorisé dans ses réglages. Il reste maître de l'instance visée (la même URL, sa
terminaison changée) et, à défaut, du geste : on ne s'attarde pas par accident.

### DATAtourisme : ce que l'API ne dit pas, et ce qu'elle ne sait pas

Les points d'intérêt viennent de **DATAtourisme** parce que c'est la base publique française du tourisme,
sous Licence Ouverte, sans clé facturée ni quota qui gêne un usage personnel. Le choix n'a rien coûté ; sa
mise en oeuvre, si.

**Sa documentation et sa réponse divergent**, et chaque écart se paie en silence - une requête mal formée ne
lève pas, elle rend zéro lieu, et une carte sans marqueur ressemble à une carte sans marqueur. Quatre points
ont dû être établis en interrogeant le service pour de vrai, et chacun est verrouillé par un test :

- les conditions de filtre se combinent par ` AND `, jamais par une virgule ni un `&` (erreur 400) ;
- le chemin d'un thème est `hasTheme.key` et non `hasTheme` - ce dernier rend **zéro résultat sans
  protester**, la faute la plus coûteuse de tout ce client ;
- le paramètre `fields` est nécessaire pour obtenir le thème et l'image, absents de la liste par défaut ;
- `geo`, `address` et `hasContact` arrivent tantôt en objet, tantôt en tableau d'un objet. Lus en dur, ils
  levaient, et l'exception emportait le point d'intérêt entier.

**Le label Accueil Vélo n'existe pas dans cette API.** Le thésaurus `Label` est vide, et ni `Theme` (758
valeurs) ni `Amenity` (312) n'en portent l'équivalent. Le rattachement à une véloroute ne marche pas non
plus : les 212 thèmes `BikeRoute*` ne sont portés que par les tronçons d'itinéraire eux-mêmes - zéro
hébergement en France entière. Le seul signal vélo que portent les services est le thème générique `Bike`,
et c'est lui que l'application propose, sous ce nom-là plutôt que sous une marque qu'elle ne peut pas
garantir.

**Un lieu porte huit classes, et l'hébergement colle à tout.** Des toilettes publiques y sont
`PublicLavatories`, mais aussi `CampingAndCaravanning`, `CamperVanArea`, `Accommodation` et
`LodgingBusiness` - et comme la catégorie retenue était la première de l'énumération qui reconnaissait une
classe, elles s'affichaient en "Campings et aires de camping-car". **57 des 250 toilettes** relevées en
France entière, soit près d'une sur quatre. Le groupe *pratique* passe donc devant les autres au moment de
trancher (`PoiCategory.ORDRE_DE_RESOLUTION`), et **seulement lui** : sur 250 lieux de chaque sorte, aucun
musée, camping ou restaurant ne porte de classe pratique, si bien que ce groupe ne peut voler personne. Une
priorité plus large ferait des dégâts - **117 hôtels sur 250** portent une classe de restauration, et
passer la restauration devant l'hébergement afficherait la moitié des hôtels en restaurants.

**Cette priorité ne dépendait que des cases cochées, et c'était une promesse fausse.** La recherche se
bornait aux catégories affichées, pour qu'un lieu qui en porte plusieurs paraisse sous celle qu'on avait
demandée. En ne cochant que "Restaurants", le centre d'Albi rendait donc six marqueurs, et les six étaient
des hôtels : Mercure, Ibis Styles, Grand Hôtel d'Orléans, Ibis, Hôtel du Parc, Hôtel Alchimy. Tous portent
`Restaurant` en plus de `Hotel`, et n'ayant plus que cette issue, ils s'affichaient en restaurants. Sur cinq
villes et 520 lieux rendus par une requête de restauration, **7 % portent une classe d'hébergement - 80 % à
Albi**. Le même défaut valait pour les toilettes : les décocher en gardant les campings les ramenait en
campings, la priorité du groupe pratique ne s'appliquant elle non plus qu'aux cases cochées.

Ce qu'un lieu **est** ne dépend donc plus de ce qu'on a demandé : la résolution parcourt toutes les
catégories, et le filtre écarte ensuite. Le prix est assumé - un lieu dont la catégorie intrinsèque est
masquée disparaît, même s'il porte la classe d'une catégorie affichée. C'est le sens qu'on veut donner au
filtre : masquer les restaurants masque les restaurants, et rien d'autre ne vient prendre leur place.

**Ses 384 classes ne sont pas des catégories.** Un même lieu en porte plusieurs - un hôtel-restaurant est
`Hotel`, `Restaurant`, `Accommodation` et `LodgingBusiness` - et personne ne veut cocher 384 cases. D'où la
table qui fait le pont entre les 27 catégories de France Vélo Tourisme et les classes qui les remplissent.
Elle sert deux fois, et c'est pourquoi elle vit dans le domaine : elle **compose la requête** et **relit la
réponse**.

### OpenStreetMap complète DATAtourisme, il ne le remplace pas

DATAtourisme est la base publique **française** : hors de France, la couche de points d'intérêt était vide,
sans que rien ne l'explique. C'était la première raison d'une seconde source. La seconde s'est révélée en
mesurant, sur un écran de carte autour de Grenoble :

| Catégories | DATAtourisme | OpenStreetMap |
|---|---|---|
| hôtels | 49 | 38 |
| points d'eau, toilettes, aires de pique-nique | 0 | 200 |
| bornes de recharge | 0 | 165 |
| loueurs et réparateurs de vélos | 4 | 50 |

La base touristique décrit bien le tourisme - et l'illustre de photos, que l'infobulle montre - mais ignore
largement ce qui sert sur le terrain. D'où le partage retenu (`poi/PoiSources`) : hors de France, OSM répond
seul ; en France, il complète le groupe *pratique* et la *restauration*. Ce n'est pas un repli, c'est une
division du travail.

**La restauration y a été ajoutée après coup, et l'erreur mérite d'être notée.** Le tableau ci-dessus
mesurait les *hôtels*, et l'on en a conclu que les restaurants étaient comparables eux aussi. Ils ne le sont
pas, et personne ne l'avait mesuré. Sur le centre d'Albi, même emprise pour les deux sources :

| Catégories | DATAtourisme | OpenStreetMap |
|---|---|---|
| restaurants | **6** | **150** |
| bars, cafés, pubs | 1 | 23 |

Et les six de DATAtourisme sont **tous des hôtels**. Un restaurant de quartier n'est pas un objet
touristique : il n'entre dans cette base que s'il est adossé à un hébergement. La couverture varie d'ailleurs
énormément d'une région à l'autre, ce qui interdit de s'y fier - pour la même question, la base rend 10 lieux
de restauration à Albi, 46 à Grenoble, 29 à Nantes, 185 à Strasbourg et 729 à Marseille. Ce ne sont pas des
villes de tailles si différentes ; ce sont des comités régionaux qui ne publient pas les mêmes choses.

L'hébergement et les loisirs, eux, restent à la base touristique, et le **contenu** le justifie autant que le
nombre : sur le centre d'Albi elle rend les quatorze hôtels de la ville, nommés, et cinquante-deux lieux de
loisirs dont les circuits de découverte, les bouclettes de randonnée urbaine, le petit train touristique et
les marchés de producteurs - autant d'objets qui n'existent tout simplement pas dans OpenStreetMap.

**Le complément se coupe** (réglage *Compléter avec OpenStreetMap*), parce qu'une requête Overpass est longue
et que tout le monde n'en veut pas. Il ne gouverne que le complément : hors de France il est ignoré, OSM y
étant la seule source - un réglage qui parle du complément ne doit pas pouvoir supprimer le principal.

**Ce qu'OSM ne peut pas dire.** Un groupe limité au thème vélo reste à DATAtourisme où qu'on soit :
OpenStreetMap ne porte pas l'équivalent de ce thème, et rendre des hébergements quelconques sous un filtre
"vélo" serait promettre ce qu'on ne sait pas. Une catégorie vide dit la vérité ; un marqueur qui ment ne se
rattrape pas. Pour la même raison, sept des vingt-sept catégories n'ont aucune étiquette OSM - "hébergements
insolites", "villages de caractère" sont des jugements touristiques, qu'OSM ne porte nulle part.

**Les sources sont interrogées en parallèle**, et chacune s'affiche dès qu'elle répond (`poiStream`). Ce
n'est pas un raffinement : mesurée sur l'instance publique, une requête de toutes les catégories sur une
ville dense met **une trentaine de secondes**, quand DATAtourisme répond en une.

OpenStreetMap est en outre découpé **par groupe** (`PoiSources.osmGroups`), une requête chacun. Les mêmes
mesures, à Berlin : hébergements 3,1 s, restauration 2,9 s, loisirs 15,9 s, pratique 23,3 s. Ce ne sont pas
les trente secondes qui gênaient, c'est de n'avoir rien pendant trente secondes - l'écran se peuple
désormais au bout de trois. **Une seule requête à la fois**, et c'est une correction : l'instance publique
n'accorde que deux créneaux par adresse, on les prenait tous les deux d'un coup, et il ne restait rien pour
le geste suivant.

**Ce que coûte un chargement qu'on n'a pas laissé finir.** Les sources publiant chacune à son arrivée, la
première suffisait à marquer l'emprise chargée - or DATAtourisme répond en une seconde et Overpass en met
trois à trente. Un geste de plus annulait le chargement entre les deux, et la vue suivante, contenue dans
celle-ci, ne redemandait plus rien : la carte restait sur les seuls lieux de la source rapide,
définitivement. Un dézoom suivi d'un zoom sur Albi faisait ainsi disparaître les cent cinquante restaurants
sans retour. Une émission de **clôture** dit désormais que tout est arrivé, et elle seule autorise à retenir
l'emprise.

La correction se retourne contre elle-même sans un frein, et c'est le relevé de terrain qui l'a montré :
**huit gestes de carte, vingt-cinq requêtes Overpass, vingt-cinq échecs de connexion**. L'emprise en échec
n'étant plus retenue, chaque geste relançait la requête que le service venait de refuser, et il refusait
d'autant plus fort. Une zone en échec attend donc une minute - l'échec seul : une réponse simplement tronquée
se redemande toujours au geste suivant, puisque le service a répondu. Une requête qui n'aboutit pas se
distingue enfin d'une zone déserte, ce qui n'était pas le cas : les deux rendaient une liste vide.

**Le plafond de la réponse ne se tait plus.** Chaque source rend au plus 250 lieux par requête - le maximum
que DATAtourisme accorde. Il était de 100, choisi pour la lisibilité, et c'était une perte silencieuse :
autour de Souillac, le service connaît 149 lieux, l'application n'en demandait que 100, et les 49 autres
étaient écartés dans un ordre que rien ne fixe. D'un déplacement de carte au suivant, ce n'étaient pas les
mêmes - un loueur de canoës apparaissait, puis disparaissait. Relevé sur le terrain, et c'est le pire genre
de faute : la carte avait l'air juste. Au-delà de 250, la carte annonce désormais qu'elle ne montre pas tout,
plutôt que de laisser croire à un catalogue complet - **et elle ne retient pas cette emprise comme chargée**.
Sans quoi la troncature se figeait : la vue suivante étant contenue dans la précédente, la règle du
"rien à redemander tant qu'on reste dedans" répondait non, et zoomer sur un coin d'une zone à trois mille
lieux n'en ramenait jamais un de plus. Le prix est une requête à chaque geste tant qu'on reste dans une zone
trop dense ; c'est le prix juste, puisqu'on sait ne pas tout montrer.

Deux prix à connaître. L'instance publique d'Overpass **repart en 504 une fois sur deux** aux heures
chargées, et ce refus arrive vite - 8 à 13 secondes, sans rapport avec le poids de la requête -, d'où la
seconde tentative. Les délais annoncés au serveur, eux, ont dû être relevés à 50 secondes : à 25, il
avortait sa propre requête (`"Query timed out after 26 seconds"`) au moment précis où elle allait aboutir,
et la couche restait vide hors de France, là où l'on demande toutes les catégories à la fois. Un échec ne se
distingue toujours pas d'une zone vide : la couche se rabat sur son cache comme pour l'autre source. Et un lieu d'OSM n'a **pas toujours de nom** - une fontaine, des toilettes -
là où DATAtourisme en exige un : l'infobulle prend alors le nom de la catégorie, plutôt que de s'ouvrir sur
un titre vide.

### Le suivi de position vit dans un service, pas dans la carte

Le capteur était écouté depuis l'écran de carte, et ses positions vivaient dans la composition. L'écran
éteint, Android arrête la recomposition : le suivi s'arrêtait avec elle, au moment précis où il sert -
téléphone en poche, sur un guidon, dans un sac. La position, l'alerte d'éloignement et le "restant" du
profil étaient donc des fonctions de terrain qui ne fonctionnaient que sous les yeux de leur utilisateur.

Le flux vit désormais dans un **service de premier plan** (`location/LocationService`), et l'écran n'en est
qu'un lecteur parmi d'autres. C'est le seul moyen qu'Android offre de tenir le capteur ouvert quand l'écran
s'endort, et sa notification permanente est le prix à payer : elle dit ce qui tourne, et l'arrêt y est à un
tap. Un **verrou processeur** l'accompagne, le premier plan garantissant qu'on ne sera pas tué, non que
l'appareil endormi traitera les positions autrement que par salves.

**Sans `ACCESS_BACKGROUND_LOCATION`** : un service de premier plan de type `location`, démarré pendant que
l'application est visible, lit le capteur sans elle. Demander l'autorisation d'arrière-plan serait réclamer
beaucoup plus que ce dont on se sert - le suivi ne démarre jamais que d'un geste.

**L'alerte d'éloignement a suivi le même chemin** (`location/TrackWatch`). Ce n'était pas une conséquence
technique mais la raison même du changement : une alerte qui ne se déclenche que sous les yeux de celui
qu'elle doit prévenir n'alerte personne. La mesure de l'écart et le son vivent donc dans le service ; l'écran
n'en lit que le résultat, et ne garde que ce qui est vraiment une affaire d'écran - le choix de la trace,
et la croix qui tait la bannière.

Conséquence à connaître : c'est aussi ce sur quoi reposera un enregistreur de trace, si l'application en
gagne un un jour. Écrire un point toutes les deux secondes demande exactement la même chose.

### Mises à jour : manifeste en asset de Release

La CI joint `latest-release.json` à la Release ; l'app le lit à l'URL stable
`/releases/latest/download/`.

Pourquoi pas l'API GitHub : 60 requêtes par heure et par IP en anonyme, ce qui compte derrière le
partage d'adresse des opérateurs mobiles. Pourquoi pas un commit sur `main` : la CI y déposerait des
commits, imposant un `git pull` avant chaque push suivant une release.

## Pièges connus

**Les bibliothèques natives de MapLibre ne se chargent pas sur la JVM.** Tout test unitaire qui
instancie la vraie `Application` échoue en `UnsatisfiedLinkError`
(cf. [`TESTS.md`](TESTS.md#pièges-de-linfrastructure)).

**Le build debug est une autre application.** `applicationIdSuffix = ".debug"` et une autre signature :
il ne peut pas être remplacé par un APK de release, d'où l'inertie de la vérification des mises à jour
en debug.

**`IconButton` impose une taille minimale de 48dp** via `minimumInteractiveComponentSize`, qui écrase
un `size()` plus petit. L'application la neutralise à plusieurs endroits
(`LocalMinimumInteractiveComponentSize provides Dp.Unspecified`). Sans cela, un fond de bouton dessiné
à 32dp sort à 48dp.

**Android bloque le trafic en clair.** Un fond de carte en `http` reste gris, sans message. Le
catalogue est vérifié par test sur ce point.

**Le KML tolère les fichiers tronqués.** Un KML coupé mais syntaxiquement correct jusqu'à la coupure
ne lève pas : la couche sort vide, et l'utilisateur lit "le fichier est vide" au lieu de "invalide".
Verrouillé par test, non corrigé.

**Les couches mélangent points et traces.** Un même fichier importé peut contenir les deux
(`hasLine` et `hasPoints`). Le modèle de la spécification v1, qui supposait un itinéraire par entrée,
ne tient plus : `Route` est devenu `LayerEntity`.
