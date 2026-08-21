package fr.lc4918.trailog.data.imp

import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Les fichiers qu'une autre application nous confie.
 *
 * Deux choses se verrouillent ici, et elles se paient toutes les deux a l'usage : lire l'URI la ou chaque
 * sorte d'intention la range - l'ouverture et le partage ne s'ecrivent pas au meme endroit -, et ne
 * traiter une intention qu'une fois. Sans cette marque, la moindre recreation de l'activite - une rotation
 * d'ecran suffit - reimporterait le meme fichier, et le dossier se remplirait de copies.
 */
@RunWith(RobolectricTestRunner::class)
class ImportInboxTest {

    private val trace: Uri = Uri.parse("content://test/trace.gpx")
    private val autre: Uri = Uri.parse("content://test/boucle.gpx")

    @After fun vider() = ImportInbox.clear()

    /** Ouvrir un fichier : l'URI est dans les donnees de l'intention. */
    @Test fun `l'ouverture d'un fichier porte son URI dans les donnees`() {
        val intent = Intent(Intent.ACTION_VIEW).setData(trace)
        assertEquals(listOf(trace), ImportInbox.urisOf(intent))
    }

    /** Partager vers Trailog : l'URI est dans un extra, et non dans les donnees. */
    @Test fun `le partage porte son URI dans un extra`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, trace)
        assertEquals(listOf(trace), ImportInbox.urisOf(intent))
    }

    @Test fun `un partage multiple les rend tous`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(trace, autre))
        assertEquals(listOf(trace, autre), ImportInbox.urisOf(intent))
    }

    /** Le lancement ordinaire de l'application n'apporte aucun fichier. */
    @Test fun `une intention ordinaire n'apporte rien`() {
        assertTrue(ImportInbox.urisOf(Intent(Intent.ACTION_MAIN)).isEmpty())
        assertTrue(ImportInbox.urisOf(null).isEmpty())
    }

    /**
     * Une intention deja traitee ne rend plus rien : l'activite la relit a chaque recreation, et sans
     * cette marque une rotation d'ecran importerait le fichier une seconde fois.
     */
    @Test fun `une intention ne se lit qu'une fois`() {
        val intent = Intent(Intent.ACTION_VIEW).setData(trace)
        assertEquals(1, ImportInbox.urisOf(intent).size)
        assertTrue(ImportInbox.urisOf(intent).isEmpty())
    }

    /** Deux partages coup sur coup s'importent tous les deux : la file s'ajoute, elle ne remplace pas. */
    @Test fun `la file s'ajoute`() {
        ImportInbox.offer(listOf(trace))
        ImportInbox.offer(listOf(autre))
        assertEquals(listOf(trace, autre), ImportInbox.pending.value)
    }

    /** L'import lance, plus rien n'attend - sans quoi les fichiers repartiraient au prochain import. */
    @Test fun `consommer vide la file`() {
        ImportInbox.offer(listOf(trace))
        assertEquals(listOf(trace), ImportInbox.consume())
        assertTrue(ImportInbox.pending.value.isEmpty())
    }

    @Test fun `une offre vide ne reveille personne`() {
        ImportInbox.offer(emptyList())
        assertTrue(ImportInbox.pending.value.isEmpty())
    }
}
