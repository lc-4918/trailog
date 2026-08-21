package fr.lc4918.trailog.data.imp

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Les fichiers qu'une autre application nous confie, en attente d'un dossier d'accueil.
 *
 * L'import se faisait par le seul bouton *Importer* : un GPX recu par courriel ou pose dans un
 * gestionnaire de fichiers ne pouvait pas s'ouvrir dans Trailog, alors que le sens sortant - partager une
 * trace en GPX - existait deja. Le manifeste declare desormais les filtres qui font apparaitre
 * l'application dans "Ouvrir avec" et "Partager vers" ; ce qu'ils rapportent atterrit ici.
 *
 * **Une boite et non un parametre d'ecran** : l'intention arrive a l'activite, parfois avant que la carte
 * ne soit composee, parfois alors qu'elle l'est deja (l'application tournait). Une file que l'ecran vide
 * quand il est pret couvre les deux cas sans que l'activite ait a savoir ou en est la composition.
 */
object ImportInbox {

    private val _pending = MutableStateFlow<List<Uri>>(emptyList())

    /** Fichiers recus et pas encore ranges. L'ecran ouvre le choix du dossier des qu'elle n'est plus vide. */
    val pending: StateFlow<List<Uri>> = _pending.asStateFlow()

    /** Ajoute a la file, sans remplacer : deux partages coup sur coup s'importent tous les deux. */
    fun offer(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _pending.value = _pending.value + uris
    }

    /** Vide la file et rend ce qu'elle portait - l'import est lance, plus rien n'attend. */
    fun consume(): List<Uri> {
        val recus = _pending.value
        _pending.value = emptyList()
        return recus
    }

    fun clear() { _pending.value = emptyList() }

    /**
     * Les fichiers que porte une intention, quelle que soit la facon dont elle nous arrive : l'ouverture
     * d'un fichier (`VIEW`, l'URI dans les donnees) et le partage (`SEND`, l'URI dans un extra), qui ne se
     * lisent pas au meme endroit.
     *
     * Une intention deja traitee ne rend rien : elle est marquee au passage, sans quoi la recreation de
     * l'activite - une rotation d'ecran suffit - reimporterait le meme fichier a chaque fois.
     */
    fun urisOf(intent: Intent?): List<Uri> {
        if (intent == null || intent.getBooleanExtra(CONSUMED, false)) return emptyList()
        val uris = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) intent.putExtra(CONSUMED, true)
        return uris.filterNotNull()
    }

    private const val CONSUMED = "fr.lc4918.trailog.INTENT_CONSUMED"
}
