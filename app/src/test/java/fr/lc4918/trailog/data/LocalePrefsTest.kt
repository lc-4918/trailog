package fr.lc4918.trailog.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Langue de l'interface : lue avant la creation de la base, donc stockee a part en SharedPreferences. */
@RunWith(RobolectricTestRunner::class)
class LocalePrefsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun vider() {
        ctx.getSharedPreferences("locale_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `le francais est la langue par defaut`() {
        assertEquals("fr", LocalePrefs.get(ctx))
    }

    @Test fun `la langue choisie est relue`() {
        LocalePrefs.SELECTABLE.forEach {
            LocalePrefs.set(ctx, it)
            assertEquals(it, LocalePrefs.get(ctx))
        }
    }

    /** Valeur inconnue en base (retour arriere, corruption) : on retombe sur le defaut au lieu de planter. */
    @Test fun `une langue inconnue retombe sur le defaut`() {
        LocalePrefs.set(ctx, "klingon")
        assertEquals("fr", LocalePrefs.get(ctx))
    }

    /** "system" est une valeur historique : conservee telle quelle, elle reste un no-op dans wrap(). */
    @Test fun `la valeur historique system est conservee`() {
        LocalePrefs.set(ctx, "system")
        assertEquals("system", LocalePrefs.get(ctx))
    }

    @Test fun `les 8 langues traduites sont proposees`() {
        assertEquals(setOf("fr", "en", "es", "de", "it", "ca", "eu", "pt"), LocalePrefs.SELECTABLE.toSet())
    }

    @Test fun `chaque langue proposee a un nom natif et un drapeau`() {
        LocalePrefs.SELECTABLE.forEach {
            assertNotNull(it, LocalePrefs.nativeName(it))
            assertTrue("$it : nom natif vide", LocalePrefs.nativeName(it).isNotBlank())
            assertNotNull("$it : aucun drapeau", LocalePrefs.flagCode(it))
        }
    }

    @Test fun `un code inconnu rend le code lui-meme et aucun drapeau`() {
        assertEquals("zz", LocalePrefs.nativeName("zz"))
        assertNull(LocalePrefs.flagCode("zz"))
    }

    /** wrap() applique la langue au contexte : c'est ce que MainActivity appelle dans attachBaseContext. */
    @Test fun `wrap applique la langue choisie au contexte`() {
        LocalePrefs.set(ctx, "es")
        val w = LocalePrefs.wrap(ctx)
        assertEquals("es", w.resources.configuration.locales[0].language)
    }
}
