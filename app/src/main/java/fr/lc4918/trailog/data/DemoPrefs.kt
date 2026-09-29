package fr.lc4918.trailog.data

import android.content.Context
import androidx.core.content.edit

/**
 * Le jeu de demonstration a-t-il deja ete pose ? En SharedPreferences, et non plus dans la ligne des
 * reglages (cf. SettingsEntity.demoSeeded, qui ne sert plus qu'aux bases d'avant).
 *
 * **Pourquoi le sortir des reglages.** Cette ligne se reecrit EN ENTIER, a partir d'une copie : la
 * position de la carte, le suivi, les filtres de points d'interet l'enregistrent chacun a leur tour. Une
 * copie lue au demarrage, juste avant que le semis leve le drapeau, le remettait a faux en s'enregistrant ;
 * "Reinitialiser les reglages" le remettait a faux par construction. Au lancement suivant, un dossier Demo
 * de plus - trois chez un testeur, au fil des mises a jour. Ici, rien d'autre n'ecrit.
 */
object DemoPrefs {
    private const val PREFS_NAME = "demo_prefs"
    private const val KEY_SEEDED = "seeded"

    fun seeded(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SEEDED, false)

    fun markSeeded(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) { putBoolean(KEY_SEEDED, true) }
    }
}
