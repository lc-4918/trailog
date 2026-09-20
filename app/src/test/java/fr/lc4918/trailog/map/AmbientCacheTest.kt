package fr.lc4918.trailog.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Le poids du cache de tuiles, celui qu'on annonce avant de l'effacer. Compter un fichier de trop - une
 * base de traces, une sauvegarde - ferait annoncer un poids qui ne partira pas.
 */
class AmbientCacheTest {

    @get:Rule val dossier = TemporaryFolder()

    @Test fun `seuls les fichiers de la carte comptent`() {
        assertTrue(AmbientCache.isCacheFile("mbgl-offline.db"))
        assertTrue(AmbientCache.isCacheFile("mbgl-offline.db-wal"))
        assertFalse("la base de l'application", AmbientCache.isCacheFile("trailog.db"))
        assertFalse("une zone hors ligne", AmbientCache.isCacheFile("zone.mbtiles"))
    }

    @Test fun `le poids est la somme des fichiers du cache`() {
        val cache = dossier.newFile("mbgl-offline.db").apply { writeBytes(ByteArray(2_000)) }
        val journal = dossier.newFile("mbgl-offline.db-wal").apply { writeBytes(ByteArray(500)) }
        val autre = dossier.newFile("trailog.db").apply { writeBytes(ByteArray(9_000)) }
        assertEquals(2_500L, AmbientCache.sizeOf(listOf(cache, journal, autre)))
    }

    @Test fun `sans fichier, le cache est vide`() {
        assertEquals(0L, AmbientCache.sizeOf(emptyList()))
    }
}
