package fr.lc4918.trailog.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Ou en est l'installation de la mise a jour, du telechargement au verdict du systeme.
 *
 * L'ancienne voie - un `ACTION_VIEW` sur l'APK - lancait l'installateur et n'en savait plus rien : sur un
 * telephone ou Play Protect demande un choix, l'installation pouvait rester suspendue sans que l'appli le
 * sache ni puisse la defaire. Une session `PackageInstaller` rend ce suivi : le systeme rapporte chaque
 * etape, et la session se laisse abandonner.
 */
sealed interface InstallState {
    /** Aucune mise a jour en cours. */
    data object Idle : InstallState
    /** L'APK arrive (DownloadManager). */
    data object Downloading : InstallState
    /** L'APK se copie dans la session d'installation. */
    data object Staging : InstallState
    /** La session est remise au systeme, qui verifie puis remplace l'appli. */
    data object Installing : InstallState
    /** Le systeme attend un geste de l'utilisateur : sa fenetre de confirmation est a l'ecran. */
    data object WaitingUser : InstallState
    /** L'installation s'est arretee ; [message] est celui du systeme, s'il en a donne un. */
    data class Failed(val message: String?) : InstallState

    companion object {
        /**
         * L'etat que decrit un rapport de session (`PackageInstaller.EXTRA_STATUS`), ou null s'il n'en change
         * pas : un succes n'en est pas un a afficher, le systeme remplace l'appli et la ferme.
         * Une annulation par l'utilisateur rend Idle : ce n'est pas un echec.
         */
        fun fromStatus(status: Int, message: String?): InstallState? = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> WaitingUser
            PackageInstaller.STATUS_SUCCESS -> null
            PackageInstaller.STATUS_FAILURE_ABORTED -> Idle
            else -> Failed(message)
        }

        /**
         * L'etat une fois de retour dans l'appli : la fenetre de confirmation d'Android est fermee, donc
         * l'utilisateur a repondu, et le systeme installe. Il n'envoie aucun rapport de cette transition ;
         * sans cette deduction, la progression restait sur "Confirmez l'installation" pendant toute l'installation.
         */
        fun afterReturn(state: InstallState): InstallState = if (state is WaitingUser) Installing else state
    }
}

/** Que faire d'une installation restee sans nouvelles du systeme. */
enum class StallAction { RETRY, GIVE_UP }

/** L'installation de la mise a jour par session `PackageInstaller`, et son etat observable. */
object UpdateInstaller {
    /**
     * Delai, une fois l'installation confirmee, passe lequel on la tient pour figee. Une installation qui
     * aboutit remplace l'appli en une quinzaine de secondes ; quand le delai de verification du systeme expire
     * pendant que Play Protect attend un choix, l'installation reste suspendue sans jamais rapporter d'echec.
     */
    const val STALL_TIMEOUT_MS = 60_000L

    /** Une installation figee se relance [maxRetries] fois (l'analyse de Play Protect est alors en cache), puis on y renonce. */
    fun onStall(attempt: Int, maxRetries: Int = 1): StallAction =
        if (attempt < maxRetries) StallAction.RETRY else StallAction.GIVE_UP

    private const val ACTION_STATUS = "fr.lc4918.trailog.update.INSTALL_STATUS"

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    /** La session ouverte, pour l'abandonner si l'utilisateur renonce ; -1 sinon. */
    @Volatile private var sessionId = -1

    fun set(state: InstallState) { _state.value = state }

    /**
     * La part [downloaded]/[total] d'un telechargement, de 0 a 1, ou null tant que la taille n'est pas
     * connue (DownloadManager rend -1 avant la reponse du serveur) : il n'y a alors rien a mesurer.
     */
    fun progressFraction(downloaded: Long, total: Long): Float? =
        if (total <= 0L) null else (downloaded.toFloat() / total).coerceIn(0f, 1f)

    /** L'avancement du telechargement [downloadId] (cf. [progressFraction]), ou null s'il est inconnu. */
    fun downloadProgress(context: Context, downloadId: Long): Float? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
            if (!c.moveToFirst()) return null
            val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return progressFraction(downloaded, total)
        }
    }

    /** Le telechargement [downloadId] est-il arrive entier ? Un echec ne doit pas mener a l'installateur. */
    fun downloadSucceeded(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
            if (!c.moveToFirst()) return false
            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    /**
     * Copie l'APK telecharge dans une session et la remet au systeme. L'issue arrive ensuite par
     * [UpdateInstallReceiver], dans [state].
     */
    suspend fun install(context: Context, downloadId: Long) {
        val app = context.applicationContext
        set(InstallState.Staging)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    // Une mise a jour de soi-meme n'a pas a redemander l'accord de l'utilisateur quand le
                    // systeme sait s'en passer (Android 12 et apres) ; sinon il redemande, et on le suit.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val id = installer.createSession(params)
                sessionId = id
                installer.openSession(id).use { session ->
                    val source = dm.openDownloadedFile(downloadId)
                    ParcelFileDescriptorStream(source).use { input ->
                        session.openWrite("trailog-update.apk", 0, source.statSize).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    session.commit(statusIntentSender(app, id))
                }
            }
        }
        if (result.isSuccess) set(InstallState.Installing)
        else {
            abandon(app)
            set(InstallState.Failed(result.exceptionOrNull()?.message))
        }
    }

    /** Abandonne la session en cours, s'il y en a une : c'est ce qui rend a l'utilisateur la main sur une installation figee. */
    fun abandon(context: Context) {
        val id = sessionId
        sessionId = -1
        if (id >= 0) runCatching { context.applicationContext.packageManager.packageInstaller.abandonSession(id) }
    }

    /** Le rapport du systeme sur la session : voir [UpdateInstallReceiver]. */
    internal fun onReport(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // La fenetre de confirmation d'Android : c'est a nous de l'ouvrir.
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        InstallState.fromStatus(status, message)?.let { set(it) }
    }

    private fun statusIntentSender(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_STATUS),
        // MUTABLE : le systeme y ajoute l'etat de la session.
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
    ).intentSender

    /** Le flux d'un fichier recu de DownloadManager, ferme avec lui. */
    private class ParcelFileDescriptorStream(pfd: android.os.ParcelFileDescriptor) :
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
}

/** Recoit les rapports de la session d'installation (declare au manifeste, non exporte). */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = UpdateInstaller.onReport(context, intent)
}
