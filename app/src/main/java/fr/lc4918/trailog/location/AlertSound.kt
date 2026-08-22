package fr.lc4918.trailog.location

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le son de l'alerte : celui que le telephone reserve a ses notifications.
 *
 * Aucun fichier livre avec l'application, et aucune notification postee non plus : l'alerte s'affiche a
 * l'ecran, sous les yeux de qui marche avec la carte ouverte. Le son ne fait que la rendre audible poche
 * fermee - il emprunte donc la sonnerie du systeme, celle que l'utilisateur reconnait deja.
 *
 * **Ici et non dans `ui/alert`**, ou ce fichier a d'abord vecu : il ne depend pas de Compose, seulement
 * d'Android, et c'est [LocationService] qui le joue - une couche basse, qui n'a pas a remonter vers
 * l'interface pour cela. L'ecran de reglages, lui, a le droit de descendre ici pour afficher le nom du
 * son retenu.
 */

/** URI du son retenu : celui choisi, ou a defaut celui que le telephone donne pour ses notifications. */
private fun soundUri(uri: String): Uri? =
    uri.takeIf { it.isNotBlank() }?.toUri()
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

/**
 * Joue le son une fois. Sur le fil d'E/S : ouvrir une sonnerie lit un fichier, parfois sur un support
 * externe, et le faire sur le fil principal ferait sauter une image de la carte.
 *
 * Un echec ne se signale pas : un son qui ne sort pas ne doit pas empecher l'alerte de s'afficher, et
 * c'est la banniere qui porte le message.
 */
suspend fun playAlertSound(ctx: Context, uri: String) = withContext(Dispatchers.IO) {
    runCatching {
        val ringtone = RingtoneManager.getRingtone(ctx, soundUri(uri) ?: return@runCatching)
        // Flux "alarme" plutot que "notification" : on marche, le telephone est en poche, et une
        // notification passe sous le seuil audible des que le volume des messages est baisse.
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone?.play()
    }
    Unit
}

/** Nom lisible du son retenu, ou null pour celui du telephone (le reglage affiche alors "Par defaut"). */
fun alertSoundTitle(ctx: Context, uri: String): String? {
    val u = uri.takeIf { it.isNotBlank() }?.toUri() ?: return null
    return runCatching { RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx) }.getOrNull()
}
