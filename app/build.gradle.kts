plugins {
    alias(libs.plugins.android.application)
    jacoco
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
    ndkVersion = "27.0.12077973"

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

    /*
     * Un APK par architecture, au lieu d'un seul qui les porte toutes les quatre.
     *
     * MapLibre pese 8 a 12 Mo de code natif PAR architecture, soit 43,6 Mo des 61,5 Mo de l'APK v0.10.0 -
     * 71 % du poids - alors qu'un telephone n'en execute qu'UNE. Les deux variantes x86 ne servent qu'aux
     * emulateurs et ne s'executeront jamais sur un appareil reel.
     *
     * L'APK universel reste produit, et c'est indispensable : c'est lui que designe le champ apkUrl du
     * manifeste, donc celui que telechargent les versions deja installees, qui ne savent pas choisir. Les
     * versions suivantes prennent celui de leur architecture (cf. ReleaseInfo.urlFor).
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
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

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            // Robolectric charge le manifeste, les ressources et les assets reels de l'app :
            // sans ca, aucun test ne peut lire une string, une migration Room ou un asset.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

        }
    }
}

// Noms de fichier APK explicites (au lieu de app-release.apk / app-debug.apk) : c'est le nom
// que voit l'utilisateur en téléchargeant l'APK depuis la page GitHub Releases (§WORKFLOW.md §5).
androidComponents {
    onVariants { variant ->
        val base = if (variant.buildType == "release") "trailog-$gitVersion" else "trailog-debug-$gitVersion"
        variant.outputs.forEach { output ->
            val impl = output as com.android.build.api.variant.impl.VariantOutputImpl
            // Une sortie par architecture depuis les splits : sans le suffixe, les cinq s'ecriraient dans
            // le meme fichier et la CI publierait la derniere arrivee, au hasard. L'APK universel, lui,
            // garde le nom nu - c'est celui que le manifeste designe pour les versions deja installees.
            val abi = impl.filters.find { it.filterType.name == "ABI" }?.identifier
            impl.outputFileName.set(if (abi == null) "$base.apk" else "$base-$abi.apk")
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

    // --- Tests unitaires (JVM + Robolectric) ---
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    // Tests d'interface : les memes que sur un appareil, joues par Robolectric sur la JVM. Le choix est
    // explique dans TESTS.md - il tient a la CI, qui n'a pas d'emulateur, et a MapLibre, dont les
    // bibliotheques natives ne se chargent nulle part hors d'un appareil.
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)

    // --- Tests d'instrumentation et e2e (appareil/emulateur) ---
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// Couverture reelle des tests unitaires (JVM + Robolectric), plutot qu'une estimation.
//   ./gradlew :app:jacocoTestReport  ->  app/build/reports/jacoco/html/index.html
jacoco { toolVersion = libs.versions.jacoco.get() }

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Rapport de couverture des tests unitaires"
    reports { html.required.set(true); xml.required.set(true) }
    // Code genere et plomberie sans logique : les compter fausserait le chiffre vers le bas
    // sans rien dire de la qualite des tests.
    val filtered = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(
            "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
            "**/*_Impl*.*", "**/*Database_Impl*.*",          // Room genere
            "**/ComposableSingletons*.*", "**/*ComposableKt*.*", "**/*_Factory*.*",
            "**/databinding/**", "**/generated/**",
        )
    }
    classDirectories.setFrom(filtered)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/testDebugUnitTest.exec") })
}

// Robolectric charge les classes par son propre chargeur, sans emplacement source : sans cette option,
// Jacoco les ignore et le rapport annonce 0 % sur du code pourtant couvert (LayerImporter, migrations...).
// Le chiffre serait un mensonge, plus nuisible qu'une absence de mesure.
tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
