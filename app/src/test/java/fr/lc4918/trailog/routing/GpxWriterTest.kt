package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Ecriture du GPX qui sort de l'application.
 *
 * C'est le seul format par lequel une trace ressort d'ici, et sa faute type est muette de l'autre cote :
 * un fichier mal forme s'ecrit sans rien lever, et ne se decouvre qu'a l'ouverture, dans une autre
 * application, souvent une fois le telephone range.
 */
class GpxWriterTest {

    private fun gpx(
        name: String = "Sortie",
        points: List<PointFeature> = emptyList(),
        lines: List<List<TrackPoint>> = emptyList(),
    ) = GpxWriter.writeLayer(name, points, lines).toString(Charsets.UTF_8)

    private val deuxPoints = listOf(TrackPoint(6.0, 45.0, 900.0), TrackPoint(6.001, 45.001, 910.0))

    /** Le seul bloc `<wpt>` du document. Les assertions sur les champs d'un waypoint s'y bornent : le
     *  document porte aussi un `<name>`, celui du fichier, qui n'est pas celui du point. */
    private fun wpt(out: String) = out.substringAfter("<wpt").substringBefore("</wpt>")

    // ---------- Traces ----------

    @Test fun `chaque segment de la couche devient un segment de trace`() {
        val out = gpx(lines = listOf(deuxPoints, listOf(TrackPoint(7.0, 46.0))))
        assertEquals("un seul <trk>", 1, Regex("<trk>").findAll(out).count())
        assertEquals("un <trkseg> par segment", 2, Regex("<trkseg>").findAll(out).count())
        assertEquals(3, Regex("<trkpt").findAll(out).count())
    }

    @Test fun `l'altitude et l'horodatage ne s'ecrivent que s'ils existent`() {
        val avec = gpx(lines = listOf(listOf(TrackPoint(6.0, 45.0, 900.0, 1_600_000_000_000))))
        assertTrue(avec, "<ele>900.0</ele>" in avec)
        // Horodatage en ISO 8601 UTC, seule forme que le schema accepte.
        assertTrue(avec, "<time>2020-09-13T12:26:40Z</time>" in avec)
        val sans = gpx(lines = listOf(listOf(TrackPoint(6.0, 45.0))))
        assertTrue(sans, "<ele>" !in sans)
        assertTrue(sans, "<time>" !in sans)
    }

    /** Une couche de marqueurs n'a pas de trace : un `<trk>` vide serait refuse par un lecteur strict. */
    @Test fun `une couche sans trace n'ecrit pas de bloc de trace`() {
        val out = gpx(points = listOf(PointFeature("p0", 6.0, 45.0)))
        assertTrue(out, "<trk>" !in out)
        assertTrue(out, "<wpt" in out)
    }

    // ---------- Waypoints ----------

    @Test fun `un waypoint porte ses champs standard`() {
        val p = PointFeature("p0", 6.0, 45.0, linkedMapOf(
            "name" to PropValue.Text("Refuge"),
            "desc" to PropValue.Text("Eau potable"),
            "sym" to PropValue.Text("Lodging"),
            "ele" to PropValue.Text("2100.5"),
        ))
        val out = gpx(points = listOf(p))
        assertTrue(out, """<wpt lat="45.0" lon="6.0">""" in out)
        assertTrue(out, "<ele>2100.5</ele>" in out)
        assertTrue(out, "<name>Refuge</name>" in out)
        assertTrue(out, "<desc>Eau potable</desc>" in out)
        assertTrue(out, "<sym>Lodging</sym>" in out)
    }

    /**
     * Le schema GPX 1.1 impose l'ordre de ces elements : un lecteur strict refuse le FICHIER ENTIER pour
     * une inversion, et la trace est perdue pour l'utilisateur sans qu'aucun message ne le dise.
     */
    @Test fun `les champs d'un waypoint sortent dans l'ordre qu'impose le schema`() {
        val p = PointFeature("p0", 6.0, 45.0, linkedMapOf(
            // Ordre d'insertion volontairement inverse de celui du schema.
            "type" to PropValue.Text("sommet"),
            "sym" to PropValue.Text("Summit"),
            "desc" to PropValue.Text("Belle vue"),
            "cmt" to PropValue.Text("un commentaire"),
            "name" to PropValue.Text("Pic"),
            "ele" to PropValue.Text("3000"),
        ))
        val bloc = wpt(gpx(points = listOf(p)))
        val ordre = listOf("<ele>", "<name>", "<cmt>", "<desc>", "<sym>", "<type>").map { bloc.indexOf(it) }
        assertTrue("un champ manque : $ordre", ordre.none { it < 0 })
        assertEquals("ordre non conforme au schema : $ordre", ordre.sorted(), ordre)
    }

    /** "description" est le nom que donne le KML a ce que le GPX appelle "desc" : les deux arrivent ici,
     *  un seul doit ressortir - deux `<desc>` dans un waypoint invalident le fichier. */
    @Test fun `desc et description ne donnent qu'une seule balise`() {
        val p = PointFeature("p0", 6.0, 45.0, linkedMapOf(
            "desc" to PropValue.Text("celle du GPX"),
            "description" to PropValue.Text("celle du KML"),
        ))
        val out = gpx(points = listOf(p))
        assertEquals(1, Regex("<desc>").findAll(out).count())
        assertTrue(out, "<desc>celle du GPX</desc>" in out)
    }

    /** Une photo ou un lien n'a pas de place dans les champs standard : il ne doit pas s'y glisser sous
     *  forme de son objet Kotlin, ce qui ecrirait "Image(path=...)" dans le fichier. */
    @Test fun `une image ou un lien ne sort pas dans un champ texte`() {
        val p = PointFeature("p0", 6.0, 45.0, linkedMapOf(
            "name" to PropValue.Image("/photos/1.jpg"),
            "desc" to PropValue.Link("site", "https://exemple.fr"),
        ))
        val bloc = wpt(gpx(points = listOf(p)))
        assertTrue(bloc, "Image(" !in bloc && "Link(" !in bloc)
        assertTrue(bloc, "<name>" !in bloc && "<desc>" !in bloc)
    }

    // ---------- Forme du document ----------

    /** Une esperluette dans un nom de trace suffit a casser le fichier, et les noms sont libres. */
    @Test fun `les caracteres reserves du XML sont echappes`() {
        val out = gpx(name = "Aller & retour <2024>", points = listOf(
            PointFeature("p0", 6.0, 45.0, linkedMapOf("name" to PropValue.Text("""Col "du" <Loup>""")))
        ))
        assertTrue(out, "Aller &amp; retour &lt;2024&gt;" in out)
        assertTrue(out, "Col &quot;du&quot; &lt;Loup&gt;" in out)
    }

    /**
     * Les coordonnees passent par toString(), insensible a la locale : avec format(), une locale francaise
     * ecrirait lat="45,0" et le fichier serait refuse par tout lecteur - a commencer par l'importeur de
     * l'application, qui vient de l'ecrire.
     */
    @Test fun `les coordonnees gardent le point decimal en locale francaise`() {
        val defaut = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val out = gpx(lines = listOf(deuxPoints))
            assertTrue("virgule decimale dans le GPX", "45,0" !in out)
            assertTrue(out, """lat="45.0" lon="6.0"""" in out)
            assertTrue(out, "<ele>900.0</ele>" in out)
        } finally {
            Locale.setDefault(defaut)
        }
    }

    @Test fun `le document porte son entete et son nom`() {
        val out = gpx(name = "Sortie", lines = listOf(deuxPoints))
        assertTrue(out, out.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(out, """<gpx version="1.1" creator="Trailog"""" in out)
        assertTrue(out, "<metadata><name>Sortie</name></metadata>" in out)
        assertTrue(out, out.trimEnd().endsWith("</gpx>"))
    }

    /** Le nom de fichier vient d'un titre libre : une barre oblique y ferait un chemin. */
    @Test fun `le nom de fichier ne garde que des caracteres surs`() {
        assertEquals("Grenoble-Vizille.gpx", GpxWriter.fileName("Grenoble / Vizille"))
        assertEquals("itineraire.gpx", GpxWriter.fileName("///"))
    }
}
