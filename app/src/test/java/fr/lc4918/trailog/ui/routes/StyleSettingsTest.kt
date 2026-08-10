package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.SettingsEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce qui doit reconstruire le style de la carte, et ce qui ne doit pas.
 *
 * Une faute ici ne casse rien de visible : le style se reconstruit a la prochaine cause valable, et le
 * reglage oublie s'applique donc avec un tour de retard - on eteint le relief, il reste ; on le rallume,
 * il n'apparait qu'au changement de fond suivant. C'est arrive une fois, quand l'ombrage a quitte le fond
 * DEM pour les reglages ; ce test est la pour que ca ne se reproduise pas en silence.
 */
class StyleSettingsTest {
    private val base = SettingsEntity()

    @Test fun `un reglage inchange ne reconstruit pas le style`() {
        assertTrue(sameStyleSettings(base, base.copy()))
    }

    @Test fun `changer de fond de plan reconstruit le style`() {
        assertFalse(sameStyleSettings(base, base.copy(defaultBasemapId = "ign_fr")))
    }

    @Test fun `changer le dossier des mbtiles reconstruit le style`() {
        assertFalse(sameStyleSettings(base, base.copy(mbtilesDir = "/sdcard/cartes")))
    }

    /** Le cas de la regression : l'ombrage vit dans les reglages depuis qu'il a quitte le fond DEM. */
    @Test fun `basculer l'ombrage du relief reconstruit le style`() {
        assertFalse(sameStyleSettings(base, base.copy(hillshadeOn = !base.hillshadeOn)))
    }

    /**
     * La camera s'ecrit a chaque arret de la carte : la laisser passer relancerait la construction du
     * style, et le chargement d'un style vectoriel distant, a chaque deplacement.
     */
    @Test fun `deplacer la carte ne reconstruit pas le style`() {
        assertTrue(sameStyleSettings(base, base.copy(lastLat = 45.1, lastLon = 6.2, lastZoom = 14.0)))
    }

    /** Meme chose pour tout ce qui ne decrit que l'interface : polices, infobulles, tolerances. */
    @Test fun `un reglage d'affichage ne reconstruit pas le style`() {
        assertTrue(sameStyleSettings(base, base.copy(bubbleFont = 20, tapToleranceDp = 24, showScale = false)))
    }

    /** Null d'un cote (reglages pas encore lus) : c'est un changement, le style n'a pas encore ete bati. */
    @Test fun `l'arrivee des reglages reconstruit le style`() {
        assertFalse(sameStyleSettings(null, base))
    }
}
