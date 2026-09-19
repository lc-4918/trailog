package fr.lc4918.trailog.routing

import android.content.Context
import btools.router.FormatJson
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.routing.offline.BrouterStorage
import fr.lc4918.trailog.routing.offline.BrouterTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File

/**
 * BRouter **sur le telephone** : le meme moteur que brouter.de, le meme profil, sans reseau.
 *
 * Le moteur est le jar "routage seul" de la version officielle (cf. `app/libs`). Il lit ses donnees dans des
 * fichiers `.rd5`, un par carre de cinq degres, que le serveur public regenere chaque semaine
 * (https://brouter.de/brouter/segments4/). Il rend sa reponse dans le format GeoJSON du serveur -
 * [FormatJson] est celui que le serveur emploie -, si bien que [Brouter.parse] la lit telle quelle : un
 * itineraire calcule ici et un itineraire calcule en ligne sortent du meme chemin.
 *
 * Le profil est celui de l'application, avec les memes preferences substituees (cf. [BrouterProfile.tune]).
 * Le moteur le lit sur fichier, a cote du `lookups.dat` qui en decrit le vocabulaire : les deux sont ecrits
 * dans le stockage prive au premier calcul.
 */
object BrouterLocal {

    /** Le dossier des fichiers `.rd5` : celui des reglages, le stockage externe prive a defaut. */
    fun segmentDir(ctx: Context): File = BrouterStorage.dir(ctx)

    /** Les donnees presentes couvrent-elles tous les points du trajet. Les points seulement : le trajet
     *  peut passer par un carre voisin, et c'est le moteur qui le dira alors (cf. [route]). */
    fun covers(ctx: Context, points: List<Pair<Double, Double>>): Boolean {
        val dir = segmentDir(ctx)
        return points.all { (lat, lon) -> File(dir, BrouterTile.of(lon, lat).fileName).isFile }
    }

    /**
     * Calcule le trajet [points] - des couples (lat, lon), comme partout dans le planificateur.
     *
     * [memoryClassMb] borne la memoire que le moteur s'accorde : c'est ce que fait l'application BRouter,
     * qui lui passe la classe memoire de l'appareil.
     */
    suspend fun route(
        ctx: Context, points: List<Pair<Double, Double>>, profile: RoutingProfile, prefs: RoutingPrefs,
        profileText: String, memoryClassMb: Int, timeoutMs: Long = 60_000L,
    ): Local = runInterruptible(Dispatchers.Default) {
        if (points.size < 2) return@runInterruptible Local(RouteOutcome.NoRoute, null)
        val profil = ecrireProfil(ctx, BrouterProfile.tune(profileText, profile, prefs))
        val rc = RoutingContext().apply {
            localFunction = profil.absolutePath
            memoryclass = memoryClassMb
        }
        val etapes = points.mapIndexed { i, (lat, lon) ->
            OsmNodeNamed().apply {
                name = "p$i"
                ilon = ((lon + 180.0) * 1_000_000.0 + 0.5).toInt()
                ilat = ((lat + 90.0) * 1_000_000.0 + 0.5).toInt()
            }
        }
        val moteur = RoutingEngine(null, null, segmentDir(ctx), etapes, rc).apply { quite = true }
        moteur.doRun(timeoutMs)
        // Le message d'erreur d'abord : il arrive que le moteur rende une trace VIDE en meme temps qu'il
        // se plaint - "datafile E5_N40.rd5 not found" -, et la trace seule passerait pour un trajet de zero
        // metre.
        val trace = moteur.foundTrack
        if (moteur.errorMessage != null || trace == null || trace.nodes.isNullOrEmpty()) {
            return@runInterruptible Local(RouteOutcome.NoRoute, moteur.errorMessage)
        }
        val resultat = Brouter.parse(FormatJson(rc).format(trace))
            ?: return@runInterruptible Local(RouteOutcome.NoRoute, "reponse illisible")
        Local(RouteOutcome.Done(resultat), null)
    }

    /** L'issue du calcul, et ce que le moteur a dit quand il n'a rien trouve. */
    class Local(val outcome: RouteOutcome, val error: String?)

    /** Ecrit le profil, et le `lookups.dat` a cote de lui s'il n'y est pas encore. */
    private fun ecrireProfil(ctx: Context, texte: String): File {
        val dir = File(ctx.filesDir, "brouter/profiles").apply { mkdirs() }
        val lookups = File(dir, "lookups.dat")
        if (!lookups.isFile) ctx.assets.open("brouter/lookups.dat").use { i ->
            lookups.outputStream().use { i.copyTo(it) }
        }
        // Un nom par contenu : deux preferences differentes ne doivent pas se lire l'une pour l'autre dans le
        // cache de profils du moteur, qui se fie au nom et a la date du fichier.
        val f = File(dir, "p${texte.hashCode().toUInt()}.brf")
        if (!f.isFile) f.writeText(texte)
        return f
    }
}
