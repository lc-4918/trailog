# Workflow CI/CD - Trailog

Ce document explique comment fonctionne l'intégration continue et la distribution des
APK de Trailog via GitHub Actions et GitHub Releases.

## 1. Vue d'ensemble

Le workflow est défini dans [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)
et se déclenche à chaque `push` sur le dépôt. Il a deux comportements distincts selon ce
qui est poussé :

- **Un push sur une branche** -> build d'un APK **debug**, disponible en artifact GitHub Actions.
- **Un push d'un tag `vX.Y.Z`** -> build d'un APK **release signé**, publié comme
  **GitHub Release** téléchargeable publiquement, puis publication du manifeste
  `latest-release.json` qui permet à l'app de se savoir périmée.

GitHub Releases fait ainsi office de **"store"** : les utilisateurs téléchargent
directement l'APK depuis la page Releases du dépôt, sans passer par le Play Store.
Le manifeste ferme la boucle : l'app installée le lit au démarrage et propose elle-même
la mise à jour (voir [section 6](#6-mises-à-jour-automatiques)).

## 2. Workflow Build Debug

**Déclenchement :** tout `git push` sur une branche (`refs/heads/**`).

**Ce qu'il fait :**
1. Checkout du code.
2. Setup JDK 17 (Temurin).
3. `./gradlew :app:assembleDebug`.
4. Upload de l'APK généré comme **artifact** de l'exécution.

**Où trouver l'APK :** onglet **Actions** du dépôt -> sélectionner l'exécution du workflow
correspondant au commit -> section **Artifacts** en bas de page -> télécharger
`trailog-debug-<sha>.zip` (contient l'APK).

**Utilité :** tester rapidement les dernières modifications sur un appareil, sans créer de
release officielle. L'APK debug n'est pas signé avec la clé de release.

## 3. Workflow Release

**Déclenchement :** push d'un tag Git au format `vX.Y.Z` (ex. `v1.2.0`).

**Prérequis :** les 4 secrets suivants doivent être configurés dans
**Settings -> Secrets and variables -> Actions** du dépôt :

| Secret | Contenu |
|---|---|
| `KEYSTORE_FILE` | Le fichier keystore de signature (`.jks`), encodé en base64 |
| `KEYSTORE_PASSWORD` | Mot de passe du keystore |
| `KEY_ALIAS` | Alias de la clé de signature |
| `KEY_PASSWORD` | Mot de passe de la clé |

**Processus complet :**
1. Checkout du code au commit correspondant au tag.
2. Setup JDK 17.
3. Décodage du keystore base64 vers un fichier temporaire (jamais écrit dans le dépôt).
4. `./gradlew :app:assembleRelease`, signé via les variables d'environnement
   `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
   (voir la config `signingConfigs` dans `app/build.gradle.kts`).
5. Suppression du keystore temporaire.
6. Création d'une **GitHub Release** portant le nom du tag, avec changelog généré
   automatiquement à partir des commits, et l'APK release joint en pièce téléchargeable.
7. Génération de **`latest-release.json`** et commit sur `main` (voir section 6).

**Où trouver l'APK :** page **Releases** du dépôt (publique).

**Nom de l'APK :** `trailog-vX.Y.Z.apk`, fixé par le bloc `androidComponents` de
`app/build.gradle.kts`. L'étape 7 reconstruit ce nom pour composer l'URL de téléchargement et
vérifie que le fichier existe avant de publier le manifeste : sans ce garde-fou, une
divergence de nommage publierait une URL morte que l'app tenterait de télécharger.

## 4. Comment créer une Release

1. Préparer le code : commits finaux mergés sur `main`, testés.
2. Créer un tag local en respectant le [Semantic Versioning](https://semver.org/lang/fr/) :
   ```bash
   git tag -a v1.2.0 -m "Release 1.2.0"
   ```
3. Pousser le tag :
   ```bash
   git push origin v1.2.0
   ```
4. Le workflow se déclenche automatiquement (job **Build & publish signed release APK**).
5. Suivre l'exécution dans l'onglet **Actions** jusqu'à completion.
6. La Release GitHub est créée automatiquement avec l'APK signé et les notes de version.

## 5. Fonctionnement du store

- La page **Releases** liste toutes les versions publiées, de la plus récente à la plus ancienne.
- Chaque release contient : l'APK signé téléchargeable, et un changelog basé sur les commits.
- Les utilisateurs téléchargent et installent l'APK directement, sans inscription,
  sans compte, sans magasin d'applications tiers.
- Avantages de cette approche : pas de délai de revue, contrôle total sur la distribution,
  cohérent avec l'esprit de la licence **GPL** (code et binaires librement accessibles).

## 6. Mises à jour automatiques

L'app n'est sur aucun store : rien ne la préviendrait qu'une version existe. La CI publie donc
un manifeste que l'app consulte elle-même.

### Le manifeste

L'étape **Publish latest-release.json on main** écrit ce fichier à la racine de `main` :

```json
{
  "version": "0.2.0",
  "versionCode": 200,
  "releaseDate": "2026-07-15",
  "apkUrl": "https://github.com/lc-4918/trailog/releases/download/v0.2.0/trailog-v0.2.0.apk",
  "changelog": ""
}
```

Il est servi tel quel par
`https://raw.githubusercontent.com/lc-4918/trailog/main/latest-release.json`.

**Pourquoi `main` plutôt qu'une branche dédiée ou l'API GitHub :** `raw.githubusercontent.com`
est un CDN sans quota, là où l'API GitHub plafonne à 60 requêtes par heure et par IP pour un
appel anonyme. Le fichier reste par ailleurs versionné avec le reste du code.

**Pourquoi la CI ne boucle pas :** un push effectué avec le `GITHUB_TOKEN` par défaut ne
redéclenche aucun workflow. Le commit du manifeste sur `main` ne relance donc pas le job debug,
qui écoute pourtant toutes les branches.

### Le contrat de version

`versionCode` est calculé **deux fois**, et les deux calculs doivent rester d'accord :

| Où | Quoi |
|---|---|
| `app/build.gradle.kts` | dérive le tag git en `maj * 10000 + min * 100 + patch` |
| étape CI du manifeste | refait le même calcul à partir du tag |

L'app compare ce nombre à son propre `BuildConfig.VERSION_CODE`. Une divergence entre les deux
calculs ne casserait rien de visible : les mises à jour cesseraient simplement d'être proposées,
en silence. Le test `ReleaseInfoTest` verrouille ce contrat, ainsi que la forme du JSON.

Le `versionName` ne sert **pas** à la comparaison : les builds de développement portent un
suffixe (`0.2.0-23-gabc1234`), qu'un découpage numérique lirait comme plus récent que `0.2.0`.

### Côté app

Le détail vit dans `app/src/main/java/fr/lc4918/trailog/update/`. En résumé : lecture du
manifeste, comparaison, téléchargement via `DownloadManager` dans le dossier privé de l'app,
puis lancement de l'installateur système. Le réglage **Système -> Mises à jour** offre le choix
entre vérification automatique au démarrage et vérification manuelle.

La vérification est **inerte en build debug** : celui-ci a son propre `applicationId`
(suffixe `.debug`) et une autre signature, l'APK de release ne peut donc pas le remplacer.

### Conséquence pour une nouvelle version

Une version ne peut pas s'annoncer elle-même : seules les installations portant déjà le code de
vérification liront le manifeste. Les utilisateurs d'une version antérieure à `v0.2.0` doivent
installer celle-ci à la main depuis la page Releases ; les mises à jour suivantes leur seront
proposées automatiquement.

## 7. Bonnes pratiques

- **Versioning** : respecter le format `vMAJOR.MINOR.PATCH` (Semantic Versioning).
- **Changelog** : les notes générées automatiquement listent les commits ; garder des
  messages de commit clairs facilite leur lecture.
- **Tester avant de tagger** : s'assurer que le build debug (déclenché à chaque push) passe
  avant de créer un tag de release.
- **Secrets** : ne jamais committer de keystore, mot de passe ou clé privée dans le dépôt ;
  toujours passer par les secrets GitHub Actions.
- **Vérifier le manifeste après une release** : la Release peut être verte et le manifeste
  faux. Contrôler que
  [`latest-release.json`](https://raw.githubusercontent.com/lc-4918/trailog/main/latest-release.json)
  annonce la bonne version et que son `apkUrl` répond bien.
