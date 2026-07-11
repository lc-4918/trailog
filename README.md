# Trailog

**Cartographie et itinéraires hors-ligne pour Android.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build](https://github.com/lc-4918/trailog/actions/workflows/build-release.yml/badge.svg)](https://github.com/lc-4918/trailog/actions/workflows/build-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/lc-4918/trailog)](https://github.com/lc-4918/trailog/releases)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com)

Trailog est une application Android native pour consulter, importer et organiser des
traces GPS (randonnée, vélo, VTT, exploration), sur des fonds de carte personnalisables,
avec un fonctionnement pensé pour le hors-ligne.

> *Captures d'écran à venir.*

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
- Exploration de fonds de carte spécialisés (IGN, relief, pistes cyclables…).

## Caractéristiques principales

- **Carte native** (MapLibre) avec de nombreux fonds de carte configurables (OpenStreetMap,
  IGN, relief/hillshade, fonds composites fond + overlays…).
- **Import de traces** au format **GPX**, **GeoJSON** et **KML/KMZ**, avec calcul automatique
  des statistiques (distance, dénivelé positif/négatif, pente, temps en mouvement).
- **Profil altimétrique** natif, synchronisé avec un curseur sur la carte.
- **Organisation en dossiers** : créer, renommer, déplacer, supprimer des dossiers et itinéraires.
- **Couches de points** (marqueurs) avec infobulles éditables (texte, image, lien).
- **Hors-ligne** : cache des tuiles déjà consultées, et import de fonds **MBTiles** locaux.
- **Multilingue** : interface disponible en français, anglais, allemand, espagnol,
  catalan, basque, italien et portugais.
- Réglages personnalisables : unités, tolérance de sélection tactile, thème d'avatar, etc.

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

## Guide de démarrage rapide

1. **Importer une trace** : bouton *Importer* → choisir un fichier GPX, GeoJSON ou KML/KMZ
   → l'app calcule automatiquement les statistiques et propose un aperçu.
2. **Choisir la destination** : dossier existant ou nouveau (dossier ou sous-dossier).
3. **Visualiser** : tap sur l'itinéraire dans le menu latéral → affichage sur la carte et du
   profil altimétrique. Un tap sur la carte ou sur le profil positionne le curseur au point
   correspondant sur l'autre vue.
4. **Ajouter des points d'intérêt** : importer une couche de points (GeoJSON/GPX/KML), tap
   sur un marqueur pour voir son infobulle.

## Utilisation avancée

- **Import/export** : GeoJSON, GPX et KML/KMZ en import ; export GeoJSON des traces.
- **Fonds de carte** : gérer la liste des fournisseurs de tuiles dans les réglages
  (URL, clé API, activation), créer des **fonds composites** (un fond opaque + un ou
  plusieurs overlays, ex. OpenStreetMap + tracés VTT).
- **Fonds hors-ligne locaux** : importer un fichier `.mbtiles` pour disposer d'un fond
  utilisable sans connexion.
- **Relief** : activer le hillshade (ombrage de relief) dans les réglages carte.
- **Personnalisation** : avatar, unités (métrique/impérial), mode d'ouverture du menu
  (bouton ou balayage), tolérance de sélection tactile sur la carte.

## Données & Confidentialité

- Aucun suivi en ligne, aucune télémétrie.
- Toutes les traces, points et réglages sont stockés **localement** sur l'appareil.
- Les seules requêtes réseau sont celles nécessaires au chargement des tuiles de carte
  auprès des fournisseurs que vous avez configurés.

## Contribution & Développement

Le développement se fait ouvertement sur GitHub :
- Guide technique complet (installation, architecture, build) : voir [`DEVELOPER.md`](DEVELOPER.md).
- Fonctionnement du CI/CD et des releases : voir [`WORKFLOW.md`](WORKFLOW.md).
- Signaler un bug ou proposer une fonctionnalité : [GitHub Issues](https://github.com/lc-4918/trailog/issues).

## Licence

Trailog est distribué sous licence **GPL v3**. Voir le fichier [`LICENSE`](LICENSE).

## Contact

Pour toute question, ouvrez une [discussion ou une issue](https://github.com/lc-4918/trailog/issues)
sur le dépôt GitHub.
