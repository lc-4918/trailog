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

    /** Copie conforme de ce que produit l'etape "Publish latest-release.json on main". */
    private val fromCi = """
        {
          "version": "1.2.3",
          "versionCode": 10203,
          "releaseDate": "2026-07-15",
          "apkUrl": "https://github.com/lc-4918/trailog/releases/download/v1.2.3/trailog-v1.2.3.apk",
          "changelog": ""
        }
    """.trimIndent()

    @Test fun `manifeste de la CI relu tel quel`() {
        val r = json.decodeFromString<ReleaseInfo>(fromCi)
        assertEquals("1.2.3", r.version)
        assertEquals(10203, r.versionCode)
        assertEquals("2026-07-15", r.releaseDate)
        assertTrue(r.apkUrl.endsWith("/trailog-v1.2.3.apk"))
        assertEquals("", r.changelog)
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
}
