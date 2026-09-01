package fr.lc4918.trailog.ui.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D'ou la fleche de position tire sa direction.
 *
 * Ce que ces cas verrouillent ne leve jamais rien : une fleche qui pointe a cote reste une fleche, et
 * l'on met sa gene sur le compte du GPS. Les deux symptomes releves sur le terrain - "elle bouge tres vite
 * sur elle-meme", "elle est en biais par rapport a la trace" - sortent tous deux du meme fait : elle
 * montrait le cap de la BOUSSOLE, donc l'orientation du telephone, et non celle du deplacement.
 */
class HeadingFusionTest {

    // ---------- Qui parle, du GPS ou de la boussole ----------

    /**
     * En mouvement, c'est le DEPLACEMENT qui commande, quelle que soit la boussole.
     *
     * C'est le correctif du "en biais par rapport a la trace" : un telephone sur un guidon ou au fond
     * d'une sacoche pointe ou son support le tient, et l'ecart avec la route est exactement l'angle du
     * support.
     */
    @Test fun `en mouvement le cap vient du GPS`() {
        val cap = HeadingFusion.heading(previous = 10f, compassDeg = 270f, speedMps = 5f, gpsBearingDeg = 90f)
        assertEquals(90f, cap!!, 1e-3f)
    }

    /** Et il s'impose TEL QUEL, sans lissage : il ne tremble pas, et le retarder le mettrait en retard sur
     *  la trace a chaque virage. */
    @Test fun `le cap du GPS n'est pas lisse`() {
        val cap = HeadingFusion.heading(previous = 0f, compassDeg = null, speedMps = 8f, gpsBearingDeg = 180f)
        assertEquals(180f, cap!!, 1e-3f)
    }

    /**
     * A l'arret, le cap du GPS ne veut rien dire : sous quelques km/h, deux mesures successives se
     * contredisent sans qu'on ait bouge d'un metre. La boussole reprend la main.
     */
    @Test fun `a l'arret le cap du GPS est ignore`() {
        assertNull(HeadingFusion.travelBearing(speedMps = 0.3f, gpsBearingDeg = 90f))
        val cap = HeadingFusion.heading(previous = 0f, compassDeg = 90f, speedMps = 0.3f, gpsBearingDeg = 270f)
        assertTrue("la boussole doit avoir parle", cap!! > 0f && cap < 90f)
    }

    /** Une mesure sans cap - une position reseau n'en a pas - laisse aussi la main a la boussole. */
    @Test fun `une mesure sans cap laisse la boussole parler`() {
        assertNull(HeadingFusion.travelBearing(speedMps = 10f, gpsBearingDeg = null))
        assertNull(HeadingFusion.travelBearing(speedMps = null, gpsBearingDeg = 90f))
    }

    /** Ni cap ni boussole : on garde ce qui etait affiche plutot que de pointer au nord par defaut. */
    @Test fun `sans rien, le cap affiche ne bouge pas`() {
        assertEquals(42f, HeadingFusion.heading(42f, null, null, null)!!, 1e-3f)
        assertNull(HeadingFusion.heading(null, null, null, null))
    }

    // ---------- Le lissage de la boussole ----------

    /**
     * C'est le correctif du "elle bouge tres vite sur elle-meme" : la boussole suit l'appareil qu'on
     * manipule, et le magnetometre est perturbe par tout ce qu'un batiment contient de metal.
     */
    @Test fun `la boussole est lissee, non suivie au degre pres`() {
        val cap = HeadingFusion.heading(previous = 0f, compassDeg = 100f, speedMps = 0f, gpsBearingDeg = null)!!
        assertTrue("un saut de 100 degres ne doit pas passer d'un coup : $cap", cap < 30f)
        assertTrue("mais le cap doit avancer vers lui : $cap", cap > 0f)
    }

    /** Le lissage converge : une boussole stable finit par etre atteinte, elle n'est pas amortie a jamais. */
    @Test fun `le lissage finit par rattraper une boussole stable`() {
        var cap: Float? = 0f
        repeat(60) { cap = HeadingFusion.heading(cap, 90f, 0f, null) }
        assertEquals(90f, cap!!, 1f)
    }

    /**
     * **Le passage devant le nord**, et c'est la faute qui se voit le plus : la moyenne naive de 359 et 1
     * donne 180, soit exactement le sens oppose. La fleche faisait un demi-tour complet a chaque fois.
     */
    @Test fun `le lissage passe le nord par le plus court chemin`() {
        val cap = HeadingFusion.smooth(previous = 355f, target = 5f, alpha = 0.5f)
        assertEquals("355 et 5 sont a 10 degres : le milieu est 0, pas 180", 0f, cap, 1e-3f)
    }

    /** Le premier point part de la mesure elle-meme : lisser depuis zero ferait balayer tout le cadran a
     *  l'allumage. */
    @Test fun `le premier cap est pris tel quel`() {
        assertEquals(270f, HeadingFusion.smooth(null, 270f, 0.15f), 1e-3f)
    }

    // ---------- L'arithmetique des angles ----------

    @Test fun `l'ecart signe va dans le bon sens`() {
        assertEquals(10f, HeadingFusion.signedGap(355f, 5f), 1e-3f)
        assertEquals(-10f, HeadingFusion.signedGap(5f, 355f), 1e-3f)
        assertEquals(90f, HeadingFusion.signedGap(0f, 90f), 1e-3f)
    }

    @Test fun `l'ecart absolu prend le plus court chemin`() {
        assertEquals(2f, HeadingFusion.gap(359f, 1f), 1e-3f)
        assertEquals(180f, HeadingFusion.gap(0f, 180f), 1e-3f)
    }

    @Test fun `un cap hors bornes revient dans le cadran`() {
        assertEquals(10f, HeadingFusion.normalize(370f), 1e-3f)
        assertEquals(350f, HeadingFusion.normalize(-10f), 1e-3f)
    }
}
