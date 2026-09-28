package fr.lc4918.trailog.location

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * La vibration de l'alerte d'eloignement : deux secousses, un silence, et ainsi de suite, jusqu'a ce qu'on
 * reponde - comme la sonnerie (cf. [AlertSound]), et independamment d'elle.
 *
 * **Pourquoi une vibration a part**, alors que la notification de l'alerte vibre deja : elle ne vibre
 * qu'une fois, a son arrivee. Telephone en poche, sur un chemin qui secoue, une secousse passe inapercue ;
 * une vibration qui insiste se sent. Et elle se sent la ou la sonnerie ne s'entend pas : vent de face,
 * torrent, ou quand on a coupe le son pour ne gener personne.
 *
 * Comme la sonnerie, elle est commandee par l'ETAT de l'alerte (cf. LocationService.watchAlertRing) : ce
 * qui l'arrete - un tap sur la banniere, le retour sur la trace, la cloche desarmee, le reglage eteint -
 * compte autant que ce qui la lance.
 */
object AlertVibration {

    /**
     * Le motif, en millisecondes : attente, secousse, pause, secousse, silence - puis il recommence.
     * Deux secousses et non une : une secousse isolee se confond avec les cahots du chemin.
     */
    internal val PATTERN = longArrayOf(0, 500, 250, 500, 1_500)

    @Volatile private var vibrating = false

    /** Lance la vibration en boucle. Sans effet si elle vibre deja : l'ecart se mesure toutes les deux secondes. */
    fun start(ctx: Context) {
        if (vibrating) return
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        vibrating = true
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Sur le canal des alarmes : un telephone en mode silencieux vibre encore pour elles.
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                @Suppress("DEPRECATION")
                v.vibrate(VibrationEffect.createWaveform(PATTERN, 0), attrs)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(PATTERN, 0)
            }
        }.onFailure { vibrating = false }
    }

    /** Arrete la vibration, s'il y en a une. Sans effet deux fois. */
    fun stop(ctx: Context) {
        if (!vibrating) return
        vibrating = false
        runCatching { vibrator(ctx)?.cancel() }
    }

    private fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
