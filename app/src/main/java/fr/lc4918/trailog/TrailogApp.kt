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
