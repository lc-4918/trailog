package fr.lc4918.trailog.geocode

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
