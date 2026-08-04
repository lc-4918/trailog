# Etat des tests - Trailog

Document de travail personnel, **non versionne** (hors depot). Il recense ce qui est testé
aujourd'hui et ce qui reste à implementer, pour le jour où l'on reprendra les tests
d'instrumentation et e2e. La documentation de test versionnee, elle, est `TESTS.md` (qui ne
reference pas ce fichier, volontairement).

## Tests realises

### Tests unitaires (JVM + Robolectric)

- **159 tests unitaires**, infrastructure Robolectric + couverture Jacoco reelle.
- Couverture reelle : **58,8 % du code hors UI**, 18,6 % du total.
- Le module `update/` est couvert : `UpdateCheckTest` (verification inerte en debug, trois issues
  distinctes) et `ReleaseInfoTest` (manifeste CI relu tel quel, ordre des `versionCode`, champ
  inconnu ignore, changelog multi-lignes).

**Plafond de couverture.** 5293 des 9100 lignes vivent dans des fichiers `@Composable`, hors
d'atteinte d'un test unitaire JVM. L'indicateur à suivre est donc le code **hors UI** (58,8 %), pas
le total.

### Tests d'instrumentation (androidTest)

- **Un seul** a ce jour : `MigrationInstrumentedTest` (2 `@Test`, ouverture de la base). Il prouve
  surtout que l'infrastructure `androidTest` compile et tourne sur appareil/emulateur.
- Les dossiers `androidTest/.../e2e/` et `androidTest/.../ui/` existent mais sont vides.

## Tests a implementer

**36 tests d'instrumentation et 12 e2e** listes ci-dessous, un par reglage des 4 onglets de
`SettingsScreen.kt` (28 rubriques recensees). Rien n'est implemente au-dela du
`MigrationInstrumentedTest`.

Question ouverte : tout implementer ou prioriser ? Certains (import .mbtiles, dossiers, langue,
avatar) dependent du selecteur de fichiers systeme, donc d'UiAutomator et d'un appareil reel.

Origine des listes : les 4 premieres rubriques de l'onglet Carte viennent de
`TESTS/tests_instru_e2e.txt` (redige par l'utilisateur). Le reste est deduit des captures de
`TESTS/screenshots/` et de l'inventaire des controles du code.

### Instrumentation, onglet Carte (10)

| #    | Reglage                      | Ce que verifie le test                                                                       |
|------|------------------------------|----------------------------------------------------------------------------------------------|
| C-1  | Position GPS                 | le toggle ecrit `showGpsButton` ; les deux boutons apparaissent et disparaissent de la carte |
| C-2  | Rotation                     | le toggle ecrit `rotateGesturesEnabled` ; la carte accepte ou refuse le geste a deux doigts  |
| C-3  | Gestionnaire : bouton        | le toggle ecrit `showBasemapControlButton`                                                   |
| C-4  | Gestionnaire : largeur       | slider 20-90 %, defaut 50 ; le panneau occupe la fraction demandee                           |
| C-5  | Gestionnaire : opacite       | slider 30-100 %, defaut 80 ; alpha applique tel quel, sans le `1 - x` d'avant                |
| C-6  | Gestionnaire : reinitialiser | remet 50 % et 80 %, sans toucher aux autres reglages                                         |
| C-7  | Echelle                      | le toggle ecrit `showScale`                                                                  |
| C-8  | Taille des marqueurs         | stepper 16-80, defaut 36 ; borne aux deux extremites                                         |
| C-9  | Infobulles : polices         | `bubbleFont` et `bubbleTitleFont`, chacun avec sa bascule gras                               |
| C-10 | Position de l'infobulle      | les 10 valeurs ecrites, relues par `BubblePosition.of`                                       |

### Instrumentation, onglet Tuiles (7)

| #   | Reglage                      | Ce que verifie le test                                                      |
|-----|------------------------------|-----------------------------------------------------------------------------|
| T-1 | Fond par defaut              | la liste propose fonds et composites ; le choix ecrit `defaultBasemapId`    |
| T-2 | Importer un .mbtiles         | le fichier est copie dans le dossier MBTiles, un fournisseur local est cree |
| T-3 | Creer un composite           | popup : nom, opacite du premier plan, les deux couches                      |
| T-4 | Composite : edition          | le crayon rouvre la popup pre-remplie ; l'enregistrement met a jour         |
| T-5 | Composite : suppression      | la corbeille retire le composite ; le fond actif retombe sur un fond valide |
| T-6 | Fournisseurs : liste         | popup : chaque fournisseur a son toggle et son crayon                       |
| T-7 | Fournisseurs : import/export | un export puis import restitue URL et cles a l'identique                    |

### Instrumentation, onglet Profil (8)

| #   | Reglage              | Ce que verifie le test                                                       |
|-----|----------------------|------------------------------------------------------------------------------|
| P-1 | Grille               | le toggle ecrit `profileGrid` ; le trace la dessine ou non                   |
| P-2 | Colorer par pente    | le toggle ecrit `profileSlope`                                               |
| P-3 | Legende des pentes   | le toggle ecrit `profileSlopeLegend` ; la legende apparait sous le profil    |
| P-4 | Lissage              | slider 1-100 m, defaut 5 ; n'affecte que le profil affiche, jamais les stats |
| P-5 | Echelle verticale    | Auto puis valeur absolue ; le libelle bascule entre "Auto" et "1 cm = N m"   |
| P-6 | Infos ligne de titre | 6 chips ; l'ordre d'affichage suit celui des chips, pas celui des taps       |
| P-7 | Infos point courant  | 4 chips ; "Temps" masque sur une trace sans horodatage                       |
| P-8 | Tailles de police    | les 5 steppers et leurs 5 bascules gras, independants                        |

### Instrumentation, onglet Systeme (11)

| #    | Reglage                         | Ce que verifie le test                                            |
|------|---------------------------------|-------------------------------------------------------------------|
| S-1  | Dossier des imports             | le selecteur ecrit `importDir` ; "Defaut du systeme" si vide      |
| S-2  | Dossier des MBTiles             | le selecteur ecrit `mbtilesDir` ; "Dossier de l'app" si vide      |
| S-3  | Menu lateral                    | les 3 modes ; en `swipe`, le burger disparait de la carte         |
| S-4  | Barre de statut                 | le toggle ; la carte passe ou non sous la barre                   |
| S-5  | Tolerance de tap                | slider 4-40 dp, defaut 16                                         |
| S-6  | Simplifier les traces           | le toggle ; n'affecte que les imports suivants                    |
| S-7  | Unites                          | metrique et imperial ; km ou mi, m ou ft dans le profil           |
| S-8  | Mises a jour                    | Auto/Manuel ; le bouton n'apparait qu'en Manuel ; inerte en debug |
| S-9  | Langue                          | les 8 langues ; l'activite se recree, l'interface change          |
| S-10 | Theme                           | Systeme, Clair, Sombre                                            |
| S-11 | Titre, avatar, reinitialisation | titre du menu, avatar par fichier ou URL, popup de reset          |

### e2e (12)

Les 10 premiers viennent de `TESTS/tests_instru_e2e.txt`. E-11 et E-12 sont deduits des captures.

| #    | Parcours                      | Scenario                                                                       |
|------|-------------------------------|--------------------------------------------------------------------------------|
| E-1  | Fichiers invalides            | GPX, KML, KMZ et GeoJSON mal formes : popup d'erreur a l'import                |
| E-2  | Lot mixte                     | un lot melant valides, invalides et vides : les valides s'importent quand meme |
| E-3  | GPX 3D avec temps             | trace sur la carte, icone au menu, profil avec denivele et temps               |
| E-4  | GPX 2D sans altitude          | `test_locus.gpx` : banniere "Parcours sans altimetrie" au lieu du trace        |
| E-5  | Boutons GPS                   | affichage des deux boutons, fond bleu, recentrage sur la position              |
| E-6  | Rotation                      | rotation a deux doigts, apparition de la fleche, reorientation au nord         |
| E-7  | Affichage du gestionnaire     | bouton present, masque par reglage, retabli par la reinitialisation            |
| E-8  | Arborescence des fonds        | dossier cree, drag and drop, survie au redemarrage de l'app                    |
| E-9  | Largeur et opacite du panneau | 90 % de large, opacite appliquee                                               |
| E-10 | Echelle graphique             | unite en m au zoom 19, en km au zoom 4, puis masquage                          |
| E-11 | Composite par defaut          | un composite cree puis choisi comme fond par defaut survit au redemarrage      |
| E-12 | Langue                        | un changement de langue se propage a toute l'interface et persiste             |

**Reserves.** E-5 et E-6 dependent du materiel (capteur GPS, geste a deux doigts) et resteront
fragiles en integration continue. Depuis la correction d'opacite, C-5 et E-9 testent une **opacite**
(et non plus une transparence). Les captures de `TESTS/screenshots/` montrent le build d'avant cette
correction (elles affichent "Transparence du panneau : 20%", devenu "Opacite du panneau : 80%").

## Defauts connus reperes par les tests (non couverts, à trancher)

- **KML tronque annonce "vide" au lieu de "invalide".** Un KML coupe mais syntaxiquement correct
  jusqu'à la coupure ne leve pas d'exception (le parseur XML ne se plaint pas des balises non
  fermees) : `LayerImporter` rend une couche vide, d'ou le message "vide". Le comportement reel est
  verrouillé par le test `kml tronque rend une couche vide au lieu de lever` (`LayerImporterTest`).
  Corriger demanderait de verifier apres parsing que la racine s'est refermee.

- **Fixtures de test volumineuses.** `app/src/test/resources/fichiers/` contient de vrais exports
  (Wikiloc, OruxMaps, Locus, Google Earth), dont un KML de 1,4 Mo (2,1 Mo au total), deja dans
  l'historique. Ils attrapent les particularites de chaque producteur ; a arbitrer entre valeur et
  poids du depot.
