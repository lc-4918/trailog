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

    /** Le cas nominal : reglage actif, capteur en marche, rien d'ouvert par-dessus la carte. */
    private fun suit(
        enabled: Boolean = true,
        gpsActive: Boolean = true,
        plannerOpen: Boolean = false,
        layerOpen: Boolean = false,
        bubbleOpen: Boolean = false,
    ) = MapFollow.follows(enabled, gpsActive, plannerOpen, layerOpen, bubbleOpen)

    @Test fun `le suivi demande le reglage et le capteur`() {
        assertTrue(suit())
        assertFalse("reglage eteint", suit(enabled = false))
        assertFalse("capteur arrete", suit(gpsActive = false))
    }

    /** Les ecrans qui se servent de la carte ailleurs qu'a l'endroit ou l'on se tient. */
    @Test fun `le planificateur et le profil d'une trace suspendent le suivi`() {
        assertFalse("planificateur ouvert", suit(plannerOpen = true))
        assertFalse("profil d'une trace ouvert", suit(layerOpen = true))
    }

    /**
     * Une infobulle decrit un point precis de la carte : la recentrer sur la position emporterait ce point
     * hors de l'ecran, et l'infobulle avec, pendant qu'on la lit.
     */
    @Test fun `une infobulle ouverte suspend le suivi`() {
        assertFalse("infobulle ouverte", suit(bubbleOpen = true))
        assertTrue("infobulle refermee", suit(bubbleOpen = false))
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

    /**
     * Fermeture d'une infobulle : l'ecran la releve comme un geste, et le silence repart entier. Sans quoi
     * une infobulle lue longuement laissait un dernier geste vieux de plusieurs minutes, et la carte
     * sautait sur la position a l'instant meme ou l'on refermait la bulle.
     */
    @Test fun `la fermeture d'une infobulle fait repartir le silence entier`() {
        val fermeture = 100_000L
        assertEquals(5_000L, MapFollow.waitMs(now = fermeture, lastGestureAt = fermeture, delayMs = 5_000L))
        assertEquals(0L, MapFollow.waitMs(now = fermeture + 5_000L, fermeture, delayMs = 5_000L))
    }

    /** Horloge qui recule : le suivi ne doit pas rester suspendu pour l'eternite. */
    @Test fun `un geste dans le futur ne bloque pas le suivi`() {
        assertEquals(0L, MapFollow.waitMs(now = 5_000L, lastGestureAt = 10_000L, delayMs = 5_000L))
    }
}
