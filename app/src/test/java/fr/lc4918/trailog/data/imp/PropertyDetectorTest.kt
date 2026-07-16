package fr.lc4918.trailog.data.imp

import fr.lc4918.trailog.domain.model.PropValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detection du type d'une propriete importee : c'est elle qui decide si un champ devient texte,
 *  lien cliquable ou image dans l'infobulle. */
class PropertyDetectorTest {
    @Test fun `texte libre reste du texte`() {
        assertEquals(PropValue.Text("Tres belle vue"), detectPropValue("Tres belle vue"))
        assertEquals(PropValue.Text("1250"), detectPropValue("1250"))
    }

    @Test fun `une URL http(s) devient un lien`() {
        val v = detectPropValue("https://example.com/page")
        assertTrue(v is PropValue.Link)
        assertEquals("https://example.com/page", (v as PropValue.Link).url)
        assertEquals(v.url, v.text)                     // a defaut de libelle, l'URL sert de texte
        assertTrue(detectPropValue("http://example.com") is PropValue.Link)
    }

    @Test fun `une URL d'image l'emporte sur le lien`() {
        assertEquals(PropValue.Image("https://x/p.jpg"), detectPropValue("https://x/p.jpg"))
        assertEquals(PropValue.Image("https://x/p.PNG"), detectPropValue("https://x/p.PNG"))
    }

    @Test fun `un chemin local d'image est detecte`() {
        assertEquals(PropValue.Image("/sdcard/DCIM/IMG_001.jpg"), detectPropValue("/sdcard/DCIM/IMG_001.jpg"))
        assertEquals(PropValue.Image("photos/pic.png"), detectPropValue("photos/pic.png"))
        assertEquals(PropValue.Image("file:///tmp/a.webp"), detectPropValue("file:///tmp/a.webp"))
    }

    @Test fun `tous les formats d'image annonces sont reconnus`() {
        listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg").forEach { ext ->
            assertTrue("extension $ext", detectPropValue("/a/b.$ext") is PropValue.Image)
        }
    }

    /** Une extension d'image sans chemin ni schema n'en est pas une : "photo.jpg" seul peut etre du texte. */
    @Test fun `une extension seule sans chemin ne suffit pas`() {
        assertTrue(detectPropValue("photo.jpg") is PropValue.Text)
    }

    /** Une URL d'image a parametres reste une image : l'extension se lit avant le point d'interrogation. */
    @Test fun `parametres de requete ignores pour l'extension`() {
        assertTrue(detectPropValue("https://x/p.jpg?w=800") is PropValue.Image)
    }

    @Test fun `un format non image reste un lien`() {
        assertTrue(detectPropValue("https://x/doc.pdf") is PropValue.Link)
    }

    @Test fun `valeur vide ou blanche reste du texte`() {
        assertEquals(PropValue.Text(""), detectPropValue(""))
        assertEquals(PropValue.Text(""), detectPropValue("   "))
    }

    @Test fun `les espaces autour sont retires`() {
        assertEquals(PropValue.Text("abc"), detectPropValue("  abc  "))
        assertTrue(detectPropValue("  https://x/p.jpg  ") is PropValue.Image)
    }

    @Test fun `looksLikeImagePath suit les memes regles que detectPropValue`() {
        listOf("/a/b.jpg", "https://x/p.png", "file:///t/a.gif", "c:\\photos\\a.webp").forEach {
            assertTrue(it, looksLikeImagePath(it))
        }
        listOf("", "   ", "abc", "photo.jpg", "https://x/doc.pdf").forEach {
            assertFalse(it, looksLikeImagePath(it))
        }
    }
}
