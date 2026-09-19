package fr.lc4918.trailog.routing

import android.app.ActivityManager
import android.content.Context
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import java.util.concurrent.ConcurrentHashMap

/**
 * Le calcul d'itinéraire, quel que soit le moteur réglé (cf. [RouteEngine]).
 *
 * Un seul point d'entrée pour les trois endroits qui calculent un trajet - la mesure depuis un point de la
 * carte, le planificateur, le pont qui rejoint deux traces. Sans lui, chacun porterait le même `when` sur
 * le moteur, et le jour où un troisième arriverait, l'un des trois l'oublierait.
 *
 * Les deux clients rendent le même [RouteResult] : c'est ce qui permet de changer de moteur sous les
 * mêmes écrans, et de comparer les deux sur le même trajet sans rien changer d'autre.
 */
object Router {

    /** L'instance publique du moteur, celle qu'un réglage d'URL vide désigne. */
    fun defaultUrlOf(engine: RouteEngine): String = when (engine) {
        RouteEngine.VALHALLA -> Valhalla.DEFAULT_URL
        RouteEngine.BROUTER -> Brouter.DEFAULT_URL
    }

    /** L'URL à interroger : celle du réglage, ou l'instance publique du moteur si le réglage est vide. */
    fun baseOf(engine: RouteEngine, url: String?): String =
        url?.trim()?.takeIf { it.isNotEmpty() } ?: defaultUrlOf(engine)

    /**
     * Textes des profils BRouter lus dans les assets, par nom de fichier.
     *
     * Gardés en mémoire parce qu'ils pèsent une vingtaine de kilo-octets chacun et qu'un planificateur
     * recalcule à chaque étape ajoutée ou déplacée : relire l'asset à chaque fois ferait un accès disque
     * par frappe. Cinq profils au total, le cache est borné par construction.
     */
    private val profils = ConcurrentHashMap<String, String>()

    private fun profileText(ctx: Context, profile: RoutingProfile): String? {
        val nom = BrouterProfile.assetOf(profile)
        profils[nom]?.let { return it }
        val texte = runCatching {
            ctx.assets.open(nom).use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return null
        profils[nom] = texte
        return texte
    }

    /**
     * Calcule l'itinéraire passant par [points], en (lat, lon).
     *
     * Rend ce qui s'est passé (cf. [RouteOutcome]) et non un simple parcours nullable : un moteur
     * injoignable et un moteur qui refuse de relier ces étapes-là appellent deux messages opposés, et
     * l'un des deux seulement mérite qu'on redemande.
     *
     * Le profil BRouter absent des assets compte comme un refus : rien n'a été demandé au réseau, et
     * réessayer ne le ferait pas apparaître.
     */
    suspend fun route(
        ctx: Context, engine: RouteEngine, base: String, points: List<Pair<Double, Double>>,
        profile: RoutingProfile, prefs: RoutingPrefs,
    ): RouteOutcome {
        /*
         * **Sur le telephone d'abord, quand les donnees sont la** (cf. BrouterLocal et les zones des
         * reglages) - quel que soit le moteur regle. Le calcul y est celui de brouter.de, au metre pres, et
         * il ne depend ni du reseau ni d'un service public qu'on ne tient pas. Il est plus lent sur un long
         * trajet - une dizaine de secondes pour cent soixante kilometres, contre deux en ligne -, et c'est
         * le prix d'une application qui calcule la ou il n'y a pas de signal.
         */
        val local = if (points.size >= 2 && BrouterLocal.covers(ctx, points)) routeLocal(ctx, points, profile, prefs) else null
        if (local?.outcome is RouteOutcome.Done) return local.outcome
        val enLigne = when (engine) {
            RouteEngine.VALHALLA -> Valhalla.route(base, points, profile, prefs)
            RouteEngine.BROUTER -> profileText(ctx, profile)?.let {
                Brouter.route(base, points, profile, prefs, it)
            } ?: RouteOutcome.NoRoute
        }
        /*
         * Le reseau manque, et le telephone avait deja REPONDU qu'il n'y a pas de chemin - avec toutes ses
         * donnees : c'est cette reponse qui vaut, et non "reseau absent", qui ferait attendre un signal pour
         * rien. S'il lui manquait un carre, en revanche, il n'a rien dit du trajet, et le reseau reste la
         * seule cause.
         */
        if (enLigne is RouteOutcome.Unreachable && local != null && !local.missingData) return RouteOutcome.NoRoute
        return enLigne
    }

    /** Un calcul sur le telephone, et s'il a echoue faute d'un carre. */
    private class Local(val outcome: RouteOutcome, val missingData: Boolean)

    private suspend fun routeLocal(
        ctx: Context, points: List<Pair<Double, Double>>, profile: RoutingProfile, prefs: RoutingPrefs,
    ): Local? {
        val texte = profileText(ctx, profile) ?: return null
        val memoire = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 128
        val r = runCatching { BrouterLocal.route(ctx, points, profile, prefs, texte, memoire) }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }
        val outcome = (r.outcome as? RouteOutcome.Done)?.copy(offline = true) ?: r.outcome
        // "datafile E5_N40.rd5 not found" : le trajet passe par un carre absent.
        return Local(outcome, missingData = r.error?.contains("not found") == true)
    }
}
