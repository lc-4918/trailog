package fr.lc4918.trailog.ui.routes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import fr.lc4918.trailog.data.imp.ImportInbox

/**
 * Le chemin qu'un fichier emprunte pour devenir une couche, d'ou qu'il vienne.
 *
 * Deux entrees, une seule suite. Le bouton "Importer" du menu demande d'abord un dossier d'accueil, puis
 * ouvre le selecteur de fichier. Une autre application qui nous confie un fichier (cf. [ImportInbox])
 * emprunte le meme chemin a ceci pres que les fichiers sont deja choisis : c'est le dossier qui manque, et
 * le choix de dossier s'ouvre alors de lui-meme.
 *
 * C'est [proceed] qui distingue les deux, et c'est tout ce qui les distingue : ce qui attend part au
 * dossier retenu, et s'il n'y a rien qui attende, on va chercher des fichiers.
 *
 * S'obtient par [rememberImportFlow] : les deux selecteurs qu'il porte ne peuvent naitre que dans une
 * composition.
 */
@Stable
class ImportFlow internal constructor(
    private val ctx: Context,
    private val onImportLayer: (bytes: ByteArray, name: String, folderId: Long?) -> Unit,
    private val onFilesEntrusted: () -> Unit,
) {
    /** Choix du dossier d'accueil, ouvert avant d'aller chercher les fichiers. */
    var folderPicker by mutableStateOf(false)

    /** Creation d'un dossier depuis ce choix, pour y importer dans la foulee. */
    var newFolderDialog by mutableStateOf(false)

    /**
     * Les fichiers refuses, presentes en une seule fois.
     *
     * Rempli quand plus aucun import ne tourne, et pas au fil de l'eau : un lot de trente fichiers dont
     * cinq sont fautifs ouvrirait cinq boites l'une apres l'autre, en plein import.
     */
    var report by mutableStateOf<List<MainViewModel.ImportFailure>>(emptyList())

    /** Dossier retenu, ou null pour la racine : le selecteur de fichier le rend a son retour. */
    private var pendingFolder: Long? = null

    // Le dossier de depart du selecteur, tel que les reglages le retiennent. Repose a chaque composition
    // plutot que fige a la construction : le reglage change sans que ce porteur soit recree.
    internal var importDir: String? = null

    // Les deux selecteurs, poses par [rememberImportFlow] : ils ne peuvent naitre que dans une composition,
    // et leurs suites appellent des methodes d'ici.
    internal lateinit var filePicker: ActivityResultLauncher<Uri?>
    internal lateinit var mediaPermissionLauncher: ActivityResultLauncher<String>

    /** Le bouton "Importer" du menu : tout commence par le dossier d'accueil. */
    fun askFolder() { folderPicker = true }

    /**
     * Suite du choix d'un dossier : importer ce qui attend, ou aller chercher des fichiers.
     *
     * Le menu s'ouvre sur un import venu d'ailleurs, et lui seul : la couche arrive dans un dossier que
     * rien a l'ecran ne montre, et sans cela l'application ne repondrait que par le silence a un fichier
     * qu'on vient de lui confier.
     */
    fun proceed(folderId: Long?) {
        pendingFolder = folderId
        val attendus = ImportInbox.consume()
        if (attendus.isEmpty()) { launchPicker(); return }
        importUris(attendus, folderId)
        onFilesEntrusted()
    }

    /**
     * Importe des fichiers designes par leur URI, quelle que soit la main qui les a choisis : le selecteur
     * de l'application, ou une autre application qui nous les confie.
     */
    internal fun importUris(uris: List<Uri>, folderId: Long?) {
        uris.forEach { uri ->
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import"
            val bytes = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@forEach
            onImportLayer(bytes, name, folderId)
        }
    }

    internal fun onFilesPicked(uris: List<Uri>) = importUris(uris, pendingFolder)

    /** Le dossier retenu s'efface, et les fichiers qui attendaient sont relaches. */
    fun cancel() {
        folderPicker = false
        ImportInbox.clear()
    }

    // Photos locales des waypoints (GPX OruxMaps/OsmAnd/Locus/Garmin) : lues en acces fichier direct dans
    // le stockage partage a l'import (resolveLocalImages) -> permission de lecture des images. Demandee
    // juste avant le selecteur de fichier ; un refus n'empeche pas l'import (les photos afficheront
    // "introuvable").
    private val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    internal fun openPicker() { filePicker.launch(importDir?.takeIf { it.isNotBlank() }?.toUri()) }

    private fun launchPicker() {
        if (ContextCompat.checkSelfPermission(ctx, mediaPermission) == PackageManager.PERMISSION_GRANTED) openPicker()
        else mediaPermissionLauncher.launch(mediaPermission)
    }
}

/**
 * Le chemin d'import, avec ses deux selecteurs et la veille sur ce qu'une autre application nous confie.
 *
 * @param importDir le dossier ou le selecteur de fichier s'ouvre, tel que les reglages le retiennent.
 * @param onFilesEntrusted appele quand des fichiers venus d'ailleurs viennent d'etre importes : c'est le
 *   moment d'ouvrir le menu, faute de quoi la couche arriverait dans un dossier que rien ne montre.
 */
@Composable
fun rememberImportFlow(
    importDir: String?,
    onImportLayer: (bytes: ByteArray, name: String, folderId: Long?) -> Unit,
    onFilesEntrusted: () -> Unit,
): ImportFlow {
    val ctx = LocalContext.current
    val flow = remember(ctx) { ImportFlow(ctx, onImportLayer, onFilesEntrusted) }
    flow.importDir = importDir
    flow.filePicker = rememberLauncherForActivityResult(remember { PickFile() }) { uris -> flow.onFilesPicked(uris) }
    flow.mediaPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { flow.openPicker() }

    // Un fichier confie par une autre application : le dossier d'accueil s'ouvre de lui-meme.
    val inbox by ImportInbox.pending.collectAsState()
    LaunchedEffect(inbox) {
        if (inbox.isNotEmpty()) flow.askFolder()
    }
    return flow
}

