package fr.lc4918.trailog.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderExpansionPrefsTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clearPrefs() {
        context.getSharedPreferences("folder_expansion_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `a folder never touched is expanded`() {
        assertTrue(FolderExpansionPrefs.isExpanded(context, 1L))
    }

    @Test fun `a collapsed folder stays collapsed and others stay expanded`() {
        FolderExpansionPrefs.setExpanded(context, 1L, false)
        assertFalse(FolderExpansionPrefs.isExpanded(context, 1L))
        assertTrue(FolderExpansionPrefs.isExpanded(context, 2L))
    }

    @Test fun `expanding again forgets the collapsed state`() {
        FolderExpansionPrefs.setExpanded(context, 1L, false)
        FolderExpansionPrefs.setExpanded(context, 1L, true)
        assertTrue(FolderExpansionPrefs.isExpanded(context, 1L))
    }
}
