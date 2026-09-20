package fr.lc4918.trailog.location

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le son de l'alerte d'eloignement : une SONNERIE, en boucle, jusqu'a ce qu'on reponde.
 *
 * **Une sonnerie et non une notification**, sur le flux sonnerie du telephone. Le son sortait d'abord sur
 * le flux alarme : son volume ne suit alors ni le curseur des notifications ni celui de la sonnerie mais
 * celui des alarmes, regle ailleurs (l'horloge, sur certains telephones) et le plus souvent a mi-course -
 * le son choisi dans les reglages sortait donc deux fois moins fort qu'a l'ecoute. Le flux sonnerie est
 * celui qu'on monte pour etre appele, et c'est exactement ce que l'alerte fait : elle appelle.
 *
 * **En boucle**, et non une fois : le telephone est en poche, le son d'une notification dure une seconde,
 * et cette seconde-la passe sous un vent de face ou une descente. La boucle tient tant que l'ecart dure et
 * que personne n'a repondu ; repondre est un tap sur la banniere ou sur la notification, et revenir sur la
 * trace la coupe aussi (cf. [OffTrack.ringing], LocationService.watchAlertRing).
 *
 * **Ici et non dans `ui/alert`**, ou ce fichier a d'abord vecu : il ne depend pas de Compose, seulement
 * d'Android, et c'est [LocationService] qui le joue - une couche basse, qui n'a pas a remonter vers
 * l'interface pour cela. L'ecran de reglages, lui, a le droit de descendre ici pour afficher le nom du
 * son retenu.
 */
object AlertSound {

    /**
     * Le lecteur en cours, ou null quand rien ne sonne.
     *
     * Un [MediaPlayer] plutot qu'une [android.media.Ringtone] : la sonnerie du systeme ne sait boucler
     * qu'a partir d'Android 9, et l'application accompagne des telephones plus anciens.
     */
    @Volatile private var player: MediaPlayer? = null

    /**
     * Ouvre la sonnerie et la lance en boucle. Sans effet si elle sonne deja : l'ecart se mesure toutes
     * les deux secondes, et chaque mesure repasse par ici.
     *
     * Sur le fil d'E/S : ouvrir une sonnerie lit un fichier, parfois sur un support externe, et le faire
     * sur le fil principal ferait sauter une image de la carte.
     *
     * Un echec ne se signale pas : un son qui ne sort pas ne doit pas empecher l'alerte de s'afficher, et
     * c'est la banniere qui porte le message.
     */
    suspend fun start(ctx: Context, uri: String) = withContext(Dispatchers.IO) {
        if (player != null) return@withContext
        val source = soundUri(uri) ?: return@withContext
        val nouveau = MediaPlayer()
        val pret = runCatching {
            nouveau.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            nouveau.setDataSource(ctx, source)
            nouveau.isLooping = true
            nouveau.prepare()
            nouveau.start()
        }.isSuccess
        // Un lecteur a moitie ouvert garde le fichier et la sortie audio : il se rend tout de suite, sans
        // quoi le silence suivant n'aurait rien a arreter et le suivant sonnerait dans le vide.
        if (pret) player = nouveau else runCatching { nouveau.release() }
        Unit
    }

    /** Coupe la sonnerie, s'il y en a une. Appelable de n'importe quel fil, et sans effet deux fois. */
    fun stop() {
        val courant = player ?: return
        player = null
        runCatching { courant.stop() }
        runCatching { courant.release() }
    }

    /** URI du son retenu : celui choisi, ou a defaut la sonnerie que le telephone donne pour ses appels. */
    private fun soundUri(uri: String): Uri? =
        uri.takeIf { it.isNotBlank() }?.toUri()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}

/** Nom lisible du son retenu, ou null pour celui du telephone (le reglage affiche alors "Par defaut"). */
fun alertSoundTitle(ctx: Context, uri: String): String? {
    val u = uri.takeIf { it.isNotBlank() }?.toUri() ?: return null
    return runCatching { RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx) }.getOrNull()
}
