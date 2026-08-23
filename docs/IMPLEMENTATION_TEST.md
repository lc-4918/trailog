# État des tests - Trailog

Document de travail personnel. Il recense ce qui est testé aujourd'hui et ce qui reste à implémenter,
pour le jour où l'on reprendra les tests d'instrumentation et e2e. La documentation de test versionnée,
elle, est `TESTS.md` (qui ne référence pas ce fichier, volontairement).

> **À trancher.** L'en-tête annonçait "non versionné (hors dépôt)". C'est faux : le fichier est suivi par
> git depuis `78b0e4b`. Soit on le retire du dépôt et l'on rétablit la phrase, soit on l'assume comme
> document de suivi versionné - ce que la phrase dit maintenant.

## Tests réalisés

### Tests unitaires et d'interface (JVM + Robolectric)

- **865 tests, 81 fichiers**, dont **44 tests d'interface** qui composent pour de vrai.
- Couverture réelle : **66,1 % du code hors UI**, 39,2 % du total.

**Le plafond de couverture a bougé.** Il était posé comme une fatalité : les lignes `@Composable` étaient
réputées hors d'atteinte d'un test JVM, et l'indicateur à suivre devait donc être le code hors UI. Ce
n'était vrai que d'une partie d'entre elles. Robolectric compose pour de vrai, et depuis que la surface de
carte est passée en paramètre (`MapSurface`), `MainScreen` se compose entier : `ui/routes` est passé de
1 % à 39 %, et le total de 27,5 % à 39,2 %.

Ce qui reste hors d'atteinte est plus étroit qu'annoncé, et se nomme précisément : le rendu des tuiles,
les gestes réels, et tout ce qui **s'ancre à un point de carte** - la position à l'écran vient de la
projection de MapLibre, qui n'existe pas sans carte.

### Tests d'instrumentation (androidTest)

- **Un seul** à ce jour : `MigrationInstrumentedTest` (2 `@Test`, ouverture de la base). Il prouve
  surtout que l'infrastructure `androidTest` compile et tourne sur appareil/émulateur.
- Les dossiers `androidTest/.../e2e/` et `androidTest/.../ui/` existent mais sont vides.

### Ce qui est passé de la liste d'attente aux tests joués

Sur la JVM, et non en instrumentation comme prévu ici.

| # | Ce qui est couvert | Ce qui ne l'est pas | Où |
|---|---|---|---|
| C-1 | le bouton GPS apparaît et disparaît de la carte selon `showGpsButton` | l'écriture du réglage par l'interrupteur, le second bouton (recentrage) | `MainScreenUiTest` |
| C-3 | le bouton du gestionnaire de fonds suit `showBasemapControlButton` | l'écriture du réglage par l'interrupteur | `MainScreenUiTest` |
| S-3 | en `swipe`, le burger disparaît de la carte | les deux autres modes | `MainScreenUiTest` |
| I-1 | toucher une étape remplie ouvre son champ sans emporter l'application | - | `RoutePlannerBandUiTest` |

I-2 (la frappe par-dessus un lieu retenu) reste à faire : elle attend toujours que le géocodeur soit
injecté plutôt qu'appelé en dur depuis le composable.

**Et trois choses qui n'étaient sur aucune liste**, parce qu'on les croyait hors de portée : à qui
reviennent les taps selon le mode de saisie actif, l'aller-retour complet du mode mesure (la règle
s'efface, sa bande porte la consigne, sa croix rend les taps à la sélection), et l'ouverture du menu
latéral. C'est le câblage de `MainScreen`, et il ne vit nulle part ailleurs.

## Tests à implémenter

**38 tests d'instrumentation et 12 e2e** listés ci-dessous (dont trois désormais couverts en partie sur
la JVM, cf. ci-dessus) : un par réglage des 4 onglets de
`SettingsScreen.kt` (28 rubriques recensées), plus 2 sur le planificateur d'itinéraire, qui ne relève
d'aucun onglet. Rien n'est implémenté au-delà du `MigrationInstrumentedTest`.

Question ouverte : tout implémenter ou prioriser ? Certains (import .mbtiles, dossiers, langue,
avatar) dépendent du sélecteur de fichiers système, donc d'UiAutomator et d'un appareil réel.

Origine des listes : les 4 premières rubriques de l'onglet Carte viennent de
`TESTS/tests_instru_e2e.txt` (rédigé par l'utilisateur). Le reste est déduit des captures de
`TESTS/screenshots/` et de l'inventaire des contrôles du code.

### Instrumentation, onglet Carte (10)

| #    | Réglage                      | Ce que vérifie le test                                                                       |
|------|------------------------------|----------------------------------------------------------------------------------------------|
| C-1  | Position GPS                 | le toggle écrit `showGpsButton` ; les deux boutons apparaissent et disparaissent de la carte |
| C-2  | Rotation                     | le toggle écrit `rotateGesturesEnabled` ; la carte accepte ou refuse le geste à deux doigts  |
| C-3  | Gestionnaire : bouton        | le toggle écrit `showBasemapControlButton`                                                   |
| C-4  | Gestionnaire : largeur       | slider 20-90 %, défaut 50 ; le panneau occupe la fraction demandée                           |
| C-5  | Gestionnaire : opacité       | slider 30-100 %, défaut 80 ; alpha appliqué tel quel, sans le `1 - x` d'avant                |
| C-6  | Gestionnaire : réinitialiser | remet 50 % et 80 %, sans toucher aux autres réglages                                         |
| C-7  | Échelle                      | le toggle écrit `showScale`                                                                  |
| C-8  | Taille des marqueurs         | stepper 16-80, défaut 36 ; borné aux deux extrémités                                         |
| C-9  | Infobulles : polices         | `bubbleFont` et `bubbleTitleFont`, chacun avec sa bascule gras                               |
| C-10 | Position de l'infobulle      | les 10 valeurs écrites, relues par `BubblePosition.of`                                       |

### Instrumentation, onglet Tuiles (7)

| #   | Réglage                      | Ce que vérifie le test                                                      |
|-----|------------------------------|-----------------------------------------------------------------------------|
| T-1 | Fond par défaut              | la liste propose fonds et composites ; le choix écrit `defaultBasemapId`    |
| T-2 | Importer un .mbtiles         | le fichier est copié dans le dossier MBTiles, un fournisseur local est créé |
| T-3 | Créer un composite           | popup : nom, opacité du premier plan, les deux couches                      |
| T-4 | Composite : édition          | le crayon rouvre la popup pré-remplie ; l'enregistrement met à jour         |
| T-5 | Composite : suppression      | la corbeille retire le composite ; le fond actif retombe sur un fond valide |
| T-6 | Fournisseurs : liste         | popup : chaque fournisseur a son toggle et son crayon                       |
| T-7 | Fournisseurs : import/export | un export puis import restitue URL et clés à l'identique                    |

### Instrumentation, onglet Profil (8)

| #   | Réglage              | Ce que vérifie le test                                                       |
|-----|----------------------|------------------------------------------------------------------------------|
| P-1 | Grille               | le toggle écrit `profileGrid` ; le tracé la dessine ou non                   |
| P-2 | Colorer par pente    | le toggle écrit `profileSlope`                                               |
| P-3 | Légende des pentes   | le toggle écrit `profileSlopeLegend` ; la légende apparaît sous le profil    |
| P-4 | Lissage              | slider 1-100 m, défaut 5 ; n'affecte que le profil affiché, jamais les stats |
| P-5 | Échelle verticale    | Auto puis valeur absolue ; le libellé bascule entre "Auto" et "1 cm = N m"   |
| P-6 | Infos ligne de titre | 6 chips ; l'ordre d'affichage suit celui des chips, pas celui des taps       |
| P-7 | Infos point courant  | 4 chips ; "Temps" masqué sur une trace sans horodatage                       |
| P-8 | Tailles de police    | les 5 steppers et leurs 5 bascules gras, indépendants                        |

### Instrumentation, onglet Système (11)

| #    | Réglage                         | Ce que vérifie le test                                            |
|------|---------------------------------|-------------------------------------------------------------------|
| S-1  | Dossier des imports             | le sélecteur écrit `importDir` ; "Défaut du système" si vide      |
| S-2  | Dossier des MBTiles             | le sélecteur écrit `mbtilesDir` ; "Dossier de l'app" si vide      |
| S-3  | Menu latéral                    | les 3 modes ; en `swipe`, le burger disparaît de la carte         |
| S-4  | Barre de statut                 | le toggle ; la carte passe ou non sous la barre                   |
| S-5  | Tolérance de tap                | slider 4-40 dp, défaut 16                                         |
| S-6  | Simplifier les traces           | le toggle ; n'affecte que les imports suivants                    |
| S-7  | Unités                          | métrique et impérial ; km ou mi, m ou ft dans le profil           |
| S-8  | Mises à jour                    | Auto/Manuel ; le bouton n'apparaît qu'en Manuel ; inerte en debug |
| S-9  | Langue                          | les 8 langues ; l'activité se recrée, l'interface change          |
| S-10 | Thème                           | Système, Clair, Sombre                                            |
| S-11 | Titre, avatar, réinitialisation | titre du menu, avatar par fichier ou URL, popup de reset          |

### Instrumentation, planificateur d'itinéraire (2)

Les deux seuls tests de cette liste nés d'un plantage réel, et non d'un inventaire de réglages : le
17 août 2026, taper dans le champ de départ d'un itinéraire calculé fermait l'application. Les deux
défauts ont été corrigés dans `RoutePlannerBand.kt` le jour même ; il manque toujours ce qui empêchera
leur retour. Aucun test unitaire JVM ne peut les voir - la faute est dans la composition, pas dans
`RoutePlannerState`. Ce seraient les premiers `createComposeRule` du dépôt (les dépendances
`compose.ui.test.junit4` et `compose.ui.test.manifest` sont déjà déclarées, et inutilisées).

| #   | Geste                            | Ce que vérifie le test                                                                        |
|-----|----------------------------------|-----------------------------------------------------------------------------------------------|
| I-1 | Clic sur une étape remplie       | le champ prend le focus au lieu de lever `FocusRequester is not initialized`                  |
| I-2 | Frappe par-dessus un lieu retenu | le champ ne porte que la frappe ; le géocodeur reçoit "Voi", non "VoiGrenoble, Isère, France" |

**Deux obstacles à lever d'abord.** `StepRow` est privé : le test passerait par `RoutePlannerBand`, donc
par un `RoutePlannerState` monté à la main avec une étape dont le lieu est déjà choisi. Et la recherche
appelle `Photon.search` directement depuis le composable : I-1 l'évite (il ne tape rien), mais I-2 part
sur le réseau 350 ms après la frappe tant que le géocodeur n'est pas injecté plutôt qu'appelé en dur.

### e2e (12)

Les 10 premiers viennent de `TESTS/tests_instru_e2e.txt`. E-11 et E-12 sont déduits des captures.

| #    | Parcours                      | Scénario                                                                       |
|------|-------------------------------|--------------------------------------------------------------------------------|
| E-1  | Fichiers invalides            | GPX, KML, KMZ et GeoJSON mal formés : popup d'erreur à l'import                |
| E-2  | Lot mixte                     | un lot mêlant valides, invalides et vides : les valides s'importent quand même |
| E-3  | GPX 3D avec temps             | tracé sur la carte, icône au menu, profil avec dénivelé et temps               |
| E-4  | GPX 2D sans altitude          | `test_locus.gpx` : bannière "Parcours sans altimétrie" au lieu du tracé        |
| E-5  | Boutons GPS                   | affichage des deux boutons, fond bleu, recentrage sur la position              |
| E-6  | Rotation                      | rotation à deux doigts, apparition de la flèche, réorientation au nord         |
| E-7  | Affichage du gestionnaire     | bouton présent, masqué par réglage, rétabli par la réinitialisation            |
| E-8  | Arborescence des fonds        | dossier créé, drag and drop, survie au redémarrage de l'app                    |
| E-9  | Largeur et opacité du panneau | 90 % de large, opacité appliquée                                               |
| E-10 | Échelle graphique             | unité en m au zoom 19, en km au zoom 4, puis masquage                          |
| E-11 | Composite par défaut          | un composite créé puis choisi comme fond par défaut survit au redémarrage      |
| E-12 | Langue                        | un changement de langue se propage à toute l'interface et persiste             |

**Réserves.** E-5 et E-6 dépendent du matériel (capteur GPS, geste à deux doigts) et resteront
fragiles en intégration continue. Depuis la correction d'opacité, C-5 et E-9 testent une **opacité**
(et non plus une transparence). Les captures de `TESTS/screenshots/` montrent le build d'avant cette
correction (elles affichent "Transparence du panneau : 20%", devenu "Opacité du panneau : 80%").

## Défauts connus repérés par les tests (non couverts, à trancher)

- **KML tronqué annonce "vide" au lieu de "invalide".** Un KML coupé mais syntaxiquement correct
  jusqu'à la coupure ne lève pas d'exception (le parseur XML ne se plaint pas des balises non
  fermées) : `LayerImporter` rend une couche vide, d'où le message "vide". Le comportement réel est
  verrouillé par le test `kml tronque rend une couche vide au lieu de lever` (`LayerImporterTest`).
  Corriger demanderait de vérifier après parsing que la racine s'est refermée.

- **Fixtures de test volumineuses.** `app/src/test/resources/fichiers/` contient de vrais exports
  (Wikiloc, OruxMaps, Locus, Google Earth), dont un KML de 1,4 Mo (2,1 Mo au total), déjà dans
  l'historique. Ils attrapent les particularités de chaque producteur ; à arbitrer entre valeur et
  poids du dépôt.
