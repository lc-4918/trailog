package fr.lc4918.trailog

import fr.lc4918.trailog.routing.BrouterLocal
import fr.lc4918.trailog.routing.offline.BrouterDownloadService
import fr.lc4918.trailog.routing.offline.BrouterDownloads
import fr.lc4918.trailog.routing.offline.BrouterSegments
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

/**
 * Ouverte, avec deux points d'entree separes du reste : c'est ce qui permet a un test d'interface de
 * monter la VRAIE application - meme depot, memes reglages, meme ViewModel - sans les deux choses qui
 * l'en empechaient.
 *
 * [initMapEngine] isole le seul appel natif : les bibliotheques de MapLibre ne se chargent pas sur la JVM
 * (cf. `MapSurface`).
 *
 * [startBackgroundWork] isole ce que le demarrage lance en tache de fond. Le semis y ecrit les reglages
 * s'ils manquent, sur un autre thread et a un moment qu'on ne choisit pas : un test qui pose les siens
 * juste apres court contre lui, et perd une fois sur dix. Le test appelle donc `ensureSeed` lui-meme,
 * puis ecrit - dans cet ordre, et sans course.
 */
open class TrailogApp : Application(), SingletonImageLoader.Factory {
    lateinit var repository: TrailogRepository
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Les donnees de BRouter pour le calcul hors ligne, et leur file de telechargement.
     *
     * Ici et non dans un ecran : un transfert de deux cents megaoctets doit survivre a l'ecran qui l'a
     * lance, et les reglages comme le telechargement d'une zone passent par la meme file (cf.
     * BrouterDownloads).
     */
    val brouterData: BrouterDownloads by lazy {
        BrouterDownloads(BrouterSegments({ BrouterLocal.segmentDir(this) }), scope,
            onBusy = { BrouterDownloadService.start(this) })
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocalePrefs.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        initMapEngine()
        repository = TrailogRepository(this)
        startBackgroundWork()
    }

    /** Ce que le demarrage lance en tache de fond. Separee et ouverte : voir la note de classe. */
    protected open fun startBackgroundWork() {
        scope.launch { repository.ensureSeed() }  // providers + réglages au 1er lancement
        // Ménage des APK de mise à jour : DownloadManager les dépose dans le dossier privé de
        // l'application et rien ne les en retirait, si bien qu'il en restait un par mise à jour -
        // 176 Mo relevés sur un téléphone, presque trois fois le poids de l'application. Au démarrage
        // et non après l'installation : à ce moment-là l'installateur système lit encore le fichier, et
        // l'application est de toute façon remplacée puis relancée (cf. UpdateManager.sweepDownloads).
        scope.launch { UpdateManager.sweepDownloads(this@TrailogApp) }
        // Un telechargement de donnees d'itineraire interrompu par l'arret de l'application reprend au
        // lancement suivant (cf. BrouterDownloadService). Sous garde dans le service : un demarrage refuse
        // en arriere-plan ne fait que remettre la reprise a plus tard.
        scope.launch { if (brouterData.hasPendingOnDisk()) BrouterDownloadService.start(this@TrailogApp) }
    }

    /** Init du SDK carte. Separe et ouverte : voir la note de classe. */
    protected open fun initMapEngine() {
        MapLibre.getInstance(this)
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
