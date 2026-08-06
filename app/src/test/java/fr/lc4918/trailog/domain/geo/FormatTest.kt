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

    /** Distance isolee (mesure du geocodage) : sous le kilometre, des metres entiers. "0,18 km" y serait
     *  juste mais illisible, la ou "180 m" se lit d'un coup d'oeil. */
    @Test fun `distance isolee sous le kilometre en metres`() {
        assertEquals("180 m", Format.shortDistance(180.4))
        assertEquals("0 m", Format.shortDistance(0.0))
        assertEquals("999 m", Format.shortDistance(999.4))
    }

    @Test fun `distance isolee au-dela du kilometre en km`() {
        assertEquals("1,0 km", Format.shortDistance(1000.0).replace('.', ','))
        assertEquals("12,3 km", Format.shortDistance(12345.0).replace('.', ','))
    }

    @Test fun `distance isolee en pieds puis en miles`() {
        assertEquals("328 ft", Format.shortDistance(100.0, imperial = true))
        assertEquals("2,0 mi", Format.shortDistance(2 * 1609.344, imperial = true).replace('.', ','))
    }

    /** Une distance non finie ne doit rien afficher plutot que "NaN m" a cote du bouton de mesure. */
    @Test fun `distance isolee non finie ou negative ne s'affiche pas`() {
        assertEquals("", Format.shortDistance(Double.NaN))
        assertEquals("", Format.shortDistance(-1.0))
    }
}
