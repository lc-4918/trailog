package fr.lc4918.trailog.location

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Ce que l'economie d'energie fait a la localisation, et comment le dire.
 *
 * **Le defaut qu'elle explique.** Sur un Galaxy S10e sous Android 12, mode economie d'energie actif,
 * la politique du systeme porte `location_mode=1` : le telephone ETEINT le fournisseur GPS des que
 * l'ecran s'eteint, pour toutes les applications, service de premier plan ou non. Aucun verrou
 * processeur, aucun type de service ne passe outre. Le suivi s'arrete donc sans que la localisation
 * soit coupee, et sans que rien ne l'annonce : le curseur saute au rallumage, les kilometres
 * manquent, et l'alerte d'eloignement ne sonne qu'une fois l'ecran rallume - c'est-a-dire trop tard.
 *
 * **Ce que l'application peut en faire.** Rien pour l'empecher : c'est un reglage du telephone, pas
 * une permission. Deux choses, en revanche - prendre le relais sur un autre fournisseur quand il en
 * reste un (cf. `LocationService.enabledProviders`), et NOMMER la cause quand les positions se
 * taisent, plutot que de laisser croire a une panne de GPS.
 */
object PowerSave {

    /** L'economie d'energie ne touche pas a la localisation (`PowerManager.LOCATION_MODE_NO_CHANGE`). */
    const val MODE_NO_CHANGE = 0

    /**
     * Les modes de bridage, tels que `PowerManager.getLocationPowerSaveMode` les rend : GPS eteint
     * ecran eteint (1), tout eteint ecran eteint (2), premier plan seulement (3), demandes bridees
     * ecran eteint (4). Ils ne sont pas nommes un par un : ce qui compte ici est qu'aucun ne soit
     * [MODE_NO_CHANGE].
     */
    const val MODE_GPS_DISABLED_WHEN_SCREEN_OFF = 1

    /**
     * L'economie d'energie va couper ou brider la localisation : elle est active, et sa politique
     * touche au capteur. Logique pure, pour qu'elle se verifie sans telephone.
     */
    fun cutsLocation(powerSaveOn: Boolean, mode: Int): Boolean = powerSaveOn && mode != MODE_NO_CHANGE

    /**
     * La meme question, posee au telephone. Faux avant Android 9, ou le mode de bridage ne se lit
     * pas : on ne dira alors rien plutot que de dire une cause inventee.
     */
    fun cutsLocation(ctx: Context): Boolean {
        val mode = locationMode(ctx)
        if (mode == MODE_UNKNOWN) return false
        return cutsLocation(isPowerSaveMode(ctx), mode)
    }

    /** L'economie d'energie est active, quoi qu'elle fasse a la localisation. */
    fun isPowerSaveMode(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isPowerSaveMode }.getOrDefault(false)
    }

    /** Le mode de bridage, ou [MODE_UNKNOWN] avant Android 9 - ou s'il ne se laisse pas lire. */
    fun locationMode(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return MODE_UNKNOWN
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return MODE_UNKNOWN
        return runCatching { pm.locationPowerSaveMode }.getOrDefault(MODE_UNKNOWN)
    }

    /** Le telephone ne dit pas ce que son economie d'energie fait a la localisation. */
    const val MODE_UNKNOWN = -1

    /** Les reglages d'economie d'energie du telephone, la ou la cause se corrige. */
    val settingsIntent: Intent
        get() = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Ouvre ces reglages, ou ceux de la batterie a defaut. Rien ne doit planter pour un ecran manquant. */
    fun openSettings(ctx: Context) {
        if (runCatching { ctx.startActivity(settingsIntent) }.isSuccess) return
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
