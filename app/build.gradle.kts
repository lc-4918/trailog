plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Version dérivée du tag git (cf. WORKFLOW.md §4 : tag vMAJOR.MINOR.PATCH avant release) plutôt
// que codée en dur, sinon toutes les releases se déclarent avec le même versionCode/versionName
// (cassant la mise à jour Android et empêchant toute publication sur un store).
// Sur un commit exactement taggé (déclencheur de la CI release), "git describe" rend le tag brut
// ("v1.2.0") ; sinon un identifiant descriptif ("v1.2.0-3-g413b197") utile pour les builds de dev.
fun gitDescribe(): String = try {
    val process = ProcessBuilder("git", "describe", "--tags", "--always", "--dirty")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    output.ifBlank { "v0.0.0" }
} catch (e: Exception) {
    "v0.0.0"
}

val gitVersion = gitDescribe()
val semver = Regex("""^v?(\d+)\.(\d+)\.(\d+)""").find(gitVersion)
val appVersionCode = semver?.destructured?.let { (maj, min, patch) ->
    maj.toInt() * 10_000 + min.toInt() * 100 + patch.toInt()
} ?: 1
val appVersionName = gitVersion.removePrefix("v")

android {
    namespace = "fr.lc4918.trailog"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.lc4918.trailog"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode.coerceAtLeast(1)
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    // Signature du build release pilotée par variables d'environnement (CI uniquement).
    // En local, sans ces variables, le build release reste simplement non signé.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // applicationId distinct du release (signé avec une autre clé, cf. keystore CI) :
            // permet d'installer le build de dev à côté de la version officielle sans que
            // l'un ne bloque l'autre avec un conflit de signature (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

// Noms de fichier APK explicites (au lieu de app-release.apk / app-debug.apk) : c'est le nom
// que voit l'utilisateur en téléchargeant l'APK depuis la page GitHub Releases (§WORKFLOW.md §5).
androidComponents {
    onVariants { variant ->
        val fileName = if (variant.buildType == "release") "trailog-$gitVersion.apk" else "trailog-debug-$gitVersion.apk"
        variant.outputs.forEach { output ->
            (output as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName.set(fileName)
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.datastore.prefs)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    // Carte. Backend Vulkan par défaut. En cas de souci d'émulateur, remplacer par :
    // implementation("org.maplibre.gl:android-sdk-opengl:11.11.0")
    implementation(libs.maplibre)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
}
