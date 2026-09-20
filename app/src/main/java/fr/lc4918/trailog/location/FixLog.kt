package fr.lc4918.trailog.location

import android.content.Context
import android.os.Build
import android.os.SystemClock
import fr.lc4918.trailog.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Le journal du suivi de position : ce que le service a recu, et quand il n'a rien recu.
 *
 * **Pourquoi il existe.** Le defaut qu'on cherche ne se produit que la ou personne ne regarde -
 * telephone en poche, ecran eteint, a vingt kilometres de l'ordinateur. Trois sorties ont ete
 * necessaires pour etablir ce que le mode economie d'energie faisait au GPS, faute de pouvoir lire
 * quoi que ce soit au retour. Ce fichier est la reponse : il survit a la sortie, et se lit branche.
 *
 * **Seulement en build debug** ([BuildConfig.DEBUG]). Un journal qui ecrit toutes les deux secondes
 * n'a rien a faire chez quelqu'un qui n'a rien demande - ni l'ecriture, ni le fichier qui grossit,
 * ni ce qu'il contient : des positions horodatees sont ce que l'application a de plus intime. Le
 * build de test est justement celui qui porte les correctifs a verifier.
 *
 * **Un seul fil d'ecriture**, et non le fil appelant : les positions arrivent sur le fil principal,
 * toutes les deux secondes, et une ecriture disque n'y a pas sa place. Un executeur a un seul fil
 * garantit en outre l'ORDRE des lignes, qu'un lancement de coroutines ne garantirait pas - un
 * journal dans le desordre ne prouve rien de ce qu'on lui demande.
 */
object FixLog {

    /** Le journal n'existe qu'en debug. Lu par les appelants pour ne rien construire pour rien. */
    val enabled: Boolean get() = BuildConfig.DEBUG

    const val FILE_NAME = "diagnostic-suivi.log"

    /** Au-dela, le journal passe en `.1` et un neuf commence : une sortie longue ne doit rien effacer. */
    const val MAX_BYTES = 1_000_000L

    private val executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "trailog-fixlog") } }

    private val heure = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    /**
     * Une ligne de plus, horodatee a l'heure murale ET sur l'horloge monotone : la premiere se
     * compare a une montre, la seconde aux durees que le code manipule.
     *
     * [texte] est un producteur : en release il n'est jamais appele.
     */
    fun write(ctx: Context, texte: () -> String) {
        if (!enabled) return
        val ligne = line(System.currentTimeMillis(), SystemClock.elapsedRealtime(), texte())
        val f = file(ctx)
        executor.execute {
            runCatching {
                rotateIfNeeded(f, MAX_BYTES)
                f.appendText(ligne)
            }
        }
    }

    /** L'en-tete d'une sortie : ce qu'il faudra savoir pour relire tout ce qui suit. */
    fun start(ctx: Context, fournisseurs: List<String>, economie: Boolean, mode: Int) {
        write(ctx) {
            "--- suivi demarre - ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, " +
                "Trailog ${BuildConfig.VERSION_NAME} - economie=$economie location_mode=$mode - " +
                "fournisseurs=${fournisseurs.joinToString(",").ifEmpty { "aucun" }}"
        }
    }

    /** La mise en forme, a part : c'est la seule chose ici qui se verifie sans disque ni telephone. */
    fun line(wallMs: Long, elapsedMs: Long, texte: String): String =
        "${heure.format(Date(wallMs))} [${elapsedMs / 1000}s] $texte\n"

    /**
     * Le journal plein passe en `.1`, et un neuf commence. Rend vrai quand la rotation a eu lieu.
     *
     * Une seule generation gardee : deux suffisent a couvrir la sortie en cours et celle d'avant,
     * et un journal de diagnostic qui remplit le telephone se retourne contre ce qu'il sert.
     */
    fun rotateIfNeeded(f: File, maxBytes: Long): Boolean {
        if (!f.exists() || f.length() < maxBytes) return false
        val vieux = File(f.parentFile, "${f.name}.1")
        vieux.delete()
        return f.renameTo(vieux)
    }
}
