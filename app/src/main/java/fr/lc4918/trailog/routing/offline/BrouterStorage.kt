package fr.lc4918.trailog.routing.offline

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Ou vivent les donnees d'itineraire hors ligne : le dossier de l'application par defaut, ou celui qu'on a
 * choisi dans les reglages (Systeme > Stockage > Dossiers).
 *
 * **En preferences, et non dans la base** : le moteur et le service de telechargement doivent le connaitre
 * des leur premier appel, sans attendre qu'une requete asynchrone ait repondu - comme le theme (cf.
 * `ThemePrefs`). Le dossier choisi est un chemin reel, comme celui des MBTiles.
 */
object BrouterStorage {
    private const val PREFS = "brouter_prefs"
    private const val KEY_DIR = "dir"

    /** Le dossier choisi, ou vide pour celui de l'application. */
    fun customPath(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DIR, "").orEmpty()

    /** Le dossier par defaut : le stockage externe prive de l'application, qu'on peut atteindre par cable. */
    fun defaultDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "brouter/segments4")

    fun dir(ctx: Context): File = customPath(ctx).takeIf { it.isNotBlank() }?.let { File(it) } ?: defaultDir(ctx)

    fun setCustomPath(ctx: Context, path: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_DIR, path) }
    }

    /** Le dossier accepte-t-il qu'on y ecrive : un chemin choisi n'est pas forcement accessible a
     *  l'application, et on ne le decouvrirait qu'au premier telechargement, en plein transfert. */
    fun writable(dir: File): Boolean = runCatching {
        dir.mkdirs()
        val essai = File(dir, ".trailog-essai")
        essai.writeText("ok")
        essai.delete()
    }.getOrDefault(false)
}
