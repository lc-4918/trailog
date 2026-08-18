package fr.lc4918.trailog.routing

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
     * Calcule l'itinéraire passant par [points], en (lat, lon). Null quand il n'y en a pas - étapes non
     * reliées, service muet, réseau absent - et, pour BRouter, quand son profil manque des assets.
     */
    suspend fun route(
        ctx: Context, engine: RouteEngine, base: String, points: List<Pair<Double, Double>>,
        profile: RoutingProfile, prefs: RoutingPrefs,
    ): RouteResult? = when (engine) {
        RouteEngine.VALHALLA -> Valhalla.route(base, points, profile, prefs)
        RouteEngine.BROUTER -> profileText(ctx, profile)?.let {
            Brouter.route(base, points, profile, prefs, it)
        }
    }
}
