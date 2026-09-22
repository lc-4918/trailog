package fr.lc4918.trailog.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'echelle verticale du profil : ce que la hauteur represente, et ce que l'axe annonce.
 *
 * Une faute ici est silencieuse - un profil faux se lit comme un profil vrai - et elle trompe sur le
 * terrain : une pente de 2 % dessinee comme un mur fait renoncer a une sortie qui ne demandait rien.
 */
class ProfileScaleTest {

    /** Un ecran de telephone : 160 dp au pouce, soit environ 63 px par centimetre a densite 1. */
    private val pxPerCm = 160f / 2.54f
    private val largeur = 1000f
    private val hauteur = 300f

    private fun fenetre(
        v: ProfileScale.Vertical, zMin: Double, zMax: Double, distanceM: Double, h: Float = hauteur,
    ) = ProfileScale.window(v, zMin, zMax, distanceM, largeur, h, pxPerCm)

    // ---------- Ce qui se lit et s'ecrit en base ----------

    @Test fun `le reglage se relit tel qu'il s'ecrit`() {
        listOf(
            ProfileScale.Vertical(ProfileScale.Mode.CAP, 25.0),
            ProfileScale.Vertical(ProfileScale.Mode.M_PER_CM, 100.0),
        ).forEach { assertEquals(it, ProfileScale.parse(ProfileScale.store(it))) }
    }

    /** L'ancienne colonne portait un entier : zero pour "remplir la hauteur", sinon des metres par cm. */
    @Test fun `les anciennes valeurs se relisent`() {
        assertEquals(ProfileScale.Mode.CAP, ProfileScale.parse("0").mode)
        assertEquals(ProfileScale.Vertical(ProfileScale.Mode.M_PER_CM, 150.0), ProfileScale.parse("150"))
        assertEquals("une valeur incomprise ne prive pas de profil",
            ProfileScale.Mode.CAP, ProfileScale.parse("n'importe quoi").mode)
        assertEquals(ProfileScale.Mode.CAP, ProfileScale.parse(null).mode)
    }

    /**
     * L'exageration fixe a ete retiree : elle ne donnait un dessin lisible pour aucune valeur sur la
     * hauteur d'un telephone. Un reglage qui la portait encore se relit comme le plafond par defaut - il
     * ne doit ni priver de profil, ni geler les reglages sur un regime qui n'existe plus.
     */
    @Test fun `un reglage d'exageration se relit comme le plafond par defaut`() {
        assertEquals(
            ProfileScale.Vertical(ProfileScale.Mode.CAP, ProfileScale.DEFAULT_CAP),
            ProfileScale.parse("x:10"),
        )
    }

    // ---------- Les graduations ----------

    @Test fun `le pas de graduation se lit`() {
        assertEquals(100.0, ProfileScale.pasRond(84.0), 0.0)
        assertEquals(200.0, ProfileScale.pasRond(120.0), 0.0)
        assertEquals(250.0, ProfileScale.pasRond(230.0), 0.0)
        assertEquals(500.0, ProfileScale.pasRond(300.0), 0.0)
        assertEquals(1000.0, ProfileScale.pasRond(900.0), 0.0)
        assertEquals(1.0, ProfileScale.pasRond(0.0), 0.0)
    }

    /** L'axe part d'une graduation et s'arrete sur une graduation, la trace entiere entre les deux. */
    @Test fun `l'axe tient sur des graduations rondes`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.CAP, 25.0), 312.0, 1149.0, 20_000.0)
        assertTrue("la trace tient dans la fenetre", w.minZ <= 312.0 && w.maxZ >= 1149.0)
        val pas = w.ticks[1] - w.ticks[0]
        w.ticks.forEach { assertEquals("graduation ronde", 0.0, it % pas, 1e-6) }
        assertEquals("la premiere graduation est le bas de l'axe", w.minZ, w.ticks.first(), 1e-6)
        assertEquals("la derniere est le haut", w.maxZ, w.ticks.last(), 1e-6)
    }

    // ---------- Le plafond d'exageration ----------

    /** Une trace de montagne est deja sous le plafond : elle remplit la hauteur, rien ne la bride. */
    @Test fun `une trace ample garde toute la hauteur`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.CAP, 25.0), 300.0, 2600.0, 40_000.0)
        assertEquals(hauteur, w.heightPx, 0.5f)
        assertTrue("sous le plafond", w.exaggeration!! <= 25.0)
    }

    /**
     * Une longue traversee presque plate : remplir la hauteur dresserait ses 120 m de denivele sur 300 km
     * en muraille. C'est la HAUTEUR qui plie, pas l'axe - et l'axe reste sur le terrain.
     */
    @Test fun `une trace longue et plate reduit la hauteur du graphe`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.CAP, 25.0), 0.0, 120.0, 300_000.0)
        assertTrue("le graphe se reduit", w.heightPx < hauteur)
        assertTrue("mais reste lisible", w.heightPx >= ProfileScale.MIN_CHART_PX)
        assertTrue("l'axe ne s'envole pas", w.maxZ <= ProfileScale.MAX_FRAME * 120.0 + 200.0)
    }

    /** Le cadre ne depasse pas quatre fois l'amplitude : sinon le dessin n'est qu'un trait en bas. */
    @Test fun `le cadre reste proche de l'amplitude`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.CAP, 25.0), 100.0, 130.0, 1_000_000.0)
        assertTrue("au plus quatre fois l'amplitude, au pas de graduation pres",
            w.spanZ <= ProfileScale.MAX_FRAME * 30.0 + ProfileScale.pasRond(30.0))
        assertTrue("et une hauteur qui porte ses graduations", w.heightPx >= ProfileScale.MIN_CHART_PX)
    }

    /** Sans plafond demande (zero), le profil remplit la hauteur quoi qu'il arrive. */
    @Test fun `sans plafond, la hauteur est toujours remplie`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.CAP, 0.0), 0.0, 120.0, 300_000.0)
        assertEquals(hauteur, w.heightPx, 0.5f)
    }

    // ---------- L'echelle absolue ----------

    /** Cent metres par centimetre : la hauteur du graphe donne exactement l'etendue attendue. */
    @Test fun `une echelle absolue donne les metres par centimetre demandes`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.M_PER_CM, 100.0), 500.0, 700.0, 10_000.0)
        val cmH = hauteur / pxPerCm
        assertEquals(100.0, w.spanZ / cmH, 1.0)
        assertTrue("la trace reste dedans", w.minZ <= 500.0 && w.maxZ >= 700.0)
    }

    /** Un plancher, pas un carcan : une trace plus ample que l'echelle ne deborde pas du cadre. */
    @Test fun `une trace trop ample elargit l'echelle`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.M_PER_CM, 50.0), 0.0, 2_000.0, 10_000.0)
        assertTrue("toute la trace est visible", w.maxZ >= 2_000.0)
        assertEquals(hauteur, w.heightPx, 0.5f)
    }

    /** La place en trop va AU-DESSUS : centrer ferait descendre l'axe sous le niveau de la mer. */
    @Test fun `la place en trop se met au-dessus de la trace`() {
        val w = fenetre(ProfileScale.Vertical(ProfileScale.Mode.M_PER_CM, 200.0), 1_200.0, 1_260.0, 5_000.0)
        assertTrue("le bas de l'axe ne descend pas sous la trace de plus d'un pas",
            w.minZ <= 1_200.0 && w.minZ > 0.0)
    }
}
