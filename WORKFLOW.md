# Workflow CI/CD - Trailog

Ce document explique comment fonctionne l'intégration continue et la distribution des
APK de Trailog via GitHub Actions et GitHub Releases.

## 1. Vue d'ensemble

Le workflow est défini dans [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)
et se déclenche à chaque `push` sur le dépôt. Il a deux comportements distincts selon ce
qui est poussé :

- **Un push sur une branche** → build d'un APK **debug**, disponible en artifact GitHub Actions.
- **Un push d'un tag `vX.Y.Z`** → build d'un APK **release signé**, publié comme
  **GitHub Release** téléchargeable publiquement.

GitHub Releases fait ainsi office de **"store"** : les utilisateurs téléchargent
directement l'APK depuis la page Releases du dépôt, sans passer par le Play Store.

## 2. Workflow Build Debug

**Déclenchement :** tout `git push` sur une branche (`refs/heads/**`).

**Ce qu'il fait :**
1. Checkout du code.
2. Setup JDK 17 (Temurin).
3. `./gradlew :app:assembleDebug`.
4. Upload de l'APK généré comme **artifact** de l'exécution.

**Où trouver l'APK :** onglet **Actions** du dépôt → sélectionner l'exécution du workflow
correspondant au commit → section **Artifacts** en bas de page → télécharger
`trailog-debug-<sha>.zip` (contient l'APK).

**Utilité :** tester rapidement les dernières modifications sur un appareil, sans créer de
release officielle. L'APK debug n'est pas signé avec la clé de release.

## 3. Workflow Release

**Déclenchement :** push d'un tag Git au format `vX.Y.Z` (ex. `v1.2.0`).

**Prérequis :** les 4 secrets suivants doivent être configurés dans
**Settings → Secrets and variables → Actions** du dépôt :

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

**Où trouver l'APK :** page **Releases** du dépôt (publique).

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

## 6. Bonnes pratiques

- **Versioning** : respecter le format `vMAJOR.MINOR.PATCH` (Semantic Versioning).
- **Changelog** : les notes générées automatiquement listent les commits ; garder des
  messages de commit clairs facilite leur lecture.
- **Tester avant de tagger** : s'assurer que le build debug (déclenché à chaque push) passe
  avant de créer un tag de release.
- **Secrets** : ne jamais committer de keystore, mot de passe ou clé privée dans le dépôt ;
  toujours passer par les secrets GitHub Actions.
