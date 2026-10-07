package fr.lc4918.trailog.net

import java.net.URI

/**
 * Portee d'une URL de service. Partage par le geocodeur et le moteur d'itineraire : tous deux sont
 * auto-hebergeables, et tous deux doivent donc distinguer "hors du reseau local" de "injoignable".
 *
 * Sans dependance Android, pour rester verifiable sans emulateur.
 */
object ServiceUrl {

    /**
     * L'URL sort-elle du reseau local ? Vrai pour une instance publique et pour tout hote routable, faux
     * pour une instance auto-hebergee a la maison.
     *
     * Sert a ne prevenir de l'absence de connexion que lorsqu'elle empeche vraiment le service : un
     * telephone en wifi sans acces Internet atteint encore le serveur du NAS, refuser y serait faux. Une
     * URL illisible est comptee comme externe : c'est le cas courant, et prevenir a tort vaut mieux que
     * laisser une requete echouer sans explication.
     */
    /**
     * Le calcul d'itineraire est-il impossible ? Seulement quand le moteur vise sort du reseau local, que
     * le telephone n'a pas Internet ET qu'aucune zone n'est telechargee : avec au moins une zone, le calcul
     * se fait sur le telephone (cf. Router.route) et c'est lui qui dira, trajet en main, si un carre manque.
     */
    fun routingBlocked(routingBase: String, hasInternet: Boolean, hasOfflineZones: Boolean): Boolean =
        needsInternet(routingBase) && !hasInternet && !hasOfflineZones

    fun needsInternet(base: String): Boolean {
        val host = runCatching { URI(base.trim()).host }.getOrNull()?.lowercase() ?: return true
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return false
        if ('.' !in host && ':' !in host) return false      // nom de machine seul, resolu sur le reseau local
        val o = host.split('.').mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return true                        // nom de domaine : routable
        return when {
            o[0] == 127 -> false                            // boucle locale
            o[0] == 10 -> false                             // 10.0.0.0/8
            o[0] == 192 && o[1] == 168 -> false             // 192.168.0.0/16
            o[0] == 172 && o[1] in 16..31 -> false          // 172.16.0.0/12
            o[0] == 169 && o[1] == 254 -> false             // lien-local
            else -> true
        }
    }
}
