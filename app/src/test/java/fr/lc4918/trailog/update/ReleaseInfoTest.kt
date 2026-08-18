package fr.lc4918.trailog.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le manifeste est ecrit par la CI (jq, cf. .github/workflows/build-release.yml) et lu ici : les deux
 * doivent rester d'accord, sans quoi l'app cesserait silencieusement de voir les mises a jour.
 */
class ReleaseInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** Copie conforme de ce que produit l'etape "Generate latest-release.json". */
    private val fromCi = """
        {
          "version": "1.2.3",
          "versionCode": 10203,
          "releaseDate": "2026-07-15",
          "apkUrl": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3.apk",
          "changelog": "- import : un fichier fautif n'interrompt plus rien\n- reglages : les deux sliders parlent d'opacite"
        }
    """.trimIndent()

    @Test fun `manifeste de la CI relu tel quel`() {
        val r = json.decodeFromString<ReleaseInfo>(fromCi)
        assertEquals("1.2.3", r.version)
        assertEquals(10203, r.versionCode)
        assertEquals("2026-07-15", r.releaseDate)
        assertTrue(r.apkUrl.endsWith("/trailog-v1.2.3.apk"))
        // Changelog multi-lignes : la CI liste les sujets de commits (cf. build-release.yml), les sauts
        // de ligne arrivent echappes en \n dans le JSON et doivent etre restitues tels quels.
        assertEquals(2, r.changelog.lines().size)
        assertTrue(r.changelog.startsWith("- import"))
    }

    /** versionCode = maj*10000 + min*100 + patch : meme calcul dans build.gradle.kts et dans la CI. */
    @Test fun `le versionCode ordonne bien les versions`() {
        fun code(maj: Int, min: Int, patch: Int) = maj * 10_000 + min * 100 + patch
        assertTrue(code(0, 1, 3) > code(0, 1, 2))
        assertTrue(code(0, 2, 0) > code(0, 1, 99))
        assertTrue(code(1, 0, 0) > code(0, 99, 99))
    }

    /** Un champ ajoute plus tard au manifeste ne doit pas casser les versions deja installees. */
    @Test fun `un champ inconnu est ignore`() {
        val r = json.decodeFromString<ReleaseInfo>(
            """{"version":"1.0.0","versionCode":10000,"releaseDate":"2026-01-01",
                "apkUrl":"https://x/y.apk","futur":"champ ajoute plus tard"}"""
        )
        assertEquals(10000, r.versionCode)
        assertEquals("", r.changelog)   // absent du JSON : valeur par defaut
    }

    // ---------- Choix de l'APK selon l'architecture ----------

    private val avecAbi = ReleaseInfo(
        version = "1.0.0", versionCode = 10000, releaseDate = "2026-01-01",
        apkUrl = "https://x/trailog-v1.0.0.apk",
        apkUrls = mapOf(
            "arm64-v8a" to "https://x/trailog-v1.0.0-arm64-v8a.apk",
            "armeabi-v7a" to "https://x/trailog-v1.0.0-armeabi-v7a.apk",
        ),
    )

    /**
     * Un APK par architecture divise le telechargement par deux, le code natif de MapLibre pesant 71 % du
     * total. L'ordre de Build.SUPPORTED_ABIS est une PREFERENCE : un appareil 64 bits liste aussi le 32
     * bits, qu'il sait executer mais plus lentement.
     */
    @Test fun `l'architecture preferee l'emporte`() {
        assertEquals("https://x/trailog-v1.0.0-arm64-v8a.apk",
            avecAbi.urlFor(listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("https://x/trailog-v1.0.0-armeabi-v7a.apk",
            avecAbi.urlFor(listOf("armeabi-v7a")))
    }

    /** Architecture non publiee : mieux vaut telecharger l'APK universel que rien du tout. */
    @Test fun `une architecture inconnue retombe sur l'apk universel`() {
        assertEquals("https://x/trailog-v1.0.0.apk", avecAbi.urlFor(listOf("mips")))
        assertEquals("https://x/trailog-v1.0.0.apk", avecAbi.urlFor(emptyList()))
    }

    /**
     * Un manifeste d'AVANT les splits ne porte pas apkUrls, et l'application doit continuer de se mettre a
     * jour - c'est le cas de toute version publiee jusqu'ici.
     */
    @Test fun `un manifeste sans urls par architecture reste installable`() {
        val ancien = json.decodeFromString<ReleaseInfo>(
            """{"version":"1.0.0","versionCode":10000,"releaseDate":"2026-01-01",
                "apkUrl":"https://x/y.apk"}"""
        )
        assertEquals("https://x/y.apk", ancien.urlFor(listOf("arm64-v8a")))
    }

    @Test fun `les urls par architecture se relisent du manifeste`() {
        val r = json.decodeFromString<ReleaseInfo>(
            """{"version":"1.0.0","versionCode":10000,"releaseDate":"2026-01-01",
                "apkUrl":"https://x/y.apk",
                "apkUrls":{"arm64-v8a":"https://x/y-arm64-v8a.apk"}}"""
        )
        assertEquals("https://x/y-arm64-v8a.apk", r.urlFor(listOf("arm64-v8a")))
    }

    /**
     * Le manifeste tel que la CI le produit, recopie mot pour mot depuis build-release.yml.
     *
     * Ce que ce cas attrape est MUET : une cle mal orthographiee dans le workflow ("apkurls") laisserait
     * la lecture reussir, apkUrls vide, et TOUS les appareils retomberaient a jamais sur l'APK universel -
     * deux fois plus lourd - sans que rien ne le signale.
     */
    @Test fun `le manifeste de la CI donne bien l'apk de l'architecture`() {
        val r = json.decodeFromString<ReleaseInfo>(
            """{
              "version": "1.2.3",
              "versionCode": 10203,
              "releaseDate": "2026-08-18",
              "apkUrl": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3.apk",
              "apkUrls": {
                "arm64-v8a": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3-arm64-v8a.apk",
                "armeabi-v7a": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3-armeabi-v7a.apk",
                "x86": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3-x86.apk",
                "x86_64": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3-x86_64.apk"
              },
              "changelog": "- un commit"
            }"""
        )
        assertEquals(4, r.apkUrls.size)
        assertTrue(r.urlFor(listOf("arm64-v8a", "armeabi-v7a")).endsWith("-arm64-v8a.apk"))
        assertTrue(r.urlFor(listOf("armeabi-v7a")).endsWith("-armeabi-v7a.apk"))
    }
}
