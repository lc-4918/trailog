package fr.lc4918.trailog.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Menage des APK de mise a jour laisses sur le disque.
 *
 * La faute qu'il repare ne casse rien et ne se voit nulle part dans l'application : DownloadManager depose
 * l'APK dans le dossier prive, rien ne l'en retirait, et il en restait UN PAR MISE A JOUR. Releve sur
 * l'appareil avant correction : trois APK, 176 Mo, pres de trois fois le poids de l'application.
 */
@RunWith(RobolectricTestRunner::class)
class SweepDownloadsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()

    private fun poser(nom: String, octets: Int = 1024): File {
        val f = File(ctx.getExternalFilesDir(null), nom)
        f.parentFile?.mkdirs()
        f.writeBytes(ByteArray(octets))
        return f
    }

    private fun nettoyer() {
        ctx.getExternalFilesDir(null)?.listFiles()?.forEach { it.delete() }
    }

    // ---------- Lecture du nom de fichier ----------

    /** Meme calcul que build.gradle.kts et que la CI : maj*10000 + min*100 + correctif. */
    @Test fun `le nom de fichier porte le versionCode`() {
        assertEquals(10000, UpdateManager.versionCodeOf("trailog-1.0.0.apk"))
        assertEquals(1000, UpdateManager.versionCodeOf("trailog-0.10.0.apk"))
        assertEquals(900, UpdateManager.versionCodeOf("trailog-0.9.0.apk"))
    }

    /** DownloadManager suffixe le nom quand il evite d'ecraser un fichier de meme nom - c'est ainsi qu'on
     *  se retrouve avec un "trailog-0.9.0-1.apk" a cote d'un "trailog-0.9.0.apk". */
    @Test fun `le suffixe de doublon de DownloadManager est tolere`() {
        assertEquals(900, UpdateManager.versionCodeOf("trailog-0.9.0-1.apk"))
    }

    /** On ne supprime pas ce qu'on n'a pas depose : un nom qu'on ne comprend pas n'est pas a nous. */
    @Test fun `un nom etranger n'est pas reconnu`() {
        assertNull(UpdateManager.versionCodeOf("trace.gpx"))
        assertNull(UpdateManager.versionCodeOf("autre-1.0.0.apk"))
        assertNull(UpdateManager.versionCodeOf("trailog-.apk"))
        assertNull(UpdateManager.versionCodeOf("trailog-1.0.apk"))
    }

    // ---------- Menage ----------

    /** L'APK qui vient d'etre installe porte la version qui tourne : il n'a plus aucune raison d'etre la. */
    @Test fun `l'apk de la version installee est supprime`() {
        nettoyer()
        val f = poser("trailog-1.0.0.apk", 2048)
        assertEquals(2048L, UpdateManager.sweepDownloads(ctx, currentVersionCode = 10000))
        assertFalse("l'apk aurait du partir", f.exists())
    }

    @Test fun `les versions precedentes partent aussi`() {
        nettoyer()
        poser("trailog-0.9.0.apk", 100)
        poser("trailog-0.9.0-1.apk", 100)
        poser("trailog-0.10.0.apk", 100)
        assertEquals(300L, UpdateManager.sweepDownloads(ctx, currentVersionCode = 1000))
        assertEquals(0, ctx.getExternalFilesDir(null)!!.listFiles()!!.size)
    }

    /**
     * Mais un telechargement d'une version PLUS RECENTE est garde : c'est celui qu'on a demande et qu'on
     * n'a pas encore installe. Le jeter obligerait a reprendre soixante megaoctets.
     */
    @Test fun `un telechargement pas encore installe est garde`() {
        nettoyer()
        val futur = poser("trailog-1.1.0.apk")
        assertEquals(0L, UpdateManager.sweepDownloads(ctx, currentVersionCode = 10000))
        assertTrue("le telechargement en attente a ete jete", futur.exists())
    }

    /** Le dossier prive porte aussi autre chose : rien de ce qui n'est pas un APK de l'application ne doit
     *  disparaitre, et surtout pas les fichiers de l'utilisateur. */
    @Test fun `le menage ne touche qu'aux apk de l'application`() {
        nettoyer()
        val trace = poser("ma-trace.gpx")
        val autre = poser("autre-0.1.0.apk")
        poser("trailog-0.9.0.apk", 512)
        assertEquals(512L, UpdateManager.sweepDownloads(ctx, currentVersionCode = 10000))
        assertTrue(trace.exists())
        assertTrue(autre.exists())
    }

    @Test fun `un dossier vide ne libere rien et ne leve pas`() {
        nettoyer()
        assertEquals(0L, UpdateManager.sweepDownloads(ctx, currentVersionCode = 10000))
    }
}
