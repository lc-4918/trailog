package fr.lc4918.trailog.map

import android.graphics.BitmapFactory
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.TileHttp
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.map.offline.TileUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Décide si un fond national a quelque chose à montrer là où la carte regarde.
 *
 * Deux signaux, parce qu'aucun ne suffit seul :
 *  - l'emprise déclarée ([CoverageBounds]) tranche le cas courant -- activer le fond polonais depuis
 *    la France -- sans réseau ni délai, et sans jamais se tromper ;
 *  - une tuile réellement demandée au service tranche le reste, l'emprise étant un rectangle alors
 *    que la couverture ne l'est pas : le service portugais s'arrête à la frontière et rend du blanc
 *    sur l'Estrémadure espagnole, pourtant dans son rectangle.
 *
 * La sonde ne conclut à l'absence que sur une réponse explicite du serveur (404/204, ou une image
 * uniformément blanche ou transparente). Une panne réseau, un délai dépassé, un 403 de quota
 * laissent [Coverage.UNKNOWN] : mieux vaut ne pas recadrer que déplacer la carte de l'utilisateur
 * parce que son train est passé sous un tunnel.
 *
 * Le blanc est exigé, et non "une couleur uniforme" : une tuile entièrement dans un lac ou en mer
 * est uniformément bleue, et recadrer là serait un contresens.
 */
object CoverageProbe {

    enum class Coverage { COVERED, EMPTY, UNKNOWN }

    /** Sous ce seuil de canal, un gris clair est encore du "pas de donnée" : les services rendent
     *  souvent un blanc cassé plutôt qu'un #FFFFFF exact (compression JPEG, fond de page du serveur). */
    private const val WHITE_MIN = 0xF0

    /** Sonde brève : la carte est déjà à l'écran, un appelant qui attend ne sert à rien. */
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000

    /**
     * Le fond couvre-t-il [viewport] ? [zoom] est celui de la caméra, ramené dans la plage servie par
     * le fond : hors plage, le service répond une erreur qui ne dit rien de sa couverture (l'OS
     * britannique renvoie 400 sous le zoom 7, partout, y compris en plein Pays de Galles).
     */
    suspend fun probe(provider: ProviderEntity, viewport: Bbox, zoom: Int): Coverage {
        val bounds = CoverageBounds.of(provider) ?: return Coverage.UNKNOWN
        if (!CoverageBounds.intersects(bounds, viewport)) return Coverage.EMPTY

        val lon = (viewport.west + viewport.east) / 2
        val lat = (viewport.south + viewport.north) / 2
        val z = zoom.coerceIn(provider.minZoom, provider.maxZoom)
        val (x, y) = TileMath.tileAt(lon, lat, z)
        val url = TileUrl.build(provider, x, y, z)
        val res = withContext(Dispatchers.IO) {
            runCatching { TileHttp.fetch(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS) }.getOrNull()
        } ?: return Coverage.UNKNOWN
        return classify(res.status, res.body)
    }

    /** Verdict sur une réponse de tuile. Séparé de [probe] pour être vérifiable sans réseau. */
    fun classify(status: Int, body: ByteArray?): Coverage = when {
        status == 404 || status == 204 -> Coverage.EMPTY   // le serveur dit qu'il n'y a rien ici
        status !in 200..299 -> Coverage.UNKNOWN            // 0 (réseau), 403 (quota), 5xx : sans avis
        body == null || body.isEmpty() -> Coverage.UNKNOWN
        else -> pixelsOf(body)?.let { if (isBlank(it)) Coverage.EMPTY else Coverage.COVERED }
            ?: Coverage.UNKNOWN                            // corps illisible (page d'erreur HTML)
    }

    /** Échantillonne l'image sur une grille, plutôt que de lire tous les pixels : une tuile vide l'est
     *  de bout en bout, et 256x256 lectures par sonde ne diraient rien de plus. Null si non décodable. */
    private fun pixelsOf(body: ByteArray): IntArray? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(body, 0, body.size) ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        val step = 16
        val out = IntArray(step * step)
        for (i in 0 until step) for (j in 0 until step) {
            val px = (bmp.width - 1) * i / (step - 1)
            val py = (bmp.height - 1) * j / (step - 1)
            out[i * step + j] = bmp.getPixel(px, py)
        }
        bmp.recycle()
        out
    }.getOrNull()

    /**
     * L'image ne porte-t-elle aucune donnée ? Vrai si tout est transparent (surcouche sans objet ici)
     * ou si tout est d'un même blanc (le "hors zone" des services WMS, qui ne savent pas répondre
     * "rien" autrement qu'en peignant la tuile en blanc).
     *
     * Pur et public : c'est la seule règle de la sonde qui puisse se tromper au détriment de
     * l'utilisateur, elle mérite d'être éprouvée directement.
     */
    fun isBlank(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        if (pixels.all { (it ushr 24 and 0xFF) == 0 }) return true
        val first = pixels[0]
        if (pixels.any { it != first }) return false
        val r = first ushr 16 and 0xFF
        val g = first ushr 8 and 0xFF
        val b = first and 0xFF
        // Uniforme ET blanc : un bleu de pleine mer ou un vert de forêt est une donnée, pas un vide.
        return r >= WHITE_MIN && g >= WHITE_MIN && b >= WHITE_MIN &&
            abs(r - g) <= 8 && abs(g - b) <= 8
    }
}
