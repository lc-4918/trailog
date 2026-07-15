package fr.lc4918.trailog.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import fr.lc4918.trailog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Contenu de latest-release.json, publie par la CI a chaque tag (cf. .github/workflows/build-release.yml). */
@Serializable
data class ReleaseInfo(
    val version: String,
    val versionCode: Int,
    val releaseDate: String,
    val apkUrl: String,
    val changelog: String = "",
)

/** Issue d'une verification, pour que l'appelant distingue "rien de neuf" d'un echec reseau. */
sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * Verification et installation des mises a jour, hors store : l'app lit le manifeste publie par la CI sur
 * main, compare a sa propre version, puis telecharge et lance l'installateur systeme.
 */
object UpdateManager {
    /** Servi par le CDN de GitHub : pas de quota, contrairement a l'API (60 requetes/h par IP). */
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/lc-4918/trailog/main/latest-release.json"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Faux en debug : ce build a son propre applicationId et une autre signature, donc l'APK de release ne
     * peut pas le remplacer (Android refuse), et son versionName suffixe "-debug" fausserait la comparaison.
     */
    val isSupported: Boolean get() = !BuildConfig.DEBUG

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        if (!isSupported) return@withContext UpdateCheck.UpToDate
        val body = fetch(MANIFEST_URL) ?: return@withContext UpdateCheck.Failed
        val release = runCatching { json.decodeFromString<ReleaseInfo>(body) }.getOrNull()
            ?: return@withContext UpdateCheck.Failed
        // Compare les versionCode : entiers derives du tag par build.gradle.kts (maj*10000 + min*100 +
        // patch), la ou comparer les versionName obligerait a reparser une chaine dont les builds de dev
        // portent un suffixe ("0.1.2-23-gabc1234") qui se lirait comme une version plus recente.
        if (release.versionCode > BuildConfig.VERSION_CODE) UpdateCheck.Available(release)
        else UpdateCheck.UpToDate
    }

    private fun fetch(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Trailog/${BuildConfig.VERSION_NAME} (Android)")
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.reader().readText() } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Met l'APK en file de telechargement et rend son id. Destination : le dossier prive de l'app, ce qui
     * evite toute permission de stockage et le FileProvider, DownloadManager sachant rendre un URI
     * installable pour ses propres fichiers (cf. [installIntent]).
     */
    fun enqueueDownload(context: Context, release: ReleaseInfo, title: String, description: String): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(release.apkUrl.toUri()).apply {
            setTitle(title)
            setDescription(description)
            setDestinationInExternalFilesDir(context, null, "trailog-${release.version}.apk")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType(APK_MIME)
        }
        return dm.enqueue(request)
    }

    /** Intent d'installation pour un APK telecharge, ou null si l'id n'est pas (ou plus) connu. */
    fun installIntent(context: Context, downloadId: Long): Intent? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri: Uri = dm.getUriForDownloadedFile(downloadId) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    /**
     * Depuis Android 8, l'autorisation d'installer est accordee par application : sans elle, l'installateur
     * s'ouvre et echoue sans rien expliquer. On verifie donc avant, quitte a envoyer l'utilisateur au reglage.
     * Avant Android 8 (minSdk = 24), l'autorisation est un reglage systeme global sans equivalent par app :
     * rien a verifier ici, l'installateur s'en charge.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Reglage par app depuis Android 8 ; avant, seul l'ecran de securite global existe. */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    private const val APK_MIME = "application/vnd.android.package-archive"
}
