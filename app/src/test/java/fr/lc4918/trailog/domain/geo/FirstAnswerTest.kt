package fr.lc4918.trailog.domain.geo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La course des fournisseurs de position : le GPS et le reseau interroges ensemble, la premiere reponse
 * gagne.
 *
 * Le cas du terrain : arrive au travail, sous un toit, le GPS ne rend rien et met une demi-minute a
 * l'avouer, pendant que le reseau sait ou l'on est des la premiere seconde.
 *
 * Temps VIRTUEL (`runTest`) : les delais ci-dessous ne coutent rien a l'horloge reelle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstAnswerTest {

    private val delai = 20_000L

    @Test fun `la premiere reponse gagne, sans attendre les autres`() = runTest {
        val debut = currentTime
        val r = FirstAnswer.from(
            listOf(
                suspend { delay(30_000); "gps" },
                suspend { delay(500); "reseau" },
            ),
            timeoutMs = delai,
        )
        assertEquals("reseau", r)
        assertEquals("on n'a pas attendu le GPS", 500, currentTime - debut)
    }

    /** Un fournisseur muet ne fait pas echouer la course : il rend null, les autres continuent. */
    @Test fun `une source muette laisse la place aux autres`() = runTest {
        val r = FirstAnswer.from(
            listOf(
                suspend { delay(100); null },
                suspend { delay(3_000); "reseau" },
            ),
            timeoutMs = delai,
        )
        assertEquals("reseau", r)
    }

    /** Toutes muettes : null, et sans attendre le delai entier. */
    @Test fun `toutes muettes rendent null`() = runTest {
        val debut = currentTime
        val r = FirstAnswer.from(listOf(suspend { delay(100); null }, suspend { delay(200); null }), delai)
        assertNull(r)
        assertTrue("on n'a pas attendu le delai entier", currentTime - debut < delai)
    }

    /** Personne ne repond a temps : le delai tranche, et le rond d'attente s'arrete. */
    @Test fun `le delai borne l'attente`() = runTest {
        val debut = currentTime
        val r = FirstAnswer.from(listOf(suspend { delay(60_000); "gps" }), timeoutMs = delai)
        assertNull(r)
        assertEquals(delai, currentTime - debut)
    }

    /** Aucun fournisseur allume : rien a courir, et surtout pas un delai a attendre. */
    @Test fun `sans source, la reponse est immediate`() = runTest {
        val debut = currentTime
        assertNull(FirstAnswer.from(emptyList<suspend () -> String?>(), delai))
        assertEquals(0, currentTime - debut)
    }
}
