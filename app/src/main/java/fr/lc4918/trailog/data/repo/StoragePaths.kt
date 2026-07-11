package fr.lc4918.trailog.data.repo

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Conversion best-effort d'une URI d'arborescence (SAF) en chemin réel, requise par
 * le natif MapLibre (mbtiles://). Couvre le stockage principal et la plupart des cartes SD.
 * Renvoie null si non convertible (scoped storage) -> l'appelant copiera le fichier.
 */
object StoragePaths {
    fun treeUriToPath(ctx: Context, uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            val type = parts[0]; val rel = parts.getOrNull(1) ?: ""
            if (type.equals("primary", true)) {
                File(Environment.getExternalStorageDirectory(), rel).absolutePath
            } else {
                // volume secondaire (carte SD) : /storage/<volId>/<rel>
                val candidate = File("/storage/$type", rel)
                if (candidate.exists()) candidate.absolutePath else null
            }
        } catch (e: Exception) { null }
    }

    fun displayName(path: String): String = path.substringAfterLast('/').ifBlank { path }
}
