package fr.lc4918.trailog.ui.routes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip

/**
 * Le selecteur de fichiers de l'import.
 *
 * Un contrat d'activite et rien d'autre, mais qui porte une connaissance durement acquise : la liste des
 * types MIME sous lesquels un GPX ou un KML circulent selon le fournisseur de stockage.
 */

/** Sélecteur de fichier qui démarre la navigation dans un dossier choisi (pas celui des MBTiles). */
internal class PickFile : ActivityResultContract<Uri?, List<Uri>>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            // Filtre par type MIME (les dossiers restent toujours visibles/navigables dans le sélecteur système).
            // KML a un type MIME officiel IANA (application/vnd.google-earth.kml+xml) ; GPX n'en a pas et
            // circule sous plusieurs conventions (gpx+xml, x-gpx+xml) selon les outils/fournisseurs. Beaucoup
            // de fournisseurs de stockage retombent sur application/octet-stream pour ces extensions non
            // reconnues : on l'inclut donc en repli, ce qui peut laisser passer d'autres fichiers à extension
            // non reconnue selon le fournisseur (limite de l'API Android, pas de filtrage par extension).
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/gpx+xml",
                "application/x-gpx+xml",
                "application/vnd.google-earth.kml+xml",
                "application/vnd.google-earth.kmz",
                "application/zip",
                "application/geo+json",
                "application/json",
                "text/xml",
                "application/xml",
                "application/octet-stream",
            ))
            if (input != null) {
                val initial = runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(input, DocumentsContract.getTreeDocumentId(input))
                }.getOrNull() ?: input
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
        }
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        val clip = intent?.clipData
        if (clip != null) return (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        return intent?.data?.let { listOf(it) } ?: emptyList()
    }
}
