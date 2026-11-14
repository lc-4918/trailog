package com.tunturo.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tunturo.app.BuildConfig
import com.tunturo.app.R
import com.tunturo.app.data.db.SettingsEntity
import com.tunturo.app.ui.routes.TestTunturoApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le mode expert des reglages : ses options restent cachees, sept appuis sur le titre les montrent - une
 * alerte le dit -, et sept autres les cachent de nouveau. Le reglage est enregistre en base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr", application = TestTunturoApp::class)
class ExpertModeUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<TestTunturoApp>()

    private fun present(res: Int) =
        compose.onAllNodesWithText(app.getString(res), substring = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Sept appuis sur le titre "Reglages".
     *
     * **Le titre s'attend avant de le viser.** `setContent` puis `waitForIdle` ne garantissent pas que
     * l'ecran soit la : ses reglages viennent de la base, et la barre du haut ne parait qu'a leur arrivee.
     * Taper aussitot visait un noeud qui n'existait pas encore, une fois sur deux.
     */
    private fun septAppuis() {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        repeat(ExpertTaps.TAPS) {
            compose.onNodeWithTag("settings_title", useUnmergedTree = true).performClick()
        }
    }

    private fun avatars() =
        compose.onAllNodesWithTag("settings_avatar", useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * Amene a l'ecran la ligne portant ce texte, et attend qu'elle y soit.
     *
     * **Necessaire depuis que le contenu des onglets est une liste paresseuse** : ce qui est hors de
     * l'ecran n'est pas compose, donc n'existe pas pour un test. Chercher un reglage sans y avoir defile
     * ne rend plus rien - ce qui est le prix, et le principe, de la liste.
     */
    private fun defileVers(texte: String) {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(texte, substring = true))
        compose.waitForIdle()
    }

    /** Un appui sur l'avatar, une fois l'ecran compose : meme attente, meme raison (cf. [septAppuis]). */
    private fun unAppui() {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onNodeWithTag("settings_avatar", useUnmergedTree = true).performClick()
    }

    @Test fun `sept appuis sur le titre montrent puis cachent les options expertes`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        val expert = R.string.settings_sw_gps_last_fix
        // Hors mode expert, la rubrique "Suivi de la position" entiere est cachee : on se place sur le
        // repere de position, la rubrique voisine, toujours visible.
        defileVers(app.getString(R.string.settings_section_gps_marker).uppercase())
        assertTrue("cachee hors mode expert", !present(expert))
        assertTrue("suivi de la position cache", !present(R.string.settings_sw_gps_recenter))

        septAppuis()
        defileVers(app.getString(R.string.settings_sw_gps_recenter))
        compose.waitUntil(5_000) { present(expert) }
        assertTrue("l'alerte le dit", present(R.string.settings_expert_on))
        assertTrue("enregistre en base", runBlocking { app.repository.settings.get()!!.expertMode })

        septAppuis()
        defileVers(app.getString(R.string.settings_section_gps_marker).uppercase())
        compose.waitUntil(5_000) { !present(expert) }
        assertTrue(present(R.string.settings_expert_off))
    }

    /** Ecran des reglages avec le tableau de bord allume (ses champs ne s'affichent que dans ce cas). */
    private fun carteAvecTableauDeBord(expert: Boolean) {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = expert, offTrackAlertEnabled = true))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    /** Onglet Carte : champs du tableau de bord, points d'interet et profil altimetrique caches hors mode expert. */
    @Test fun `la carte cache ses groupes experts hors mode expert`() {
        carteAvecTableauDeBord(expert = false)
        // Le groupe du tableau de bord est le dernier hors mode expert : en y defilant, on est en bout de liste.
        defileVers(app.getString(R.string.settings_group_dashboard))
        fun presentText(texte: String) =
            compose.onAllNodesWithText(texte, substring = true).fetchSemanticsNodes().isNotEmpty()
        assertFalse("champs affiches", presentText(app.getString(R.string.settings_section_dashboard_fields).uppercase()))
        // Le titre du groupe est aussi le libelle de l'interrupteur de Boutons et gestes : on vise son contenu.
        assertFalse("points d'interet", present(R.string.settings_label_bubble_position))
        assertFalse("profil altimetrique", present(R.string.settings_group_elevation_profile))
    }

    @Test fun `la carte montre ses groupes experts en mode expert`() {
        carteAvecTableauDeBord(expert = true)
        defileVers(app.getString(R.string.settings_section_dashboard_fields).uppercase())
        defileVers(app.getString(R.string.settings_group_pois))
        defileVers(app.getString(R.string.settings_group_elevation_profile))
        assertTrue("profil altimetrique", present(R.string.settings_group_elevation_profile))
    }

    /** Ouvre l'onglet portant ce libelle, une fois l'ecran compose. */
    private fun onglet(res: Int) {
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onAllNodesWithText(app.getString(res))[0].performClick()
        compose.waitForIdle()
    }

    /** Ecran des reglages, mode expert allume ou eteint en base. */
    private fun reglagesEnMode(expert: Boolean) {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = expert, poiEnabled = true))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    /**
     * Trajets : l'altimetrie manquante et le complement OpenStreetMap. Systeme : dossiers, sauvegarde et
     * avatar. Tous caches hors mode expert.
     */
    @Test fun `trajets et systeme cachent leurs reglages experts`() {
        reglagesEnMode(expert = false)
        onglet(R.string.settings_tab_routes)
        assertFalse("altimetrie manquante", present(R.string.settings_label_fill_elevation))
        assertFalse("complement OpenStreetMap", present(R.string.settings_sw_poi_osm))
        onglet(R.string.settings_tab_general)
        assertFalse("dossiers", present(R.string.settings_section_import_folder))
        assertFalse("sauvegarde", present(R.string.settings_backup_create))
        assertFalse("avatar", present(R.string.settings_label_avatar))
    }

    @Test fun `trajets et systeme montrent leurs reglages experts en mode expert`() {
        reglagesEnMode(expert = true)
        onglet(R.string.settings_tab_routes)
        assertTrue("altimetrie manquante", present(R.string.settings_label_fill_elevation))
        assertTrue("complement OpenStreetMap", present(R.string.settings_sw_poi_osm))
        onglet(R.string.settings_tab_general)
        assertTrue("dossiers", present(R.string.settings_section_import_folder))
        assertTrue("sauvegarde", present(R.string.settings_backup_create))
        assertTrue("avatar", present(R.string.settings_label_avatar))
    }

    /** L'affichage de la carte (echelle, rotation, fonds) n'apparait qu'en mode expert. */
    @Test fun `l'affichage de la carte ne s'ouvre qu'en mode expert`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        defileVers(app.getString(R.string.settings_sw_track_follow))
        assertFalse("cachee hors mode expert", present(R.string.settings_sw_scale))

        septAppuis()
        defileVers(app.getString(R.string.settings_sw_scale))
        assertTrue("affichee en mode expert", present(R.string.settings_sw_scale))
    }

    /**
     * Le titre du groupe courant se pose sous les onglets des que son titre est passe en haut, et se
     * retire quand on remonte. Sans lui, on regle des curseurs sans savoir a quoi ils se rapportent.
     */
    @Test fun `le groupe courant reste affiche en defilant`() {
        runBlocking { app.repository.ensureSeed() }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { avatars().isNotEmpty() }
        compose.onAllNodesWithTag("settings_group_bar").assertCountEquals(0)

        // On descend jusqu'a un reglage du groupe GPS : son titre est alors passe au-dessus du bord.
        defileVers(app.getString(R.string.settings_section_gps_marker).uppercase())
        compose.onAllNodesWithTag("settings_group_bar").assertCountEquals(1)
    }

    /** Un tap sur l'avatar ouvre son menu : "A propos" et "Aide". */
    @Test fun `l'avatar porte un menu`() {
        runBlocking { app.repository.ensureSeed() }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("menu_about").assertCountEquals(0)

        unAppui()
        compose.waitForIdle()
        compose.onAllNodesWithTag("menu_about").assertCountEquals(1)
        compose.onAllNodesWithTag("menu_help").assertCountEquals(1)
    }

    /** "A propos" dit la version installee et ou signaler un probleme. */
    @Test fun `a propos dit la version et le lien`() {
        runBlocking { app.repository.ensureSeed() }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        unAppui()
        compose.onNodeWithTag("menu_about").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.about_version, BuildConfig.VERSION_NAME)).assertIsDisplayed()
        compose.onNodeWithTag("about_issues_link").assertIsDisplayed()
    }

    /** L'avatar n'allume plus le mode expert : ses appuis ouvrent son menu, et rien d'autre. */
    @Test fun `sept appuis sur l'avatar ne touchent pas au mode expert`() {
        runBlocking {
            app.repository.ensureSeed()
            val s = app.repository.settings.get() ?: SettingsEntity()
            app.repository.settings.upsert(s.copy(expertMode = false))
        }
        compose.setContent { MaterialTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        repeat(ExpertTaps.TAPS) { unAppui() }
        compose.waitForIdle()
        assertTrue(!runBlocking { app.repository.settings.get()!!.expertMode })
        assertTrue(!present(R.string.settings_expert_on))
    }
}
