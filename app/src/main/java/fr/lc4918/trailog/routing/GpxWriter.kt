package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.Sample

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
     * Nom de fichier tiré du titre : tout ce qui n'est ni lettre, ni chiffre, ni tiret devient un tiret.
     *
     * Le titre est libre, et l'utilisateur y met volontiers une barre oblique ("Grenoble / Vizille") ou
     * deux-points, qu'un système de fichiers refuse ou interprète comme un chemin.
     */
    fun fileName(name: String): String {
        val base = name.trim().map { if (it.isLetterOrDigit() || it == '-') it else '-' }
            .joinToString("").trim('-').replace(Regex("-{2,}"), "-")
        return (base.ifBlank { "itineraire" }) + ".gpx"
    }

    /** Les cinq entités XML. Le titre vient d'une saisie libre, une esperluette y suffit à casser le fichier. */
    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
