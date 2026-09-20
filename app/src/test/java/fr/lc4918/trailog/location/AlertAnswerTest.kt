package fr.lc4918.trailog.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que l'arrivee de la carte a l'ecran fait de l'alerte d'eloignement.
 *
 * La regle a deja echoue une fois sur le terrain, et en silence : les deux arrivees portaient la meme
 * action, l'ecran s'allumait tout seul sur une carte sans banniere et sans sonnerie. Ce qui se verifie ici
 * est donc exactement cette difference - qui reveille, et qui repond.
 */
class AlertAnswerTest {

    @Test fun `le tap sur la notification reveille l'ecran et tait l'alerte`() {
        assertTrue(AlertAnswer.wakesScreen(LocationService.ACTION_SHOW_ALERT))
        assertTrue(AlertAnswer.silences(LocationService.ACTION_SHOW_ALERT))
    }

    @Test fun `le reveil automatique allume l'ecran mais laisse l'alerte sonner`() {
        assertTrue(AlertAnswer.wakesScreen(LocationService.ACTION_WAKE_ALERT))
        assertFalse(AlertAnswer.silences(LocationService.ACTION_WAKE_ALERT))
    }

    /** Une ouverture ordinaire - l'icone, un fichier importe - ne touche a rien. */
    @Test fun `une ouverture ordinaire ne reveille rien et ne tait rien`() {
        assertFalse(AlertAnswer.wakesScreen(null))
        assertFalse(AlertAnswer.silences(null))
        assertFalse(AlertAnswer.wakesScreen(android.content.Intent.ACTION_VIEW))
        assertFalse(AlertAnswer.silences(android.content.Intent.ACTION_VIEW))
    }

    /** Les deux actions sont distinctes : confondues, le reveil se tairait de nouveau lui-meme. */
    @Test fun `les deux actions ne se confondent pas`() {
        assertFalse(LocationService.ACTION_SHOW_ALERT == LocationService.ACTION_WAKE_ALERT)
    }
}
