package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.PropValue
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackPoint
import java.time.Instant

/**
 * Écriture d'un itinéraire calculé en GPX 1.1.
 *
 * Un seul écrivain sert les deux sorties du planificateur : le téléchargement du fichier, et l'import du
 * parcours en couche - l'import de l'application prend des octets de fichier et sait déjà lire le GPX
 * (cf. `LayerImporter`). Passer par le même format évite un second chemin d'entrée dans la base, et
 * garantit qu'un parcours importé est exactement celui qu'on aurait téléchargé.
 *
 * Une trace (`trk`), et non une route (`rte`) : le GPX distingue l'itinéraire à suivre, réduit à ses
 * points de passage, du chemin réellement parcouru, point par point. Ce que rend le moteur est une
 * polyligne complète avec ses altitudes ; c'est une trace, et c'est ce que l'application sait profiler.
 *
 * Sans dépendance Android : vérifiable sans émulateur.
 */
object GpxWriter {

    /**
     * Document GPX d'un parcours, [name] en titre.
     *
     * Les coordonnées et altitudes passent par `toString()`, insensible à la locale : `format()` écrirait
     * une virgule décimale en français, et le fichier serait refusé par tout lecteur de GPX - à commencer
     * par celui de l'application.
     *
     * Aucun horodatage : un itinéraire planifié n'a pas été parcouru, et dater ses points en ferait passer
     * le calcul pour un enregistrement. Le profil s'en accommode, le temps y étant facultatif.
     */
    fun write(name: String, samples: List<Sample>, hasElevation: Boolean): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Trailog" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(escape(name)).append("</name></metadata>\n")
        append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (s in samples) {
            append("""      <trkpt lat="${s.lat}" lon="${s.lon}">""")
            if (hasElevation) append("<ele>${s.z}</ele>")
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }.toByteArray(Charsets.UTF_8)

    /**
     * Document GPX d'une **couche** : ses waypoints, puis ses traces, un segment par ligne.
     *
     * Le même écrivain que pour un parcours calculé, et non un second : ce qui sort de l'application doit
     * être lisible par elle (cf. `LayerImporter`), et deux écrivains finiraient par diverger sur un détail
     * - l'ordre des éléments, l'échappement - qu'aucun test ne rattraperait des deux côtés à la fois.
     *
     * Une couche porte plus que ce que le GPX sait dire. Les champs standard d'un waypoint (nom,
     * commentaire, description, symbole, type, altitude) sont écrits ; **les photos, les liens et les
     * champs libres n'ont pas de place dans le format** et ne sortent pas. C'est la limite du GPX, pas un
     * oubli : le fichier de couche de l'application, lui, les garde tous.
     *
     * L'ordre des éléments n'est pas libre : le schéma GPX 1.1 impose `ele` puis `time`, puis le nom, le
     * commentaire, la description, le symbole et le type. Un lecteur strict refuse le fichier entier pour
     * une inversion.
     */
    fun writeLayer(
        name: String,
        points: List<PointFeature>,
        lines: List<List<TrackPoint>>,
    ): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Trailog" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(escape(name)).append("</name></metadata>\n")
        points.forEach { p ->
            append("""  <wpt lat="${p.lat}" lon="${p.lon}">""").append('\n')
            text(p, "ele")?.toDoubleOrNull()?.let { append("    <ele>").append(it).append("</ele>\n") }
            WPT_TAGS.forEach { (tag, keys) ->
                // Une balise, et les noms sous lesquels la propriété peut arriver : "description" est le
                // nom que donne le KML à ce que le GPX appelle "desc". Le premier trouvé l'emporte.
                val v = keys.firstNotNullOfOrNull { text(p, it) } ?: return@forEach
                append("    <").append(tag).append('>').append(escape(v)).append("</").append(tag).append(">\n")
            }
            append("  </wpt>\n")
        }
        if (lines.isNotEmpty()) {
            append("  <trk>\n    <name>").append(escape(name)).append("</name>\n")
            lines.forEach { seg ->
                append("    <trkseg>\n")
                seg.forEach { pt ->
                    append("""      <trkpt lat="${pt.lat}" lon="${pt.lon}">""")
                    pt.ele?.let { append("<ele>").append(it).append("</ele>") }
                    pt.timeMs?.let { append("<time>").append(Instant.ofEpochMilli(it)).append("</time>") }
                    append("</trkpt>\n")
                }
                append("    </trkseg>\n")
            }
            append("  </trk>\n")
        }
        append("</gpx>\n")
    }.toByteArray(Charsets.UTF_8)

    /** Les champs standard d'un waypoint, **dans l'ordre qu'impose le schéma**, et les propriétés d'où ils
     *  sortent. `ele` est écrit avant eux, séparément : c'est le seul qui soit un nombre. */
    private val WPT_TAGS = listOf(
        "name" to listOf("name"),
        "cmt" to listOf("cmt"),
        "desc" to listOf("desc", "description"),
        "sym" to listOf("sym"),
        "type" to listOf("type"),
    )

    /** La valeur texte d'une propriété de waypoint, ou null : une image ou un lien n'a pas de place dans
     *  les champs standard du GPX. */
    private fun text(p: PointFeature, key: String): String? =
        (p.props[key] as? PropValue.Text)?.value?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Nom de fichier tiré du titre : tout ce qui n'est ni lettre, ni chiffre, ni tiret devient un tiret.
     *
     * Le titre est libre, et l'utilisateur y met volontiers une barre oblique ("Grenoble / Vizille") ou
     * deux-points, qu'un système de fichiers refuse ou interprète comme un chemin.
     */
    fun fileName(name: String, extension: String = "gpx"): String {
        val base = name.trim().map { if (it.isLetterOrDigit() || it == '-') it else '-' }
            .joinToString("").trim('-').replace(Regex("-{2,}"), "-")
        return (base.ifBlank { "itineraire" }) + "." + extension
    }

    /** Les cinq entités XML. Le titre vient d'une saisie libre, une esperluette y suffit à casser le fichier. */
    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
