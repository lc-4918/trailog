package fr.lc4918.trailog.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Le journal du suivi : sa mise en forme, et ce qu'il fait quand il est plein.
 *
 * Il sert a relire une sortie qu'on n'a pas pu regarder - telephone en poche, ecran eteint. Deux
 * choses le rendraient inutile : des lignes qu'on ne sait pas dater, et une rotation qui emporte
 * justement la sortie qu'on vient de faire.
 */
class FixLogTest {

    @get:Rule val dossier = TemporaryFolder()

    /** L'heure murale se compare a une montre, l'horloge monotone aux durees que le code manipule. */
    @Test fun `chaque ligne porte l'heure et l'horloge du telephone`() {
        val l = FixLog.line(wallMs = 0L, elapsedMs = 125_000L, texte = "position gps prec=8m")
        assertTrue("l'horloge monotone, en secondes", l.contains("[125s]"))
        assertTrue(l.contains("position gps prec=8m"))
        assertTrue("une ligne par evenement", l.endsWith("\n"))
    }

    @Test fun `un journal qui tient dans sa taille ne tourne pas`() {
        val f = dossier.newFile("journal.log")
        f.writeText("court")
        assertFalse(FixLog.rotateIfNeeded(f, maxBytes = 1_000L))
        assertEquals("court", f.readText())
    }

    /** Plein, il passe en `.1` : la sortie precedente reste lisible, la nouvelle repart de zero. */
    @Test fun `un journal plein passe en une generation, et rien n'est perdu`() {
        val f = dossier.newFile("journal.log")
        f.writeText("x".repeat(1_200))
        assertTrue(FixLog.rotateIfNeeded(f, maxBytes = 1_000L))
        assertFalse("le neuf n'existe pas encore : la premiere ecriture le cree", f.exists())
        assertEquals(1_200, File(f.parentFile, "journal.log.1").length().toInt())
    }

    /** Deux generations, et pas trois : un journal de diagnostic qui remplit le telephone nuit. */
    @Test fun `la rotation ne garde qu'une generation`() {
        val f = dossier.newFile("journal.log")
        f.writeText("x".repeat(1_200))
        FixLog.rotateIfNeeded(f, maxBytes = 1_000L)
        f.writeText("y".repeat(1_200))
        FixLog.rotateIfNeeded(f, maxBytes = 1_000L)
        assertEquals("yyy", File(f.parentFile, "journal.log.1").readText().take(3))
        assertFalse(File(f.parentFile, "journal.log.1.1").exists())
    }
}
