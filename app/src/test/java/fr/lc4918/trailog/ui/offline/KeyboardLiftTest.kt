package fr.lc4918.trailog.ui.offline

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La remontee du champ "Nom de la couche" au-dessus du clavier (cf. keyboardLiftPx).
 *
 * L'ecran ne se redimensionne pas quand le clavier sort : sans remontee, le champ, en bas du formulaire,
 * restait sous le clavier pendant qu'on le tapait.
 */
class KeyboardLiftTest {

    // Un ecran de 2000 px, un clavier de 800 px : son haut est a 1200 px.

    @Test fun `un champ cache par le clavier remonte juste au-dessus`() {
        assertEquals(1900f + 16f - 1200f, keyboardLiftPx(true, 1900f, 2000f, 800f, 16f), 1e-3f)
    }

    @Test fun `un champ deja au-dessus du clavier ne bouge pas`() {
        assertEquals(0f, keyboardLiftPx(true, 1000f, 2000f, 800f, 16f), 1e-3f)
    }

    /** Clavier ferme : le champ reprend sa place. */
    @Test fun `sans clavier, le champ reste a sa place`() {
        assertEquals(0f, keyboardLiftPx(true, 1900f, 2000f, 0f, 16f), 1e-3f)
    }

    /** Le clavier ouvert pour un autre champ ne deplace pas celui-ci. */
    @Test fun `sans le focus, le champ reste a sa place`() {
        assertEquals(0f, keyboardLiftPx(false, 1900f, 2000f, 800f, 16f), 1e-3f)
    }
}
