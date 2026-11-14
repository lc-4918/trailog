package com.tunturo.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * Etat d'ouverture des dossiers de la bibliotheque, conserve d'un lancement a l'autre.
 *
 * On retient les dossiers REPLIES et non les ouverts : un dossier jamais touche (ou cree depuis) reste ouvert,
 * comme avant, sans rien avoir a enregistrer.
 */
object FolderExpansionPrefs {
    private const val PREFS_NAME = "folder_expansion_prefs"
    private const val KEY_COLLAPSED = "collapsed_folder_ids"

    fun isExpanded(context: Context, folderId: Long): Boolean = folderId.toString() !in readCollapsed(context)

    fun setExpanded(context: Context, folderId: Long, expanded: Boolean) {
        val collapsed = readCollapsed(context).toMutableSet()
        if (expanded) collapsed.remove(folderId.toString()) else collapsed.add(folderId.toString())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putStringSet(KEY_COLLAPSED, collapsed) }
    }

    private fun readCollapsed(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(KEY_COLLAPSED, emptySet()).orEmpty()
}
