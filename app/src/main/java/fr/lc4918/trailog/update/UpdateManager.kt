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
    /**
     * Une URL par architecture, depuis que la CI publie un APK par ABI (cf. le bloc `splits` de
     * build.gradle.kts). Absent des manifestes d'avant, d'ou le defaut vide.
     */
    val apkUrls: Map<String, String> = emptyMap(),
    val changelog: String = "",
) {
    /**
     * L'APK a telecharger pour un appareil qui execute [abis], par ordre de preference
     * (`Build.SUPPORTED_ABIS`).
     *
     * Un APK par architecture divise le telechargement par deux - 61,5 Mo en universel contre une
     * trentaine pour l'architecture seule, le code natif de MapLibre pesant 71 % du total. Le repli sur
     * [apkUrl], l'APK universel, couvre deux cas : un manifeste d'avant les splits, et une architecture
     * qu'on ne publierait pas. Mieux vaut telecharger trop que rien.
     */
    fun urlFor(abis: List<String>): String = abis.firstNotNullOfOrNull { apkUrls[it] } ?: apkUrl
}

/** Issue d'une verification, pour que l'appelant distingue "rien de neuf" d'un echec reseau. */
sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * Verification et installation des mises a jour, hors store : l'app lit le manifeste publie par la CI en
 * asset de la Release, compare a sa propre version, puis telecharge et lance l'installateur systeme.
 */
object UpdateManager {
    /**
     * URL stable : GitHub redirige (302) vers l'asset de la derniere release non-prerelease, servi par son
     * CDN, donc pas de quota contrairement a l'API (60 requetes/h par IP). Suivre les redirections est
     * indispensable ici (cf. instanceFollowRedirects plus bas).
     */
    private const val MANIFEST_URL =
        "https://github.com/lc-4918/trailog/releases/latest/download/latest-release.json"
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
        val url = release.urlFor(Build.SUPPORTED_ABIS.orEmpty().toList())
        val request = DownloadManager.Request(url.toUri()).apply {
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

    /**
     * Nom des APK deposes par [enqueueDownload] : "trailog-0.10.0.apk", et parfois "trailog-0.10.0-1.apk"
     * quand DownloadManager evite d'ecraser un fichier de meme nom.
     */
    private val APK_NAME = Regex("""^trailog-(\d+)\.(\d+)\.(\d+).*\.apk$""")

    /**
     * Le versionCode que porte le nom de fichier [name], ou null si ce n'en est pas un.
     *
     * Meme calcul que build.gradle.kts et que la CI - majeur*10000 + mineur*100 + correctif - et c'est la
     * troisieme fois qu'il est ecrit. Le repeter n'est pas beau, mais les trois vivent dans des langages
     * differents et aucun ne peut lire les deux autres.
     *
     * Null plutot qu'une valeur par defaut : un fichier dont on ne comprend pas le nom n'est pas a nous, et
     * on ne supprime pas ce qu'on n'a pas depose.
     */
    internal fun versionCodeOf(name: String): Int? {
        val m = APK_NAME.matchEntire(name) ?: return null
        val (maj, min, patch) = m.destructured
        return maj.toInt() * 10000 + min.toInt() * 100 + patch.toInt()
    }

    /**
     * Supprime les APK de mise a jour devenus inutiles et rend le nombre d'octets liberes.
     *
     * Sans ce menage, il en reste UN PAR MISE A JOUR, indefiniment : DownloadManager depose l'APK dans le
     * dossier prive de l'application et rien ne l'en retire, l'installation faite. Mesure sur l'appareil
     * avant correction - trois APK, 176 Mo, soit pres de trois fois le poids de l'application, dont deux
     * d'une version abandonnee depuis six jours.
     *
     * Est supprime ce qui n'est pas plus recent que la version qui tourne : l'APK qui vient d'etre installe
     * (meme version) et tous les precedents. Un telechargement d'une version PLUS RECENTE est garde -
     * c'est celui qu'on a demande et qu'on n'a pas encore installe, le jeter obligerait a le reprendre.
     */
    fun sweepDownloads(context: Context, currentVersionCode: Int = BuildConfig.VERSION_CODE): Long {
        val dossier = context.getExternalFilesDir(null) ?: return 0L
        var liberes = 0L
        dossier.listFiles().orEmpty().forEach { f ->
            val code = versionCodeOf(f.name) ?: return@forEach
            if (code > currentVersionCode) return@forEach
            val taille = f.length()
            if (f.delete()) liberes += taille
        }
        return liberes
    }
}
