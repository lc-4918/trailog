package fr.lc4918.trailog.ui.routes

import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.data.db.AppDatabase
import fr.lc4918.trailog.data.db.SettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * Une ecriture des reglages partie AVANT que le ViewModel ait lu la base.
 *
 * Au premier lancement apres une mise a jour, la migration retarde la lecture de la base ; le ViewModel
 * tient alors des reglages par defaut. Ses ecritures partaient de ces defauts et ecrasaient la vraie ligne :
 * les reglages de l'utilisateur, et le drapeau du jeu de demonstration - d'ou un dossier Demo de plus a
 * chaque release. Elles relisent desormais la ligne en base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTrailogApp::class)
class SettingsWriteRaceTest {

    private val app = ApplicationProvider.getApplicationContext<TestTrailogApp>()
    private val db = AppDatabase.get(app)

    @Test fun `une ecriture avant la lecture de la base ne remet pas les reglages aux defauts`() {
        val reglages = SettingsEntity(customTitle = "Mes sorties", demoSeeded = true, units = "imperial",
            mapFollowPosition = true)
        runBlocking { db.settings().upsert(reglages) }
        // Le ViewModel vient de naitre : ses reglages sont encore les defauts, rien n'a ete lu.
        val vm = MainViewModel(app)
        vm.setMapFollowPosition(false)
        // Laisse tout s'executer : l'ecriture, et la lecture du flux.
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val apres = runBlocking { db.settings().get() }!!
        assertEquals("le reglage demande est ecrit", false, apres.mapFollowPosition)
        assertEquals("Mes sorties", apres.customTitle)
        assertEquals("imperial", apres.units)
        assertTrue("le drapeau de la demonstration survit", apres.demoSeeded)
    }
}
