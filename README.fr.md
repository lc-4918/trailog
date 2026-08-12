# Trailog

<img src="app/src/main/assets/flags/fr.svg" alt="" width="20" align="top"> Français | [<img src="app/src/main/assets/flags/gb.svg" alt="" width="20" align="top"> English](README.md)

**Cartographie et itinéraires hors-ligne pour Android.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build](https://github.com/lc-4918/trailog/actions/workflows/build-release.yml/badge.svg)](https://github.com/lc-4918/trailog/actions/workflows/build-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/lc-4918/trailog)](https://github.com/lc-4918/trailog/releases)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)

Trailog est une application Android native pour consulter, importer et organiser des
traces GPS (randonnée, vélo, VTT, exploration), sur des fonds de carte personnalisables,
avec un fonctionnement pensé pour le hors-ligne.

<table width="100%">
  <tr>
    <td colspan="3" align="center"><img src="docs/screenshots/1.jpg" alt="Une trace sur la carte" width="330"><br><sub>Une trace sur la carte</sub></td>
  </tr>
  <tr>
    <td align="center" width="33.3%"><img src="docs/screenshots/2.jpg" alt="Profil altimétrique synchronisé" width="100%"><br><sub>Le profil, synchronisé</sub></td>
    <td align="center" width="33.3%"><img src="docs/screenshots/3.jpg" alt="Infobulle d'un marqueur avec photo" width="100%"><br><sub>Infobulle, photo comprise</sub></td>
    <td align="center" width="33.3%"><img src="docs/screenshots/4.jpg" alt="Bibliothèque de dossiers et de couches" width="100%"><br><sub>Bibliothèque des couches</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/5.jpg" alt="Planificateur d'itinéraire" width="100%"><br><sub>Calcul d'itinéraire</sub></td>
    <td align="center"><img src="docs/screenshots/6.jpg" alt="Gestionnaire de fonds de carte" width="100%"><br><sub>Gestionnaire de fonds</sub></td>
    <td align="center"><img src="docs/screenshots/7.jpg" alt="Réglages, onglet Carte" width="100%"><br><sub>Réglages, onglet Carte</sub></td>
  </tr>
</table>

---

## Table des matières

- [Qu'est-ce que Trailog ?](#quest-ce-que-trailog-)
- [Caractéristiques principales](#caractéristiques-principales)
- [Installation](#installation)
- [Guide de démarrage rapide](#guide-de-démarrage-rapide)
- [Utilisation avancée](#utilisation-avancée)
- [Données & Confidentialité](#données--confidentialité)
- [Contribution & Développement](#contribution--développement)
- [Licence](#licence)
- [Contact](#contact)

---

## Qu'est-ce que Trailog ?

Trailog permet de garder ses traces et points d'intérêt organisés localement sur son
téléphone, et de les visualiser sur une carte avec un profil altimétrique synchronisé,
sans dépendre d'un service en ligne.

**Cas d'usage typiques :**
- Randonnée, vélo, VTT : consulter un itinéraire préparé à l'avance, hors-ligne sur le terrain.
- Archivage de traces personnelles, classées en dossiers.
- Exploration de fonds de carte spécialisés (IGN, relief, pistes cyclables...).

## Caractéristiques principales

- **Carte native** (MapLibre) avec de nombreux fonds de carte configurables (OpenStreetMap,
  IGN, relief, pistes cyclables, fonds composites associant un fond et une surcouche).
- **Import de traces** au format **GPX**, **GeoJSON** et **KML/KMZ**, avec calcul automatique
  des statistiques (distance, dénivelé positif/négatif, pente, temps en mouvement).
- **Profil altimétrique** natif, synchronisé avec un curseur sur la carte, avec zoom sur une
  portion du parcours et échelle verticale réglable.
- **Organisation en dossiers** : créer, renommer, déplacer, supprimer des dossiers et itinéraires,
  et donner d'un coup la même couleur à toutes les traces d'un dossier.
- **Points d'intérêt** : marqueurs avec infobulles que vous pouvez modifier (titre, texte,
  liens, photos), y compris les photos des waypoints GPX de votre téléphone.
- **Cartes hors-ligne** : téléchargez une zone pour l'emporter sans réseau, importez vos
  propres fonds **MBTiles**, et les tuiles déjà consultées restent en cache.
- **Recherche d'un lieu ou d'une adresse** (à activer dans les réglages) : le lieu trouvé se pose
  sur la carte, avec son adresse.
- **Appui long sur la carte** : n'importe où hors d'une trace, une infobulle donne l'adresse de
  l'endroit touché, et mesure la distance et la durée pour l'atteindre — depuis votre position GPS,
  ou depuis un second point que vous désignez.
- **Mesure sur une trace** (à activer dans les réglages) : deux points posés du doigt sur une trace
  affichée, et Trailog donne la distance qui les sépare **le long du parcours**, sans réseau.
- **Alerte d'éloignement** (à activer dans les réglages) : choisissez la trace que vous suivez parmi
  les plus proches de vous, et Trailog vous prévient - bandeau en bas de l'écran, et son au choix -
  dès que vous vous en écartez de plus que la distance réglée.
- **Mises à jour intégrées** : l'app vous signale une nouvelle version et l'installe pour vous.
- **Multilingue** : interface disponible en français, anglais, allemand, espagnol,
  catalan, basque, italien et portugais.
- Réglages personnalisables : unités, tolérance de sélection tactile, avatar, position des
  infobulles, taille des textes.

## Installation

**Prérequis :** Android 7.0 (API 24) ou supérieur.

### Depuis GitHub Releases (recommandé)

1. Ouvrir la page [Releases](https://github.com/lc-4918/trailog/releases) du dépôt.
2. Télécharger le fichier `.apk` de la dernière version.
3. Ouvrir le fichier téléchargé sur votre téléphone (autoriser l'installation depuis une
   source inconnue si demandé par Android).
4. Confirmer l'installation.

> Trailog n'est pas distribué sur le Play Store : GitHub Releases sert de plateforme de
> distribution. Voir la section [Contribution & Développement](#contribution--développement)
> pour le détail du fonctionnement de ce "store".

### Mises à jour

Cette première installation faite, vous n'aurez plus à revenir ici : Trailog vérifie
lui-même s'il existe une version plus récente et vous propose de l'installer.

- Par défaut, la vérification a lieu **au démarrage** de l'application.
- Vous pouvez la passer en **manuel** dans **Réglages -> Système -> Mises à jour**, où un bouton
  permet alors de vérifier quand vous le souhaitez.
- Quand vous acceptez, Trailog télécharge la nouvelle version et lance l'installation. Android
  vous demandera une fois l'autorisation d'installer des applications depuis Trailog : c'est
  normal pour une application distribuée hors magasin, et vous pouvez la retirer à tout moment
  dans les réglages Android.
- Vos traces, dossiers et réglages sont conservés.

## Guide de démarrage rapide

1. **Importer une trace** : bouton *Importer* -> choisir un fichier GPX, GeoJSON ou KML/KMZ
   -> l'app calcule automatiquement les statistiques et propose un aperçu.
2. **Choisir la destination** : dossier existant ou nouveau (dossier ou sous-dossier).
3. **Visualiser** : tap sur l'itinéraire dans le menu latéral -> affichage sur la carte et du
   profil altimétrique. Un tap sur la carte ou sur le profil positionne le curseur au point
   correspondant sur l'autre vue.
4. **Ajouter des points d'intérêt** : importer une couche de points (GeoJSON/GPX/KML), tap
   sur un marqueur pour voir son infobulle. Le crayon permet de la modifier.

## Utilisation avancée

- **Import/export** : GeoJSON, GPX et KML/KMZ en import ; export GeoJSON des traces. Les
  photos référencées par les waypoints GPX (OruxMaps, OsmAnd, Locus, Garmin) sont récupérées
  et rangées dans l'application.
- **Modifier une infobulle** : le crayon ouvre un formulaire où vous pouvez changer le titre,
  corriger un champ, ajouter du texte, un lien ou une photo, choisir la photo mise en avant,
  ou supprimer le point.
- **Emporter une carte hors-ligne** : délimitez une zone sur la carte, choisissez la plage de
  zoom, et Trailog télécharge les tuiles dans une couche réutilisable sans réseau.
- **Fonds de carte** : gérer la liste des fournisseurs de tuiles dans les réglages
  (URL, clé API, activation), créer des **fonds composites** (un fond opaque plus une
  surcouche, par exemple OpenStreetMap avec les tracés VTT).
- **Fonds hors-ligne locaux** : importer un fichier `.mbtiles` pour disposer d'un fond
  utilisable sans connexion.
- **Légende d'un fond** : certains fonds, comme les voies cyclables AF3V, affichent un bouton
  d'information sur la carte qui déplie leur légende.
- **Chercher un lieu** : une fois le géocodage activé dans **Réglages / Carte**, un bouton de
  recherche apparaît sous le menu. Le lieu choisi se marque en noir sur la carte, et son infobulle
  en donne l'adresse. Les propositions sont classées par importance du lieu, une ville passant donc
  avant un hameau du même nom.
- **Interroger un point de la carte** : un appui long n'importe où, hors d'une trace et d'un
  marqueur, y pose une épingle et ouvre son infobulle. Elle cherche d'abord l'adresse de cet
  endroit, puis propose deux mesures : la distance depuis votre position GPS (si elle est active),
  et la distance depuis un second point, que vous désignez ensuite d'un tap sur la carte.
- **Distance et durée jusqu'au point** : ce ne sont pas des distances à vol d'oiseau mais celles de
  l'itinéraire recommandé, calculé pour la **discipline** réglée dans *Réglages / Trajets* :
  vélo de route, gravel, VTC, VTT ou à pied. Le petit "i" à côté de la valeur le rappelle.
  L'itinéraire lui-même se dessine sur la carte, teinté selon la pente. Les services
  interrogés sont **Photon** (adresses) et **Valhalla** (itinéraires), sans compte ni clé ; vous pouvez
  leur substituer vos propres instances en renseignant leurs URL dans les réglages.
- **Mesurer une portion de trace** : une fois *Afficher le bouton de mesure* activé dans
  **Réglages / Carte**, un bouton en forme de règle apparaît sous le menu. Une bande vous demande
  alors deux points : tapez le départ sur une trace affichée, puis l'arrivée sur la même trace, et
  la distance qui les sépare le long du parcours s'affiche entre les deux marqueurs. Inutile de
  viser la ligne au pixel près : chaque tap est ramené sur la trace la plus proche, et un tap
  au-delà d'un bout de trace se pose sur ce bout. Vous pouvez déplacer et zoomer la carte entre les
  deux points, l'infobulle reste visible et se cale au plus près du milieu de la portion mesurée.
  Sa croix efface la mesure.
- **Relief** : activer l'ombrage de relief dans les réglages carte.
- **Profil altimétrique** : zoomer sur une portion en choisissant un début et une fin
  (jusqu'à trois niveaux), régler le lissage et l'échelle verticale (par exemple 1 cm = 100 m,
  pour que la même pente occupe toujours la même hauteur).
- **Personnalisation** : avatar, unités (métrique/impérial), mode d'ouverture du menu
  (bouton ou balayage), tolérance de sélection tactile, position des infobulles.

## Données & Confidentialité

- Aucun suivi en ligne, aucune télémétrie, aucun compte.
- Toutes les traces, points et réglages sont stockés **localement** sur l'appareil.
- Les requêtes réseau se limitent au chargement des tuiles auprès des fournisseurs que vous
  avez configurés, et à la vérification des mises à jour auprès de GitHub. Cette dernière ne
  transmet rien sur vous : elle lit un fichier public indiquant la dernière version publiée.
  Vous pouvez la passer en manuel dans les réglages.
- La **recherche d'un lieu**, l'**adresse d'un point** et les **mesures de distance** sont les seules
  fonctions qui interrogent un service tiers pendant que vous vous en servez. Une recherche envoie le
  texte tapé, et rien d'autre — ni votre position, ni l'endroit que vous regardez. L'adresse d'un point
  envoie ce point, celui que vous venez de désigner du doigt. Une mesure de distance envoie les deux
  points concernés (dont votre position GPS si vous la demandez depuis celle-ci) au service
  d'itinéraire. La recherche par le nom est **désactivée par défaut**, et les deux services visés sont
  configurables : vous pouvez héberger les vôtres.
- La **mesure sur une trace**, elle, ne sort pas du téléphone : elle se lit sur la trace que vous avez
  importée, et n'interroge aucun service.

## Contribution & Développement

Le développement se fait ouvertement sur GitHub :
- Guide technique complet (installation, architecture, build) : voir [`DEVELOPER.md`](DEVELOPER.md).
- Ce que fait l'application, en détail : voir [`SPEC.md`](SPEC.md) ; les fonds de carte et leurs règles :
  voir [`BASEMAPS.md`](BASEMAPS.md).
- Fonctionnement du CI/CD et des releases : voir [`WORKFLOW.md`](WORKFLOW.md).
- Signaler un bug ou proposer une fonctionnalité : [GitHub Issues](https://github.com/lc-4918/trailog/issues).

## Licence

Trailog est distribué sous licence **GPL v3**. Voir le fichier [`LICENSE`](LICENSE).

## Contact

Pour toute question, ouvrez une [discussion ou une issue](https://github.com/lc-4918/trailog/issues)
sur le dépôt GitHub.
