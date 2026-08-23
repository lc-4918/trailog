package fr.lc4918.trailog.location

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * La trace suivie, gardee sur le disque le temps d'une sortie.
 *
 * **Pourquoi elle doit y etre.** Le service de suivi est deja construit pour la mort du processus : il
 * rend `START_STICKY`, et le systeme le relance apres avoir repris sa memoire - "le suivi reprend alors
 * seul, ce qu'on attend d'une fonction qu'on a demandee pour la duree d'une sortie" (cf.
 * [LocationService.onStartCommand]). Le capteur repartait donc, et la notification aussi... mais
 * [TrackWatch] est un objet en memoire, que rien ne reconstituait : la trace suivie revenait a null, et
 * l'alerte d'eloignement se retrouvait DESARMEE SANS UN MOT. Telephone en poche, notification affichee,
 * plus personne ne veillait.
 *
 * C'est exactement le defaut que le projet a deja paye une fois, sous une autre cause : un repere qui
 * disparait en silence, et vingt kilometres dans le mauvais sens.
 *
 * **Pourquoi les echantillons, et pas seulement l'identite de la couche.** Reconstituer la trace depuis
 * sa couche demanderait le depot, une lecture de profil, et n'aurait de reponse que pour les traces de la
 * bibliotheque - le parcours du planificateur, lui, n'a aucune couche derriere lui. Les ecrire une fois
 * pour toutes donne UN seul chemin de reprise, sans cas particulier. Un itineraire de cent kilometres
 * pese quelques centaines de kilo-octets, ecrits une fois par choix de trace.
 *
 * **Pourquoi pas dans les reglages.** La table des reglages est une ligne qu'on relit a chaque
 * changement, partout dans l'application ; y coller une geometrie de plusieurs milliers de points ferait
 * payer ce poids a tout le monde, pour une donnee que seul le service lit.
 */
object FollowedStore {

    private const val FILE_NAME = "followed-track.json"

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    /**
     * Ecrit la trace suivie, ou efface le fichier quand on ne suit plus rien.
     *
     * L'effacement compte autant que l'ecriture : sans lui, un suivi arrete a midi reprendrait tout seul
     * au prochain redemarrage du service, des heures plus tard et sans que personne l'ait demande.
     */
    suspend fun save(ctx: Context, f: TrackWatch.Followed?) = withContext(Dispatchers.IO) {
        val cible = file(ctx)
        runCatching {
            if (f == null) cible.delete() else cible.writeText(json.encodeToString(f))
        }
        Unit
    }

    /** La trace suivie retrouvee, ou null : fichier absent, illisible, ou d'une version qui ne se relit
     *  plus. Un echec de lecture n'est pas une faute a signaler - il vaut "on ne suivait rien". */
    suspend fun load(ctx: Context): TrackWatch.Followed? = withContext(Dispatchers.IO) {
        val cible = file(ctx)
        if (!cible.exists()) return@withContext null
        runCatching { json.decodeFromString<TrackWatch.Followed>(cible.readText()) }.getOrNull()
    }
}
