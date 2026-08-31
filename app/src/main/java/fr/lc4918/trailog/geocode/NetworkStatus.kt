package fr.lc4918.trailog.geocode

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Etat de la connexion, pour prevenir avant d'ouvrir une recherche qui ne pourrait pas aboutir. */
object NetworkStatus {

    /**
     * Le telephone a-t-il un acces Internet utilisable (wifi ou donnees mobiles) ?
     *
     * On exige NET_CAPABILITY_VALIDATED en plus de INTERNET : un wifi rejoint mais sans sortie (portail
     * captif d'hotel, box hors service) annonce la capacite INTERNET tout en ne menant nulle part, et la
     * recherche echouerait apres un delai d'attente, sans explication.
     *
     * Repond vrai si le service systeme est indisponible : ne pas savoir n'est pas une raison de refuser.
     */
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return usable(caps)
    }

    private fun usable(caps: NetworkCapabilities): Boolean =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    /**
     * L'acces a Internet, **suivi dans le temps** et non constate une fois.
     *
     * **Ce que [hasInternet] ne pouvait pas dire.** Le constat ponctuel repond au moment ou l'on ouvre une
     * recherche : c'est ce qu'il faut pour refuser d'ouvrir un champ qui ne rendrait rien. Mais il ne dit
     * jamais que le reseau est REVENU, et ce qui s'est constate sans lui reste affiche : le bandeau
     * "Derniers points connus (hors ligne)" tenait l'ecran indefiniment, une fois repasse en ligne, faute
     * de quoi que ce soit pour le lever - les points d'interet ne se rechargent qu'a l'arret de la camera,
     * et un telephone pose sur une table n'en produit aucun.
     *
     * Emet a l'abonnement, puis a chaque changement. [distinctUntilChanged] parce que le systeme signale
     * abondamment - une capacite qui change, un reseau qui remplace un autre - la ou l'on ne veut savoir
     * qu'une chose : peut-on sortir, oui ou non.
     *
     * Vrai quand le service systeme manque, comme [hasInternet] : ne pas savoir n'est pas une raison de se
     * declarer hors ligne.
     */
    fun online(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) { send(true); awaitClose { } ; return@callbackFlow }
        // Le reseau PAR DEFAUT, celui qu'une requete empruntera : suivre tous les reseaux ferait dire
        // "en ligne" pour un wifi rejoint que le systeme n'a pas retenu.
        val rappel = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // La capacite VALIDATED arrive APRES la disponibilite du reseau : un wifi rejoint annonce
                // d'abord une liaison, et seulement ensuite qu'elle mene quelque part. C'est ce second
                // signal qui compte - un portail captif d'hotel s'arrete au premier.
                trySend(usable(caps))
            }
        }
        trySend(runCatching { hasInternet(context) }.getOrDefault(true))
        val pose = runCatching { cm.registerDefaultNetworkCallback(rappel) }.isSuccess
        awaitClose { if (pose) runCatching { cm.unregisterNetworkCallback(rappel) } }
    }.distinctUntilChanged()
}
