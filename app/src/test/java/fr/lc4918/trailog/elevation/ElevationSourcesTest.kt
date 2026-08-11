package fr.lc4918.trailog.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Requetes et reponses des deux services altimetriques.
 *
 * Memes raisons de les verrouiller que pour le geocodeur et le moteur d'itineraire : une URL mal formee ou
 * une reponse mal lue ne leve rien. La couche s'importe alors sans altitude, ce qui est exactement ce qui
 * arrive quand le fichier n'en portait pas - la faute serait invisible.
 */
class ElevationSourcesTest {

    private val alpe = LonLat(6.0833, 45.1833)

    // ---------- IGN ----------

    @Test fun `la requete IGN aligne les points dans l'ordre, lon d'un cote lat de l'autre`() {
        val u = IgnElevation.url(IgnElevation.DEFAULT_URL, listOf(alpe, LonLat(2.5, 48.8)))
        assertTrue(u, "lon=6.083300|2.500000" in u)
        assertTrue(u, "lat=45.183300|48.800000" in u)
        assertTrue(u, "delimiter=|" in u)
        assertTrue(u, "zonly=true" in u)
    }

    /** Sans ressource nommee, le service repond 405 : ce n'est pas un parametre facultatif. */
    @Test fun `la requete IGN nomme sa ressource`() {
        assertTrue("resource=ign_rge_alti_wld" in IgnElevation.url(IgnElevation.DEFAULT_URL, listOf(alpe)))
    }

    @Test fun `la requete IGN garde le point decimal en locale francaise`() {
        val defaut = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val u = IgnElevation.url(IgnElevation.DEFAULT_URL, listOf(alpe))
            assertTrue("virgule decimale dans l'URL", "6,08" !in u)
            assertTrue(u, "lon=6.083300" in u)
        } finally {
            Locale.setDefault(defaut)
        }
    }

    /** Une base deja porteuse d'une chaine de requete (instance derriere un proxy) garde la sienne. */
    @Test fun `la requete IGN se colle a une base deja parametree`() {
        val u = IgnElevation.url("https://exemple.fr/alti?token=abc", listOf(alpe))
        assertTrue(u, "token=abc&resource=" in u)
    }

    @Test fun `la reponse IGN rend une altitude par point demande`() {
        val z = IgnElevation.parse("""{"elevations": [2256.6, 1024.0]}""", 2)
        assertEquals(listOf(2256.6, 1024.0), z)
    }

    /** Hors couverture, le service ne repond pas une erreur mais cette valeur : c'est elle qui envoie le
     *  point vers le modele mondial, sans qu'aucune frontiere soit codee dans l'application. */
    @Test fun `hors couverture, l'IGN rend un trou et non une altitude`() {
        val z = IgnElevation.parse("""{"elevations": [-99999.0, 1024.0]}""", 2)
        assertEquals(listOf(null, 1024.0), z)
    }

    /** Une reponse dont on ne sait plus a quel point chaque valeur se rapporte poserait des altitudes sur
     *  les mauvais points : elle est rejetee en bloc. */
    @Test fun `une reponse IGN de taille inattendue est refusee en entier`() {
        assertNull(IgnElevation.parse("""{"elevations": [2256.6]}""", 2))
        assertNull(IgnElevation.parse("""<html>Service indisponible</html>""", 1))
    }

    // ---------- OpenTopography ----------

    /**
     * Le modele mondial est le Copernicus GLO-90, et il est SEUL : complet la ou le SRTM a des trous dans
     * les reliefs escarpes - un trou coute la trace entiere -, et au pas de 90 m la ou le GLO-30 pese neuf
     * fois plus pour la meme emprise.
     *
     * Le pas est verrouille avec le nom : c'est lui qui place les altitudes dans la grille recue, et une
     * grille lue au pas d'un autre modele les poserait a cote sans rien lever.
     */
    @Test fun `le modele mondial est le Copernicus GLO-90, au pas de trois secondes d'arc`() {
        assertEquals("COP90", OpenTopo.DEM.grid)
        assertEquals("COP90", OpenTopo.DEM.point)
        assertEquals(3.0 / 3600.0, OpenTopo.DEM.cellDeg, 1e-12)
    }

    @Test fun `la requete d'un point porte la cle et le modele sous le nom de son API`() {
        val u = OpenTopo.pointUrl(OpenTopo.DEFAULT_URL, "K1", alpe, OpenTopo.DEM)
        assertTrue(u, "longitude=6.083300" in u)
        assertTrue(u, "latitude=45.183300" in u)
        // Chaque modele porte les deux noms que lui donnent les deux API : le Copernicus s'ecrit pareil des
        // deux cotes, le SRTM non (SRTM_GL1 cote point, SRTMGL1 cote grille). Sans ce couple, un waypoint et
        // la trace qui le traverse sortiraient de deux modeles differents - ou d'une requete refusee.
        assertTrue(u, "dataset=COP90" in u)
        assertTrue(u, "API_Key=K1" in u)
    }

    @Test fun `la requete de grille porte l'emprise et le format texte`() {
        val u = OpenTopo.gridUrl(OpenTopo.DEFAULT_URL, "K1", Bbox(6.0, 45.0, 6.1, 45.1), OpenTopo.DEM)
        assertTrue(u, "demtype=COP90" in u)
        assertTrue(u, "south=45.000000" in u)
        assertTrue(u, "north=45.100000" in u)
        assertTrue(u, "west=6.000000" in u)
        assertTrue(u, "east=6.100000" in u)
        assertTrue(u, "outputFormat=AAIGrid" in u)
    }

    @Test fun `la reponse d'un point rend son altitude`() {
        assertEquals(
            2940.229,
            OpenTopo.parsePoint("""{"Status": "Success", "Elevation": 2940.229, "Unit": "Meters"}""")!!,
            1e-6,
        )
    }

    /** En mer, le service repond 404 avec une altitude nulle : rien a distinguer d'une reponse illisible,
     *  l'appelant n'en fait rien de different. */
    @Test fun `un point sans donnee ne rend pas d'altitude`() {
        assertNull(OpenTopo.parsePoint("""{"Status": "Not Found", "Elevation": null}"""))
        assertNull(OpenTopo.parsePoint("<html>403</html>"))
    }

    // ---------- Emprises ----------

    @Test fun `l'emprise d'une liste de points les contient tous`() {
        val b = Bbox.of(listOf(LonLat(6.0, 45.0), LonLat(5.5, 45.5), LonLat(6.5, 44.5)))!!
        assertEquals(5.5, b.west, 1e-9)
        assertEquals(6.5, b.east, 1e-9)
        assertEquals(44.5, b.south, 1e-9)
        assertEquals(45.5, b.north, 1e-9)
        assertNull(Bbox.of(emptyList()))
    }

    /** Le service refuse les emprises de moins de 250 m de cote : celle d'un point isole doit donc etre
     *  elargie, et plus largement en longitude que en latitude des qu'on s'eloigne de l'equateur. */
    @Test fun `l'emprise d'un point isole est elargie au minimum du service`() {
        val b = Bbox.of(listOf(LonLat(6.0, 45.0)))!!.padded(400.0, OpenTopo.DEM.cellDeg)
        val meters = 111_320.0
        assertTrue("hauteur ${b.heightDeg * meters} m", b.heightDeg * meters >= 400.0)
        val widthM = b.widthDeg * meters * Math.cos(Math.toRadians(45.0))
        assertTrue("largeur $widthM m", widthM >= 400.0)
        assertTrue("le point reste dedans", b.west < 6.0 && b.east > 6.0 && b.south < 45.0 && b.north > 45.0)
    }

    /** Une emprise deja large ne grandit que de la marge d'interpolation : un point pose sur son bord n'a
     *  pas de voisin au-dela, et [AsciiGrid.sample] ne rendrait rien. */
    @Test fun `une emprise large ne gagne que sa marge d'interpolation`() {
        val box = Bbox(6.0, 45.0, 6.05, 45.05)
        val b = box.padded(400.0, OpenTopo.DEM.cellDeg)
        assertTrue(b.west < box.west && b.east > box.east)
        assertEquals(box.widthDeg + 4 * OpenTopo.DEM.cellDeg, b.widthDeg, 1e-9)
    }
}
