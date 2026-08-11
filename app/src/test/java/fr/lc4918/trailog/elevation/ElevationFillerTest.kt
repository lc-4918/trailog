package fr.lc4918.trailog.elevation

import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.TrackPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Completement des altitudes manquantes : qui est interroge, dans quel ordre, et ce qui est ecrit au bout.
 *
 * Les services sont remplaces par des reponses ecrites ici : ce qu'on verifie n'est pas qu'ils repondent -
 * ils repondent - mais les regles que l'application leur applique. Deux d'entre elles ne se voient pas a
 * l'usage et se paient cher : le tout ou rien d'une trace (un profil a trous plonge au niveau de la mer,
 * cf. TrackMath.compute) et l'abandon des le premier paquet quand l'IGN ne connait pas la region (sinon,
 * une trace etrangere lui coute une requete par tranche de deux cents points, toutes vides).
 */
class ElevationFillerTest {

    private val services = ElevationServices()

    /** Un modele de terrain plat a 1234 m sur toute la Terre : la grille la plus courte qui reponde partout. */
    private val flatWorld = """
        ncols 2
        nrows 2
        xllcorner -180.0
        yllcorner -90.0
        cellsize 180.0
        NODATA_value -32768
        1234 1234
        1234 1234
    """.trimIndent()

    /** Reponses jouees selon l'URL demandee, et journal des appels. */
    private class Fake(val reply: (String) -> ElevationResponse) {
        val calls = ArrayList<String>()
        val fetcher: ElevationFetcher = { url -> calls.add(url); reply(url) }
        fun ign() = calls.count { "geopf" in it || "elevation.json" in it }
        fun grids() = calls.count { "globaldem" in it }
        fun points() = calls.count { "v1/elevation" in it }
    }

    private fun ok(body: String) = ElevationResponse(200, body)

    /** Reponse IGN a [n] points, tous a [z] metres ; -99999 pour dire "hors de ma couverture". */
    private fun ignBody(n: Int, z: Double) = """{"elevations": [${List(n) { z }.joinToString(", ")}]}"""

    private fun countPoints(url: String) = url.substringAfter("lon=").substringBefore("&").split("|").size

    private fun line(vararg lonlat: Pair<Double, Double>, ele: Double? = null) =
        lonlat.map { (lon, lat) -> TrackPoint(lon, lat, ele) }

    // ---------- Ce qui est interroge ----------

    @Test fun `une trace francaise se complete par l'IGN, sans toucher au service mondial`() = runTest {
        val fake = Fake { url -> ok(ignBody(countPoints(url), 1500.0)) }
        val track = line(6.0 to 45.0, 6.001 to 45.001, 6.002 to 45.002)
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(1, fake.ign())
        assertEquals(0, fake.grids() + fake.points())
        assertEquals(listOf(1500.0, 1500.0, 1500.0), out.lines[0].map { it.ele })
    }

    @Test fun `les points deja pourvus d'une altitude ne sont pas redemandes`() = runTest {
        val fake = Fake { url -> ok(ignBody(countPoints(url), 1500.0)) }
        val track = listOf(
            TrackPoint(6.0, 45.0, 900.0), TrackPoint(6.001, 45.001), TrackPoint(6.002, 45.002, 910.0),
        )
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(1, countPoints(fake.calls[0]))
        assertEquals(listOf(900.0, 1500.0, 910.0), out.lines[0].map { it.ele })
    }

    @Test fun `une trace deja complete ne declenche aucune requete`() = runTest {
        val fake = Fake { error("aucune requete attendue") }
        val track = line(6.0 to 45.0, 6.001 to 45.001, ele = 900.0)
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(0, fake.calls.size)
        assertEquals(listOf(900.0, 900.0), out.lines[0].map { it.ele })
    }

    /** Deux cents points par requete : une trace de 450 points en demande trois, pas trois cents. */
    @Test fun `les points partent par paquets de deux cents`() = runTest {
        val fake = Fake { url -> ok(ignBody(countPoints(url), 1500.0)) }
        val track = (0 until 450).map { TrackPoint(6.0 + it * 1e-4, 45.0) }
        ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(3, fake.ign())
        assertEquals(listOf(200, 200, 50), fake.calls.map { countPoints(it) })
    }

    /**
     * Le cout de l'IGN suit le nombre de points, celui des grilles la surface : passe douze mille points,
     * une trace dense revient moins cher en grilles qu'en paquets de deux cents. On y perd la finesse du
     * modele francais, pas le profil.
     */
    @Test fun `une trace tres dense passe par les grilles plutot que par trois cents paquets`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url) ok(flatWorld) else ok(ignBody(countPoints(url), 1500.0))
        }
        val track = (0 until 12_001).map { TrackPoint(6.0 + it * 1e-7, 45.0) }
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(0, fake.ign())
        assertEquals(1, fake.grids())
        assertTrue(out.lines[0].all { it.ele == 1234.0 })
    }

    /**
     * Ce que l'import interroge avant d'annoncer "calcul de l'altimetrie" a l'ecran : un fichier deja
     * pourvu ne doit pas faire clignoter le libelle pour un travail qui n'aura pas lieu.
     */
    @Test fun `un fichier deja pourvu n'a aucun trou a combler`() {
        val complete = line(6.0 to 45.0, 6.001 to 45.001, ele = 900.0)
        val wptPourvu = PointFeature("p0", 6.0, 45.0, linkedMapOf("ele" to PropValue.Text("902.0")))
        assertTrue(!ElevationFiller.hasHoles(listOf(wptPourvu), listOf(complete)))
        assertTrue(!ElevationFiller.hasHoles(emptyList(), emptyList()))
        // Un seul point de trace sans Z, ou un seul waypoint sans altitude, suffit a ouvrir le calcul.
        assertTrue(ElevationFiller.hasHoles(emptyList(), listOf(complete + TrackPoint(6.002, 45.002))))
        assertTrue(ElevationFiller.hasHoles(listOf(PointFeature("p1", 6.0, 45.0)), listOf(complete)))
        // Une propriete d'altitude vide vaut une absence : c'est ce qu'ecrivent certains exports.
        val wptVide = PointFeature("p2", 6.0, 45.0, linkedMapOf("ele" to PropValue.Text("  ")))
        assertTrue(ElevationFiller.hasHoles(listOf(wptVide), emptyList()))
    }

    // ---------- Le partage France / monde ----------

    @Test fun `hors couverture IGN, la trace passe par une grille du modele mondial`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url) ok(flatWorld) else ok(ignBody(countPoints(url), -99999.0))
        }
        val track = line(8.0 to 46.5, 8.001 to 46.501, 8.002 to 46.502)
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(1, fake.grids())
        assertEquals(listOf(1234.0, 1234.0, 1234.0), out.lines[0].map { it.ele })
    }

    /** L'IGN ne connaissant pas ses propres frontieres autrement qu'en repondant "pas de donnee", on cesse
     *  de l'interroger des le premier paquet vide. Sans cette regle, une trace etrangere de mille points
     *  lui couterait cinq requetes pour rien. */
    @Test fun `une trace entierement etrangere ne coute qu'un seul appel a l'IGN`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url) ok(flatWorld) else ok(ignBody(countPoints(url), -99999.0))
        }
        val track = (0 until 500).map { TrackPoint(8.0 + it * 1e-5, 46.5) }
        ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(1, fake.ign())
    }

    /** Ce que l'IGN a servi n'est pas redemande au service mondial : seul le reste y va. */
    @Test fun `un fichier a cheval sur la frontiere ne demande au monde que ce qui manque`() = runTest {
        var first = true
        val fake = Fake { url ->
            when {
                "v1/elevation" in url -> ok("""{"Status": "Success", "Elevation": 1234.0}""")
                "globaldem" in url -> ok(flatWorld)
                else -> {
                    // Premier paquet : le premier point est servi, le second non.
                    first = false
                    ok("""{"elevations": [1500.0, -99999.0]}""")
                }
            }
        }
        val track = line(6.0 to 45.0, 8.0 to 46.5)
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertTrue("l'IGN a ete interroge une fois", fake.ign() == 1 && !first)
        assertEquals(1, fake.grids() + fake.points())
        assertEquals(listOf(1500.0, 1234.0), out.lines[0].map { it.ele })
    }

    // ---------- Le tout ou rien d'une trace ----------

    /** Un profil auquel il manque un point ne descend pas jusqu'a la mer a cet endroit : la trace garde
     *  l'absence d'altitude qu'elle avait, et n'affiche pas de profil du tout. */
    @Test fun `une trace dont un point manque reste sans altitude`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url) ElevationResponse(500, null)
            else ok("""{"elevations": [1500.0, 1510.0, -99999.0]}""")
        }
        val track = line(6.0 to 45.0, 6.001 to 45.001, 6.002 to 45.002)
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(listOf(null, null, null), out.lines[0].map { it.ele })
    }

    /** Les traces sont independantes entre elles : celle qui est servie garde son altitude. */
    @Test fun `une trace incomplete n'entraine pas les autres`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url) ElevationResponse(500, null)
            else ok("""{"elevations": [1500.0, 1510.0, -99999.0]}""")
        }
        val bonne = line(6.0 to 45.0, 6.001 to 45.001)
        val mauvaise = line(6.002 to 45.002)
        val out = ElevationFiller.fill(emptyList(), listOf(bonne, mauvaise), services, fake.fetcher)
        assertEquals(listOf(1500.0, 1510.0), out.lines[0].map { it.ele })
        assertEquals(listOf(null), out.lines[1].map { it.ele })
    }

    // ---------- Waypoints ----------

    @Test fun `un waypoint recoit son altitude en propriete`() = runTest {
        val fake = Fake { url -> ok(ignBody(countPoints(url), 1512.34)) }
        val wpt = PointFeature("p0", 6.0, 45.0, linkedMapOf("name" to PropValue.Text("Refuge")))
        val out = ElevationFiller.fill(listOf(wpt), emptyList(), services, fake.fetcher)
        assertEquals(PropValue.Text("1512.3"), out.points[0].props[ElevationFiller.ELE_PROP])
        // Les proprietes du fichier restent en place, et dans leur ordre.
        assertEquals(listOf("name", "ele"), out.points[0].props.keys.toList())
    }

    /** Un waypoint qui porte deja une altitude n'est pas retouche : c'est la mesure du fichier importe. */
    @Test fun `un waypoint deja pourvu garde son altitude`() = runTest {
        val fake = Fake { error("aucune requete attendue") }
        val wpt = PointFeature("p0", 6.0, 45.0, linkedMapOf("ele" to PropValue.Text("902.0")))
        val out = ElevationFiller.fill(listOf(wpt), emptyList(), services, fake.fetcher)
        assertEquals(0, fake.calls.size)
        assertEquals(PropValue.Text("902.0"), out.points[0].props["ele"])
    }

    /** Un point isole passe par l'API du point : une grille pour une seule altitude serait mille fois trop
     *  lourde. Un groupe, lui, tient dans une seule grille. */
    @Test fun `un waypoint etranger isole passe par l'API du point`() = runTest {
        val fake = Fake { url ->
            when {
                "v1/elevation" in url -> ok("""{"Status": "Success", "Elevation": 1600.5}""")
                "globaldem" in url -> ok(flatWorld)
                else -> ok(ignBody(countPoints(url), -99999.0))
            }
        }
        val wpt = PointFeature("p0", 8.0, 46.5)
        val out = ElevationFiller.fill(listOf(wpt), emptyList(), services, fake.fetcher)
        assertEquals(1, fake.points())
        assertEquals(0, fake.grids())
        assertEquals(PropValue.Text("1600.5"), out.points[0].props[ElevationFiller.ELE_PROP])
    }

    /** Un waypoint sans altitude n'empeche pas les autres d'en recevoir une : contrairement aux points
     *  d'une trace, ils ne forment pas un profil. */
    @Test fun `un waypoint sans donnee laisse les autres se completer`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url || "v1/elevation" in url) ElevationResponse(404, null)
            else ok("""{"elevations": [1500.0, -99999.0]}""")
        }
        val out = ElevationFiller.fill(
            listOf(PointFeature("p0", 6.0, 45.0), PointFeature("p1", 8.0, 46.5)),
            emptyList(), services, fake.fetcher,
        )
        assertEquals(PropValue.Text("1500.0"), out.points[0].props[ElevationFiller.ELE_PROP])
        assertNull(out.points[1].props[ElevationFiller.ELE_PROP])
    }

    // ---------- Reseau absent ----------

    /** Sans reseau, on ne joue pas la suite : rien ne servirait d'aligner les memes echecs, et l'import
     *  attendrait le delai de chaque requete. */
    @Test fun `sans reseau, la couche s'importe telle quelle apres un seul essai`() = runTest {
        val fake = Fake { ElevationResponse(0, null) }
        val track = (0 until 500).map { TrackPoint(6.0 + it * 1e-5, 45.0) }
        val out = ElevationFiller.fill(
            listOf(PointFeature("p0", 6.0, 45.0)), listOf(track), services, fake.fetcher,
        )
        assertEquals(1, fake.calls.size)
        assertTrue(out.lines[0].all { it.ele == null })
        assertNull(out.points[0].props[ElevationFiller.ELE_PROP])
    }

    // ---------- Decoupage en emprises ----------

    @Test fun `les points voisins tiennent dans une seule emprise`() {
        val pts = (0 until 100).map { LonLat(6.0 + it * 1e-4, 45.0) }
        assertEquals(listOf(0 until 100), ElevationFiller.chunks(pts, 0.05))
    }

    @Test fun `une trace qui s'eloigne ouvre une emprise de plus`() {
        val pts = listOf(LonLat(6.0, 45.0), LonLat(6.02, 45.0), LonLat(6.2, 45.0), LonLat(6.21, 45.0))
        assertEquals(listOf(0 until 2, 2 until 4), ElevationFiller.chunks(pts, 0.05))
    }

    @Test fun `l'eloignement se mesure aussi en latitude`() {
        val pts = listOf(LonLat(6.0, 45.0), LonLat(6.0, 45.9))
        assertEquals(listOf(0 until 1, 1 until 2), ElevationFiller.chunks(pts, 0.05))
    }

    @Test fun `une liste vide ne donne aucune emprise`() {
        assertEquals(emptyList<IntRange>(), ElevationFiller.chunks(emptyList(), 0.05))
    }

    /**
     * Au-dela de ce que le decoupage grossier sait couvrir, on renonce AVANT la premiere requete : une
     * trace de mille kilometres epuiserait le quota de la cle pour un profil que la regle du tout ou rien
     * refuserait au bout.
     */
    @Test fun `une trace trop longue pour le monde ne coute aucune requete de grille`() = runTest {
        val fake = Fake { url ->
            if ("globaldem" in url) ok(flatWorld) else ok(ignBody(countPoints(url), -99999.0))
        }
        // Dix degres de longitude, soit bien plus que ce que couvrent 24 emprises de 0,15 degre.
        val track = (0 until 200).map { TrackPoint(8.0 + it * 0.05, 46.5) }
        val out = ElevationFiller.fill(emptyList(), listOf(track), services, fake.fetcher)
        assertEquals(0, fake.grids())
        assertTrue(out.lines[0].all { it.ele == null })
    }
}
