package fr.lc4918.trailog.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import fr.lc4918.trailog.ui.settings.settingsPaletteOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le theme de l'application : complet, lisible, et sans teinte dans le sombre. */
class ThemeTest {

    /** Chaque role du jeu, par son nom : un getter sans argument qui rend une couleur (un Long en JVM). */
    private fun roles(s: ColorScheme): Map<String, Color> = ColorScheme::class.java.methods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
        .associate { it.name.removePrefix("get").substringBefore('-') to Color((it.invoke(s) as Long).toULong()) }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /**
     * Aucun role ne reste a la valeur par defaut de Material - c'est ainsi que le violet s'etait glisse
     * dans les surfaces et les conteneurs. Le blanc et le noir purs peuvent y coincider, et les rouges
     * d'erreur en partie aussi, PAR CHOIX : ils ne teintent rien d'autre qu'une erreur.
     */
    @Test fun `aucun role ne garde la valeur de Material`() {
        val rougesVoulus = setOf("Error", "OnError", "ErrorContainer", "OnErrorContainer")
        for ((nom, ours, base) in listOf(
            Triple("clair", TrailogLight, lightColorScheme()),
            Triple("sombre", TrailogDark, darkColorScheme()),
        )) {
            val defauts = roles(base).values.filter { it != Color.White && it != Color.Black }.toSet()
            val restes = roles(ours).filter { (role, c) -> c in defauts && role !in rougesVoulus }
            assertTrue("$nom : ${restes.keys}", restes.isEmpty())
        }
    }

    /** Garde-fou du test precedent : si la lecture des roles ne trouvait rien, il passerait a vide. */
    @Test fun `la lecture des roles trouve les surfaces`() {
        val r = roles(TrailogLight)
        assertEquals(Color(0xFF16588F), r["Primary"])
        assertEquals(Color(0xFFF2F6FA), r["SurfaceContainerLow"])
        assertTrue(r.size >= 30)
    }

    /** Le sombre est NOIR, pas bleu nuit : fonds, textes et filets sont des gris sans teinte. */
    @Test fun `le sombre est fait de gris neutres`() {
        val d = TrailogDark
        for (c in listOf(
            d.background, d.surface, d.surfaceDim, d.surfaceBright, d.surfaceVariant,
            d.surfaceContainerLowest, d.surfaceContainerLow, d.surfaceContainer,
            d.surfaceContainerHigh, d.surfaceContainerHighest,
            d.onSurface, d.onSurfaceVariant, d.outline, d.outlineVariant,
        )) {
            val argb = c.toArgb()
            val r = argb shr 16 and 0xFF; val g = argb shr 8 and 0xFF; val b = argb and 0xFF
            assertTrue("%08X".format(argb), r == g && g == b)
        }
    }

    /** Tout texte pose sur son fond se lit a 4.5:1 au moins, dans les deux themes. */
    @Test fun `les textes se lisent sur leur fond`() {
        for ((nom, s) in listOf("clair" to TrailogLight, "sombre" to TrailogDark)) {
            val paires = listOf(
                "onPrimary/primary" to (s.onPrimary to s.primary),
                "onPrimaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                "onSecondary/secondary" to (s.onSecondary to s.secondary),
                "onSecondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
                "onTertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
                "onSurface/surface" to (s.onSurface to s.surface),
                "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
                "onSurfaceVariant/low" to (s.onSurfaceVariant to s.surfaceContainerLow),
                "onSurfaceVariant/high" to (s.onSurfaceVariant to s.surfaceContainerHigh),
                // Le libelle rouge de "Reinitialiser", pose a meme la bande.
                "error/surface" to (s.error to s.surface),
                "onError/error" to (s.onError to s.error),
                "onErrorContainer" to (s.onErrorContainer to s.errorContainer),
                "primary/surface" to (s.primary to s.surface),
                "inverseOnSurface" to (s.inverseOnSurface to s.inverseSurface),
                "inversePrimary" to (s.inversePrimary to s.inverseSurface),
            )
            for ((role, p) in paires) {
                val c = contrast(p.first, p.second)
                assertTrue("$nom $role : $c", c >= 4.5f)
            }
            // Un contour de champ doit se voir : 3:1, le seuil des elements d'interface.
            assertTrue("$nom outline", contrast(s.outline, s.surfaceContainerLow) >= 3f)
        }
    }

    /** Chaque style de texte est en Inter : aucun ne retombe sur la police du systeme. */
    @Test fun `tous les styles sont en Inter`() {
        val t = TrailogTypography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium,
            t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium,
            t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall,
        )
        styles.forEach { assertEquals(InterFamily, it.fontFamily) }
        // Les totaux ne dansent pas quand une valeur change : chiffres a chasse fixe.
        assertEquals("tnum", t.titleSmall.fontFeatureSettings)
    }

    /** Les reglages suivent le theme : leur accent est le sien, et la carte se detache du fond. */
    @Test fun `la palette des reglages vient du theme`() {
        for ((s, dark) in listOf(TrailogLight to false, TrailogDark to true)) {
            val p = settingsPaletteOf(s, dark)
            assertEquals(s.primary, p.accent)
            assertEquals(s.onSurface, p.label)
            assertTrue("carte plus claire que le fond, sombre=$dark", p.card.luminance() > p.screen.luminance())
        }
    }
}
