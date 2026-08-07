# Contexte - Trailog

Ce document explique **pourquoi** l'application est faite ainsi. Les autres documents disent autre
chose : [`README.md`](README.md) ce qu'elle fait pour l'utilisateur, [`DEVELOPER.md`](DEVELOPER.md)
comment la compiler et s'y reperer, [`SPEC.md`](SPEC.md) les intentions d'origine,
[`WORKFLOW.md`](WORKFLOW.md) comment elle est distribuee, [`TESTS.md`](TESTS.md) comment elle est
verifiee.

Il s'adresse a qui reprend le code et se demande pourquoi tel choix a ete fait plutot qu'un autre.

- [D'ou vient le projet](#dou-vient-le-projet)
- [Ce que l'application est, et n'est pas](#ce-que-lapplication-est-et-nest-pas)
- [Les contraintes qui expliquent tout le reste](#les-contraintes-qui-expliquent-tout-le-reste)
- [Decisions structurantes](#decisions-structurantes)
- [Pieges connus](#pieges-connus)

## D'ou vient le projet

Trailog est le portage Android natif d'une application web personnelle (`tefeciste/2024`, dont
`js/map.js` a fourni le registre de fonds de carte initial, et dont le profil altimetrique
reimplemente la logique de `ol-elevation-profile`). Le depot demarre le 11 juillet 2026.

C'est une application **personnelle**, ecrite pour un usage de randonnee et de velo, publiee sous
**GPL v3**. Elle n'a ni utilisateurs a satisfaire, ni feuille de route commerciale, ni store a
menager. Cela explique plusieurs choix qui seraient discutables ailleurs.

## Ce que l'application est, et n'est pas

**Est** : un consultateur de traces. On importe des fichiers GPX, GeoJSON, KML et KMZ, on les range en
dossiers, on les regarde sur une carte avec un profil altimetrique synchronise, et on emporte le tout
hors ligne.

**N'est pas** : un enregistreur GPS, un editeur de traces, un service en ligne. Il n'y a ni compte, ni
synchronisation, ni telemetrie. Tout vit sur l'appareil.

Le dessin a la main et le suivi GPS temps reel etaient explicitement hors perimetre v1
(cf. [`SPEC.md`](SPEC.md) section 1) et le restent.

## Les contraintes qui expliquent tout le reste

**Hors ligne d'abord.** L'usage cible est le terrain, sans reseau. Tout ce qui peut etre precalcule
l'est, tout ce qui peut etre stocke localement l'est. Une fonctionnalite qui exige le reseau doit
degrader proprement, pas echouer - et si elle ne peut pas s'en passer du tout, comme la recherche
d'adresse, elle reste optionnelle et eteinte par defaut.

**Aucun store.** La distribution passe par GitHub Releases. Il n'y a donc personne pour prevenir
l'utilisateur qu'une version existe : l'app doit s'en charger elle-meme. Cela justifie la permission
`REQUEST_INSTALL_PACKAGES`, qui vaudrait un refus sur le Play Store.

**minSdk 24** (Android 7.0). Plusieurs API confortables ne sont pas disponibles : l'autorisation
d'installer par application n'existe que depuis Android 8, `Modifier.blur` depuis Android 12. Chaque
usage doit prevoir le chemin ancien.

**Un seul developpeur.** Pas de revue de code, pas de garde-fou humain. D'ou l'insistance sur les
tests des zones ou une faute ne se voit pas : migrations de base, contrat entre la CI et l'app,
integrite du catalogue de fonds.

## Decisions structurantes

### Room pour le catalogue, fichiers pour la geometrie

Les dossiers, couches, fonds et reglages vivent dans Room. La **geometrie** des traces vit dans des
fichiers GeoJSON separes, references par `LayerEntity.geometryFile`.

Pourquoi : une trace fait couramment plusieurs milliers de points. Les stocker en base rendrait chaque
requete lourde, alors que la geometrie n'est lue qu'au moment de l'afficher. MapLibre sait par ailleurs
lire un fichier GeoJSON par URI `file://` sur son propre thread, sans passer par nous.

### Trois fichiers par couche

Pour une couche importee, on ecrit :

| Fichier | Role |
|---|---|
| `<nom>` | GeoJSON source, geometrie complete. Reference pour le profil, l'edition et l'export |
| `<nom>.map` | GeoJSON pret pour la carte : lignes simplifiees, points annotes pour le tap |
| `<nom>.prof` | profil precalcule (echantillons decimes et statistiques), par segment |

Pourquoi : recalculer le profil ou simplifier la geometrie a chaque affichage saturait le processeur
sur les grosses traces. Le cout est paye une fois, a l'import.

La simplification (Douglas-Peucker, tolerance 1 m) ne touche **que** le fichier de rendu. Les
statistiques et le profil restent calcules sur la geometrie brute : simplifier avant de mesurer
fausserait les distances et le denivele.

### Migrations explicites, jamais destructives

Chaque evolution du schema passe par une `Migration` ecrite a la main
(cf. [`TESTS.md`](TESTS.md#datadb---base-de-donnees)). `fallbackToDestructiveMigration` reste declare
en dernier recours, mais aucune migration ne doit l'atteindre.

Pourquoi : une recreation de base effacerait les couches importees de l'utilisateur, en silence, au
premier lancement d'une nouvelle version. C'est la faute la plus couteuse possible ici, et la moins
visible : le build passe, les tests passent, et les donnees disparaissent chez l'utilisateur.

### Le catalogue de fonds n'est seme qu'a vide

`TrailogRepository.ensureSeed` n'insere les fonds par defaut que si la table est vide.

Pourquoi : l'utilisateur peut supprimer un fond, en modifier l'URL ou la cle. Un seed rejoue a chaque
lancement les ressusciterait ou ecraserait ses modifications.

**Consequence a connaitre** : ajouter un fond a `Providers.defaults()` ne suffit pas. Il n'apparaitra
que sur une installation neuve. Pour une base deja en place, il faut l'inserer par une migration
(cf. la 18-19, qui ajoute le fond AF3V).

### Version derivee du tag git

`versionCode` et `versionName` sont calcules par `build.gradle.kts` a partir de `git describe`, jamais
codes en dur.

Pourquoi : une version codee en dur finit oubliee, et toutes les releases se declarent alors avec le
meme `versionCode`. Android refuse alors la mise a jour, sans expliquer pourquoi.

**Consequence** : un build hors tag porte un suffixe descriptif (`0.2.0-23-gabc1234`). C'est pour cela
que la comparaison des versions porte sur `versionCode`, un entier, et jamais sur `versionName` : un
decoupage numerique de ce suffixe lirait `0.2.0-23` comme plus recent que `0.2.0`.

### Pas de NavHost

`AppRoot` superpose les reglages a la carte dans une `Box`, au lieu de naviguer.

Pourquoi : detruire et recreer la `MapView` reinitialise le zoom et provoque un scintillement. La
carte doit survivre a l'ouverture des reglages.

### La langue est a part

Tous les reglages vivent dans la table `settings`, sauf la langue, qui est dans des
`SharedPreferences` (`LocalePrefs`).

Pourquoi : elle est lue dans `attachBaseContext`, avant que la base ne soit ouverte.

### Le relief n'est pas un fond

Un provider de type `DEM` fournit des tuiles d'altitude brutes, illisibles telles quelles. Il n'est
jamais selectionnable comme fond visuel : son `enabled` sert de bascule d'affichage de l'ombrage.

Consequence dans le code : il reste toujours visible dans le gestionnaire, meme desactive. Le filtrer
comme les autres fonds le ferait disparaitre au premier tap qui l'eteint, sans moyen de le rallumer.

### Le geocodage est optionnel, et son service interchangeable

Chercher une adresse exige un service en ligne : rien n'est geocodable hors ligne a cette echelle. La
fonction est donc **desactivee par defaut** et s'active dans *Reglages / Carte*.

Le service retenu est **Photon**, et non Google. Google impose une cle facturee, et ses conditions
reservent l'affichage des resultats a une carte Google, alors que l'application rend les siennes avec
MapLibre. Nominatim, l'autre geocodeur OSM connu, **interdit** l'autocompletion au clavier dans sa
politique d'usage. Photon est le seul a la fois prevu pour la frappe et **auto-hebergeable**.

C'est pour cela que son URL est un reglage et non une constante : le jour ou une instance personnelle
tourne, il suffit de la designer. Le defaut vide, et non l'URL publique ecrite en base, evite qu'un
changement de defaut dans le code laisse les installations existantes sur l'ancienne adresse.

Le moteur d'itineraire suit la meme regle. **Valhalla** y est retenu pour une raison qui lui est propre :
ses cinq disciplines (route, gravel, VTC, VTT, marche) sortent d'une **seule** instance, via le
`bicycle_type` de son modele de cout. OSRM demanderait un serveur par profil, soit cinq a heberger, et
GraphHopper n'offre pas d'instance publique sans cle. BRouter, seul a savoir router hors ligne, resterait
le choix du jour ou l'on voudrait s'affranchir du reseau : il faudrait alors gerer ses tuiles `.rd5`.

Consequence a connaitre : une mesure de distance est une **requete reseau**, la ou le vol d'oiseau ne
coutait rien. L'origine de la mesure depuis la position est donc figee a la premiere position recue et ne
suit pas le capteur : la suivre lancerait une requete toutes les deux secondes.

### L'adresse d'un point n'attend pas le reglage du geocodage

L'appui long sur la carte cherche l'adresse du point touche (geocodage **inverse**) meme si la recherche
par le nom est eteinte, et vise alors l'instance par defaut.

Pourquoi cette exception : le reglage sert a ne pas poser sur la carte un bouton de recherche dont la
frappe n'aboutirait a rien. Ici il n'y a pas de bouton, et rien ne part tant que le doigt ne s'attarde pas
sur un endroit precis - le geste EST la demande. Le cacher derriere un interrupteur reviendrait a rendre
muet un appui long, sans que rien a l'ecran ne dise pourquoi.

Le prix a connaitre : c'est le seul endroit ou l'application interroge un service tiers sans que
l'utilisateur l'ait autorise dans ses reglages. Il reste maitre de l'instance visee (la meme URL, sa
terminaison changee) et, a defaut, du geste : on ne s'attarde pas par accident.

### Mises a jour : manifeste en asset de Release

La CI joint `latest-release.json` a la Release ; l'app le lit a l'URL stable
`/releases/latest/download/`.

Pourquoi pas l'API GitHub : 60 requetes par heure et par IP en anonyme, ce qui compte derriere le
partage d'adresse des operateurs mobiles. Pourquoi pas un commit sur `main` : la CI y deposerait des
commits, imposant un `git pull` avant chaque push suivant une release.

## Pieges connus

**Les bibliotheques natives de MapLibre ne se chargent pas sur la JVM.** Tout test unitaire qui
instancie la vraie `Application` echoue en `UnsatisfiedLinkError`
(cf. [`TESTS.md`](TESTS.md#pieges-de-linfrastructure)).

**Le build debug est une autre application.** `applicationIdSuffix = ".debug"` et une autre signature :
il ne peut pas etre remplace par un APK de release, d'ou l'inertie de la verification des mises a jour
en debug.

**`IconButton` impose une taille minimale de 48dp** via `minimumInteractiveComponentSize`, qui ecrase
un `size()` plus petit. L'application la neutralise a plusieurs endroits
(`LocalMinimumInteractiveComponentSize provides Dp.Unspecified`). Sans cela, un fond de bouton dessine
a 32dp sort a 48dp.

**Android bloque le trafic en clair.** Un fond de carte en `http` reste gris, sans message. Le
catalogue est verifie par test sur ce point.

**Le KML tolere les fichiers tronques.** Un KML coupe mais syntaxiquement correct jusqu'a la coupure
ne leve pas : la couche sort vide, et l'utilisateur lit "le fichier est vide" au lieu de "invalide".
Verrouille par test, non corrige.

**Les couches melangent points et traces.** Un meme fichier importe peut contenir les deux
(`hasLine` et `hasPoints`). Le modele de la specification v1, qui supposait un itineraire par entree,
ne tient plus : `Route` est devenu `LayerEntity`.
