package fr.lc4918.trailog.ui.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suivi de la position par la carte.
 *
 * Deux fautes possibles, aussi genantes l'une que l'autre : une carte qui revient sous les doigts pendant
 * qu'on la deplace, et une carte qui ne revient jamais. Aucune des deux ne leve quoi que ce soit.
 */
class MapFollowTest {

    @Test fun `le suivi demande le reglage et le capteur`() {
        assertTrue(MapFollow.follows(enabled = true, gpsActive = true, plannerOpen = false, layerOpen = false))
        assertFalse("reglage eteint",
            MapFollow.follows(enabled = false, gpsActive = true, plannerOpen = false, layerOpen = false))
        assertFalse("capteur arrete",
            MapFollow.follows(enabled = true, gpsActive = false, plannerOpen = false, layerOpen = false))
    }

    /** Les deux ecrans qui se servent de la carte ailleurs qu'a l'endroit ou l'on se tient. */
    @Test fun `le planificateur et le profil d'une trace suspendent le suivi`() {
        assertFalse("planificateur ouvert",
            MapFollow.follows(enabled = true, gpsActive = true, plannerOpen = true, layerOpen = false))
        assertFalse("profil d'une trace ouvert",
            MapFollow.follows(enabled = true, gpsActive = true, plannerOpen = false, layerOpen = true))
    }

    @Test fun `sans geste, la carte suit sans attendre`() {
        assertEquals(0L, MapFollow.waitMs(now = 10_000L, lastGestureAt = 0L))
    }

    @Test fun `un geste recent fait attendre ce qu'il reste du silence`() {
        assertEquals(3_000L, MapFollow.waitMs(now = 12_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
        assertEquals(5_000L, MapFollow.waitMs(now = 10_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }

    /**
     * L'attente est ce qu'il RESTE, non le delai entier : les positions arrivent environ une par seconde,
     * et repartir de zero a chacune ferait reculer l'echeance indefiniment.
     */
    @Test fun `l'attente decroit a mesure que les positions arrivent`() {
        val geste = 10_000L
        val attentes = (1..6).map { MapFollow.waitMs(now = geste + it * 1_000L, geste, delayMs = 5_000L) }
        assertEquals(listOf(4_000L, 3_000L, 2_000L, 1_000L, 0L, 0L), attentes)
    }

    @Test fun `un geste ancien n'attend plus`() {
        assertEquals(0L, MapFollow.waitMs(now = 100_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }

    /** Horloge qui recule : le suivi ne doit pas rester suspendu pour l'eternite. */
    @Test fun `un geste dans le futur ne bloque pas le suivi`() {
        assertEquals(0L, MapFollow.waitMs(now = 5_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }
}
