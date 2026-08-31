package fr.lc4918.trailog.ui.planner

import android.content.Context
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.geocode.GeocodePlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Une etape, reduite a ce qui la fait revivre : un lieu, ou la position du porteur.
 *
 * Un booleen plutot que deux types : la position du porteur n'a ni libelle ni coordonnees a garder - elle
 * se resout au calcul, et le parcours qu'on reprend porte deja sa geometrie.
 *
 * [mapPicked] dit la meme chose d'un lieu MONTRE sur la carte : le point est deja dans [lon] et [lat], et
 * son adresse dans [lines] - il ne reste a retenir que le fait qu'on l'ait designe du doigt, pour reposer
 * son epingle noire (cf. RoutePlannerState.mapPins). Defaut faux : un fichier ecrit avant lui se relit.
 */
@Serializable
data class StepSnapshot(
    val lines: List<String> = emptyList(),
    val lon: Double = 0.0,
    val lat: Double = 0.0,
    val currentPosition: Boolean = false,
    val mapPicked: Boolean = false,
) {
    fun target(): StepTarget =
        if (currentPosition) StepTarget.CurrentPosition else StepTarget.Place(GeocodePlace(lines, lon, lat))

    companion object {
        fun of(t: StepTarget, mapPicked: Boolean = false): StepSnapshot = when (t) {
            StepTarget.CurrentPosition -> StepSnapshot(currentPosition = true)
            is StepTarget.Place -> StepSnapshot(t.place.lines, t.place.lon, t.place.lat, mapPicked = mapPicked)
        }
    }
}

/**
 * L'itineraire temporaire tel qu'il etait a l'ecran : ses etapes, sa discipline, et le parcours calcule.
 *
 * Le parcours EST garde, et non recalcule a la reprise. Le recalculer demanderait le reseau au pire moment
 * - une application qui vient de renaitre, sur un telephone qui sort de veille - et une etape "d'ou je
 * suis" ne partirait plus du meme endroit. Un parcours calcule reste un parcours calcule.
 */
@Serializable
data class PlannerSnapshot(
    val steps: List<StepSnapshot>,
    val profile: String,
    val collapsed: Boolean,
    val meters: Double,
    val seconds: Double,
    val track: ComputedTrack,
)

/**
 * L'itineraire temporaire, garde sur le disque le temps d'une sortie.
 *
 * **Pourquoi il doit y etre.** Le planificateur vit dans un `ViewModel` (cf. `MapScreenStates`), ce qui
 * lui fait traverser une rotation - mais pas la mort du processus, que le systeme provoque des que
 * l'application passe assez longtemps derriere, ecran eteint au premier chef. Un testeur l'a rapporte
 * ainsi : "l'itineraire temporaire se perd, sans meme cliquer" ; veille souvent, bascule d'application
 * rarement, rotation jamais - exactement le classement des durees passees en arriere-plan.
 *
 * L'etat le disait deja sans le corriger : "Apres une mort du processus, le planificateur repart vide
 * alors que la veille, elle, a ete reprise du disque" (cf. `OffTrackAlertEffects`). C'etait la pire des
 * deux moities - la cloche continuait de veiller sur un parcours que plus rien ne montrait.
 *
 * **Le meme remede que pour la trace suivie** (cf. [fr.lc4918.trailog.location.FollowedStore]), et pour
 * les memes raisons : un fichier plutot que la table des reglages, qu'on relit partout et a chaque
 * changement - une geometrie de plusieurs milliers de points y ferait payer son poids a tout le monde.
 * Les deux fichiers sont ecrits ensemble et se relisent ensemble : le parcours et la veille qui le
 * surveille reviennent d'un seul tenant.
 *
 * **Ecrit seulement quand il y a un parcours CALCULE.** Des etapes a moitie saisies ne valent pas d'etre
 * ressuscitees : ce qu'on regrette de perdre, c'est le trajet qu'on suivait.
 */
object PlannerStore {

    private const val FILE_NAME = "planner-route.json"

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    /**
     * Ecrit l'itineraire, ou efface le fichier quand il n'y en a plus.
     *
     * L'effacement compte autant que l'ecriture : sans lui, un trajet abandonne a midi reviendrait tout
     * seul au prochain lancement, des heures plus tard et sans que personne l'ait demande.
     */
    suspend fun save(ctx: Context, snapshot: PlannerSnapshot?) = withContext(Dispatchers.IO) {
        val cible = file(ctx)
        runCatching {
            if (snapshot == null) cible.delete() else cible.writeText(json.encodeToString(snapshot))
        }
        Unit
    }

    /** L'itineraire retrouve, ou null : fichier absent, illisible, ou d'une version qui ne se relit plus.
     *  Un echec de lecture n'est pas une faute a signaler - il vaut "il n'y avait pas de trajet". */
    suspend fun load(ctx: Context): PlannerSnapshot? = withContext(Dispatchers.IO) {
        val cible = file(ctx)
        if (!cible.exists()) return@withContext null
        runCatching { json.decodeFromString<PlannerSnapshot>(cible.readText()) }.getOrNull()
    }
}
