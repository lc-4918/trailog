package fr.lc4918.trailog.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Les ids de composites cohabitent avec les ids de providers dans les memes champs
 *  (settings.defaultBasemapId) : le prefixe est ce qui les distingue. */
class CompositeBasemapsTest {
    @Test fun `un id de composite fait l'aller-retour`() {
        listOf(0L, 1L, 42L, Long.MAX_VALUE).forEach {
            assertEquals(it, compositeIdFromBasemapId(compositeBasemapId(it)))
        }
    }

    @Test fun `un id de provider n'est pas pris pour un composite`() {
        listOf("osm", "ign_fr", "af3v", "", "composite", "12").forEach {
            assertNull(it, compositeIdFromBasemapId(it))
        }
    }

    /** Prefixe present mais suffixe non numerique : on rend null plutot que de planter. */
    @Test fun `un id de composite mal forme rend null`() {
        assertNull(compositeIdFromBasemapId("composite_"))
        assertNull(compositeIdFromBasemapId("composite_abc"))
    }
}
