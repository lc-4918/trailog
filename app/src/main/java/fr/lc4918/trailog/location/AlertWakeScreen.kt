package fr.lc4918.trailog.location

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * L'autorisation qui permet a l'alerte d'ALLUMER L'ECRAN, et ce qu'il faut faire quand elle manque.
 *
 * **Ce qu'elle autorise.** L'alerte d'eloignement arrive telephone en poche, ecran eteint : sa notification
 * porte donc une intention plein ecran, le seul mecanisme qu'Android offre pour allumer l'ecran et poser la
 * carte par-dessus le verrouillage (cf. LocationService.postOffTrackAlert, MainActivity). Sans
 * l'autorisation, la notification retombe en simple banniere - la sonnerie sonne, mais l'ecran reste noir
 * et l'alerte attend qu'on le rallume soi-meme.
 *
 * **Pourquoi elle manque.** Jusqu'a Android 13, elle est accordee a l'installation : le manifeste la
 * declare, il n'y a rien a demander. Depuis Android 14, Android ne l'accorde d'office qu'aux applications
 * d'appel et de reveil ; pour toutes les autres, elle part revoquee et ne s'active qu'a la main, dans un
 * reglage que personne ne va chercher sans savoir qu'il existe. D'ou la boite qui le dit, au moment ou l'on
 * allume le son - le seul moment ou cela veut dire quelque chose.
 *
 * **Elle n'est pas une condition du son.** Le son sonne sans elle, et l'alerte s'affiche sans elle : refuser
 * n'eteint rien, cela laisse seulement l'ecran noir. La boite se ferme donc sans annuler le reglage.
 */
object AlertWakeScreen {

    /** L'autorisation est acquise : toujours avant Android 14, a verifier aupres du systeme ensuite. */
    fun granted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
        return runCatching { nm.canUseFullScreenIntent() }.getOrDefault(true)
    }

    /**
     * Il faut prevenir : on vient d'allumer le son, et l'autorisation manque.
     *
     * Pure, et separee de [granted] : c'est la regle - "au moment ou l'on allume, et seulement si elle
     * manque" - et elle se verifie sans telephone. Eteindre le son ne demande rien, rallumer une alerte
     * deja autorisee non plus.
     */
    fun shouldAsk(turningOn: Boolean, granted: Boolean): Boolean = turningOn && !granted

    /**
     * Le reglage a ouvrir : la page de l'autorisation POUR CETTE APPLICATION.
     *
     * `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` ouvre exactement l'interrupteur cherche - Android y fait
     * defiler la liste jusqu'a Trailog et le met en evidence -, ce qu'un renvoi vers les reglages generaux
     * ne fait pas : il faudrait alors trouver seul "Notifications", puis "Notifications plein ecran". Le
     * repli reste la fiche de l'application, la ou l'autorisation se trouve aussi, pour le cas ou un
     * telephone n'offrirait pas cette page (cf. [openSettings]).
     */
    private fun settingsIntent(ctx: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${ctx.packageName}".toUri())
        } else {
            null
        }

    /** Fiche de l'application : le repli, et la page qui porte "Notifications" sur tous les telephones. */
    private fun detailsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${ctx.packageName}".toUri())

    /**
     * Ouvre le reglage, et rend vrai si une page s'est ouverte.
     *
     * Deux essais plutot qu'un : une page de reglage absente leve une `ActivityNotFoundException`, et
     * l'application tomberait pour avoir voulu rendre service.
     */
    fun openSettings(ctx: Context): Boolean {
        settingsIntent(ctx)?.let { intent ->
            if (runCatching { ctx.startActivity(intent) }.isSuccess) return true
        }
        return runCatching { ctx.startActivity(detailsIntent(ctx)) }.isSuccess
    }
}
