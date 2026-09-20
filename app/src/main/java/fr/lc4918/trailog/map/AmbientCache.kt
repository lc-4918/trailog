package fr.lc4918.trailog.map

import android.content.Context
import org.maplibre.android.offline.OfflineManager
import java.io.File

/**
 * La taille du cache de tuiles de MapLibre, celle que les reglages annoncent.
 *
 * **Le reglage existait, s'affichait, et ne commandait rien.** `SettingsEntity.ambientCacheMb` vaut 200 Mo
 * par defaut et vit dans la base depuis longtemps ; `MapLibre.getInstance` etait appele nu, et la
 * bibliotheque restait donc sur son propre defaut - **50 Mo**. Un reglage qu'on affiche et qui ne fait rien
 * est pire qu'un reglage absent : on le regle, on croit avoir agi, et rien ne change.
 *
 * **Ce que la taille change, et ce qu'elle ne change pas.** Elle borne ce que le cache RETIENT : au-dela,
 * les tuiles les moins recemment vues sont evincees, et revenir sur une zone d'il y a trois jours la fait
 * retelecharger. A quinze kilo-octets la tuile, 50 Mo tiennent environ 3 300 tuiles - une region traversee
 * sur quatre ou cinq niveaux de zoom en depasse largement.
 *
 * Elle ne change en revanche RIEN a la peremption : une tuile gardee mais perimee est revalidee aupres du
 * serveur, et les fournisseurs sont avares - `max-age=2232` chez Google, soit 37 minutes ; 4 h 35 chez
 * OpenStreetMap. C'est un autre mecanisme, et il ne se corrige pas ici.
 */
object AmbientCache {

    /** Octets par mebioctet : le reglage se compte en Mo, l'API en octets. */
    private const val MB = 1024L * 1024L

    /**
     * Applique la taille reglee, en mebioctets.
     *
     * Silencieux en cas d'echec, et volontairement : c'est un reglage de confort, appele au demarrage de
     * la carte. Une bibliotheque native qui refuse ne doit pas empecher la carte de s'afficher - au pire,
     * elle garde le defaut qu'elle avait.
     */
    fun apply(context: Context, sizeMb: Int) {
        if (sizeMb <= 0) return
        runCatching {
            // Le rappel ne sert a rien ici - reussite ou echec, la carte s'affiche pareil - mais l'API
            // l'exige, et un objet vide dit plus clairement qu'on n'en attend rien qu'un lambda.
            OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
                sizeMb * MB,
                object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() = Unit
                    override fun onError(message: String) = Unit
                },
            )
        }
    }

    /**
     * Le poids du cache sur le disque.
     *
     * Lu sur les FICHIERS et non demande a la bibliotheque : elle ne dit pas ce qu'elle occupe, seulement
     * ce qu'elle s'autorise. Or c'est le poids reel qu'on vient voir avant de l'effacer.
     */
    fun sizeBytes(ctx: Context): Long =
        ctx.filesDir.listFiles()?.filter { isCacheFile(it.name) }?.sumOf { it.length() } ?: 0L

    /**
     * Les fichiers du cache de tuiles, parmi ceux de l'application : la base de MapLibre et ses journaux.
     *
     * Les cartes hors ligne n'en sont pas : ce sont nos propres MBTiles, ailleurs (cf. TrailogRepository).
     */
    fun isCacheFile(name: String): Boolean = name.startsWith("mbgl-")

    /**
     * Efface les tuiles gardees. Les cartes hors ligne ne sont pas touchees - elles ne sont pas la.
     *
     * [onDone] est appele dans tous les cas : le compteur affiche doit se relire meme si la bibliotheque
     * a refuse, sans quoi l'ecran annoncerait un poids qui n'est plus le bon.
     */
    fun clear(ctx: Context, onDone: () -> Unit) {
        val ok = runCatching {
            OfflineManager.getInstance(ctx).clearAmbientCache(object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = onDone()
                override fun onError(message: String) = onDone()
            })
        }.isSuccess
        if (!ok) onDone()
    }

    /** Ce que le cache occupe, sans Android : la somme de ses fichiers (cf. [isCacheFile]). */
    fun sizeOf(files: List<File>): Long = files.filter { isCacheFile(it.name) }.sumOf { it.length() }
}
