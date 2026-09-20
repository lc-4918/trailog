package fr.lc4918.trailog.location

import fr.lc4918.trailog.domain.geo.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les compteurs de la sortie, entre le service qui les nourrit, le bouton qui les remet a zero, et le
 * disque d'ou ils reviennent. Une faute ici fait revenir la sortie d'hier apres qu'on l'a effacee.
 */
class TripWatchTest {

    private fun fix(nordM: Double, t: Long, recueA: Long = t) = LocationHub.Fix(
        lat = 45.0 + nordM / 111_195.0, lon = 6.0, accuracyM = 5f, bearingDeg = null, speedMps = 2f,
        altitudeM = null, timeMs = t, receivedAtMs = recueA, elapsedAtMs = t,
    )

    @Test fun `les positions font avancer les compteurs, et la remise a zero repart de rien`() {
        TripWatch.reset()
        TripWatch.add(fix(0.0, 1_000L))
        assertTrue("la distance change", TripWatch.add(fix(20.0, 11_000L)))
        assertEquals(20.0, TripWatch.trip.value.distanceM, 0.5)
        TripWatch.reset()
        assertEquals(Trip(), TripWatch.trip.value)
    }

    /**
     * Ecran rallume apres dix minutes de GPS eteint : le systeme livre d'un coup les positions qu'il
     * tenait, mesurees a plusieurs minutes d'intervalle. Ce sont les instants de MESURE qui comptent -
     * sur ceux de la livraison, elles paraitraient consecutives, et les kilometres entre elles se
     * compteraient en ligne droite, a travers ce qu'on n'a pas parcouru.
     */
    @Test fun `une salve rattrapee au reveil ne relie pas les points`() {
        TripWatch.reset()
        TripWatch.add(fix(0.0, 1_000L, recueA = 600_000L))
        TripWatch.add(fix(3_000.0, 400_000L, recueA = 600_010L))
        assertEquals(0.0, TripWatch.trip.value.distanceM, 0.01)
    }

    /** Remis a zero puis repris du disque : le disque est plus vieux que la remise a zero, il ne gagne pas. */
    @Test fun `la reprise ne ramene pas une sortie effacee`() {
        TripWatch.reset()
        TripWatch.restore(Trip(distanceM = 5_000.0))
        assertEquals(0.0, TripWatch.trip.value.distanceM, 0.0)
    }
}
