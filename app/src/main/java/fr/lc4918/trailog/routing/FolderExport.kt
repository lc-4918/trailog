package fr.lc4918.trailog.routing

import fr.lc4918.trailog.data.db.FolderEntity
import fr.lc4918.trailog.data.db.LayerEntity

/**
 * Ce qu'emporte l'export d'un dossier : ses couches (traces et marqueurs), sous-dossiers compris, rangees
 * dans l'archive comme elles le sont dans l'arborescence.
 */
object FolderExport {

    /** Une couche a ecrire dans l'archive, sous le chemin [path] (separateur "/", extension comprise). */
    class Entry(val path: String, val layer: LayerEntity)

    /**
     * Les couches du dossier [folderId] et de ses descendants, toutes : une couche de marqueurs s'exporte
     * comme une trace, et en omettre une dans l'archive passerait inapercu. Deux couches de meme nom dans un meme dossier ne
     * doivent pas s'ecraser dans l'archive : la seconde prend un numero.
     */
    fun entries(
        folderId: Long, folders: List<FolderEntity>, layers: List<LayerEntity>, extension: String,
    ): List<Entry> {
        val result = mutableListOf<Entry>()
        val taken = HashSet<String>()
        val visited = HashSet<Long>()
        fun visit(id: Long, prefix: String) {
            if (!visited.add(id)) return   // un cycle (que le deplacement interdit) ne doit pas figer l'export
            layers.filter { it.folderId == id }.sortedBy { it.sortOrder }.forEach { layer ->
                val base = GpxWriter.safeName(layer.name)
                var path = "$prefix$base.$extension"
                var n = 2
                while (!taken.add(path.lowercase())) path = "$prefix$base-${n++}.$extension"
                result += Entry(path, layer)
            }
            val segments = HashSet<String>()
            folders.filter { it.parentId == id }.sortedBy { it.sortOrder }.forEach { child ->
                val base = GpxWriter.safeName(child.name, "dossier")
                var segment = base
                var n = 2
                // Deux sous-dossiers de meme nom : leurs traces ne doivent pas se melanger.
                while (!segments.add(segment.lowercase())) segment = "$base-${n++}"
                visit(child.id, "$prefix$segment/")
            }
        }
        visit(folderId, "")
        return result
    }
}
