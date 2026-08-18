package fr.lc4918.trailog

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.repo.TrailogRepository
import fr.lc4918.trailog.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class TrailogApp : Application(), SingletonImageLoader.Factory {
    lateinit var repository: TrailogRepository
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocalePrefs.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)               // init du SDK carte
        repository = TrailogRepository(this)
        scope.launch { repository.ensureSeed() }  // providers + réglages au 1er lancement
        // Ménage des APK de mise à jour : DownloadManager les dépose dans le dossier privé de
        // l'application et rien ne les en retirait, si bien qu'il en restait un par mise à jour -
        // 176 Mo relevés sur un téléphone, presque trois fois le poids de l'application. Au démarrage
        // et non après l'installation : à ce moment-là l'installateur système lit encore le fichier, et
        // l'application est de toute façon remplacée puis relancée (cf. UpdateManager.sweepDownloads).
        scope.launch { UpdateManager.sweepDownloads(this@TrailogApp) }
    }

    /** Loader d'images partagé (avatar + champs image des infobulles) : SVG, GIF, chargement réseau. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(GifDecoder.Factory())
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
}
