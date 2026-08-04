package fr.lc4918.trailog.data.seed

import java.io.File

/**
 * Jeu de demonstration livre avec l'app : un dossier contenant une couche de traces et de waypoints
 * photographies, pose au tout premier lancement pour qu'une installation neuve ne s'ouvre pas sur une
 * carte vide.
 *
 * Les fichiers vivent dans les assets, sous [ASSET_DIR] : un .gpx et les photos qu'il cite, a plat.
 *
 * Le semis passe par l'import normal (TrailogRepository.importLayer) plutot que par des INSERT : la
 * couche de demonstration est alors en tout point une couche importee -- meme GeoJSON precalcule, meme
 * profil, memes bornes, memes photos recopiees dans le stockage prive. Elle se supprime, se renomme et
 * se deplace comme les autres, et rien dans le rendu n'a besoin de savoir qu'elle vient de la.
 *
 * Le prix a payer est que les photos doivent exister sur le disque au moment de l'import : l'import ne
 * lit pas les assets. Elles sont donc extraites dans un dossier temporaire, dont le chemin absolu est
 * injecte dans le GPX (cf. [rewritePhotoPaths]), puis efface une fois l'import termine.
 */
object DemoData {
    /** Dossier des assets ou deposer le .gpx et ses photos, a plat. */
    const val ASSET_DIR = "demo"

    /**
     * Reecrit les chemins de photos du GPX pour les faire pointer vers [photoDir].
     *
     * Ne touche qu'aux `<photo>` dont le contenu est un nom de fichier nu : un chemin deja absolu ou une
     * URL distante est laisse tel quel, l'import sait les traiter. Les `<link href>` ne sont pas
     * concernes -- l'import les rejette quand ils sont relatifs (cf. LayerImporter.isResolvablePhoto),
     * c'est bien `<photo>` qu'il faut employer dans le GPX de demonstration.
     *
     * Pur et separe du reste pour etre verifiable sans Android ni fichiers.
     */
    fun rewritePhotoPaths(gpx: String, photoDir: File): String =
        PHOTO_TAG.replace(gpx) { m ->
            val name = m.groupValues[1].trim()
            if (name.isEmpty() || isAbsoluteOrRemote(name)) m.value
            else "<photo>${File(photoDir, name).absolutePath}</photo>"
        }

    private val PHOTO_TAG = Regex("""<photo>([^<]*)</photo>""", RegexOption.IGNORE_CASE)

    private fun isAbsoluteOrRemote(p: String): Boolean =
        p.startsWith("/") || p.startsWith("file://") || p.startsWith("http://") || p.startsWith("https://")

    /** Noms de fichiers photo cites par le GPX, dans l'ordre, sans doublon : ce sont eux qu'il faut
     *  extraire des assets. Les chemins absolus et les URL en sont exclus, ils n'ont rien a extraire. */
    fun photoNames(gpx: String): List<String> =
        PHOTO_TAG.findAll(gpx).map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !isAbsoluteOrRemote(it) }
            .distinct().toList()
}
