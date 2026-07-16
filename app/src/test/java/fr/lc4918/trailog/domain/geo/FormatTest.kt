package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatage affiche dans la barre de titre du profil et sur le point courant. */
class FormatTest {
    @Test fun `duree sous la minute en secondes`() {
        assertEquals("7 sec", Format.duration(7.0))
        assertEquals("59 sec", Format.duration(59.4))
    }

    @Test fun `duree sous l'heure en minutes`() {
        assertEquals("1 min", Format.duration(60.0))
        assertEquals("26 min", Format.duration(26 * 60.0))
    }

    @Test fun `duree sous le jour en heures et minutes`() {
        assertEquals("1 h 48 min", Format.duration(3600.0 + 48 * 60))
        assertEquals("2 h", Format.duration(7200.0))          // minutes nulles : omises
    }

    /** L'arrondi des minutes peut atteindre 60 : sans report, on afficherait "1 h 60 min". */
    @Test fun `report quand les minutes arrondissent a 60`() {
        assertEquals("2 h", Format.duration(3600.0 + 59 * 60 + 59))
    }

    @Test fun `duree au-dela du jour`() {
        assertEquals("2 j 3 h", Format.duration(2 * 86400.0 + 3 * 3600))
        assertEquals("1 j", Format.duration(86400.0))          // heures nulles : omises
    }

    /** Meme report que pour les minutes, une case au-dessus. */
    @Test fun `report quand les heures arrondissent a 24`() {
        assertEquals("2 j", Format.duration(86400.0 + 23 * 3600 + 59 * 60 + 59))
    }

    @Test fun `duree absente ou non finie donne une chaine vide`() {
        assertEquals("", Format.duration(null))
        assertEquals("", Format.duration(Double.NaN))
        assertEquals("", Format.duration(Double.POSITIVE_INFINITY))
    }

    @Test fun `duree negative bornee a zero`() {
        assertEquals("0 sec", Format.duration(-5.0))
    }

    @Test fun `distance en km ou en miles`() {
        assertEquals("1,50 km", Format.distance(1500.0).replace('.', ','))
        assertEquals("1,00 mi", Format.distance(1609.344, imperial = true).replace('.', ','))
    }

    @Test fun `altitude en metres ou en pieds`() {
        assertEquals("1250 m", Format.elevation(1250.4))
        assertEquals("328 ft", Format.elevation(100.0, imperial = true))
    }
}
