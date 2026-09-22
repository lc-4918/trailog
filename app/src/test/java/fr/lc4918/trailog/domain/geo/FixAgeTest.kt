package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'age d'une position, sur l'horloge monotone du telephone.
 *
 * Le cas du terrain : le GPS eteint par l'economie d'energie a l'extinction de l'ecran, dix kilometres
 * parcourus, et une derniere position recue qui date du depart. "Partir d'ou je suis" la prenait telle
 * quelle, et calculait un itineraire depuis la maison.
 */
class FixAgeTest {

    private val deuxMinutes = 2 * 60 * 1000L

    @Test fun `une position de l'instant est fraiche`() {
        assertTrue(FixAge.fresh(measuredAtMs = 100_000, nowMs = 100_000, maxAgeMs = deuxMinutes))
    }

    @Test fun `une position d'une minute est fraiche`() {
        assertTrue(FixAge.fresh(measuredAtMs = 100_000, nowMs = 160_000, maxAgeMs = deuxMinutes))
    }

    /** La borne appartient au frais : deux minutes tout juste passent encore. */
    @Test fun `la borne passe`() {
        assertTrue(FixAge.fresh(measuredAtMs = 100_000, nowMs = 220_000, maxAgeMs = deuxMinutes))
        assertFalse(FixAge.fresh(measuredAtMs = 100_000, nowMs = 220_001, maxAgeMs = deuxMinutes))
    }

    /** Le cas signale : une demi-heure d'ecran eteint, et la position du depart qui traine encore. */
    @Test fun `la position d'avant l'extinction de l'ecran a fait son temps`() {
        assertFalse(FixAge.fresh(measuredAtMs = 100_000, nowMs = 100_000 + 30 * 60_000, maxAgeMs = deuxMinutes))
    }

    /** Une mesure datee dans l'avenir ne peut pas etre plus vieille que maintenant : elle passe. */
    @Test fun `une pendule de travers ne fait pas jeter la position`() {
        assertTrue(FixAge.fresh(measuredAtMs = 200_000, nowMs = 100_000, maxAgeMs = deuxMinutes))
    }
}
