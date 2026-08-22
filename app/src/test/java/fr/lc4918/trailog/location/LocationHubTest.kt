package fr.lc4918.trailog.location

import android.location.Location
import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La difference entre un suivi qu'on arrete et un suivi qui s'arrete.
 *
 * **Ce test existe a cause d'une sortie reelle.** Un testeur a fait vingt kilometres dans le mauvais sens :
 * son repere avait disparu, il l'avait vu, et rien ne lui a appris que l'application ne savait plus ou il
 * etait. Le suivi s'etait arrete tout seul - la localisation coupee, ou le service tue - et le code
 * traitait cet arret-la exactement comme un tap sur le bouton.
 *
 * Toute la correction tient dans cette distinction, et elle se decide ici : ce qui suit un arret DEMANDE
 * est le silence, ce qui suit un arret SUBI est une annonce et une reprise.
 */
@RunWith(RobolectricTestRunner::class)
class LocationHubTest {

    /** Le concentrateur est un objet de processus : les tests d'une meme classe le partagent. Ce geste
     *  est celui de l'utilisateur qui eteint, et il remet tout a plat - c'est ce qui en fait un depart. */
    @Before fun repartDeZero() { LocationHub.stopRequestedByUser() }

    private fun position(lat: Double = 45.18, lon: Double = 5.72) = Location("test").apply {
        latitude = lat; longitude = lon; time = 1_700_000_000_000L
    }

    // ---------- L'arret demande ne s'annonce pas ----------

    @Test fun `un arret demande n'annonce rien`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(true)
        LocationHub.stopRequestedByUser()
        LocationHub.setTracking(false, LocationHub.StopReason.USER)
        assertNull("rien a dire de ce qu'on vient de demander", LocationHub.stopNotice.value)
        assertFalse("et rien a rallumer", LocationHub.wanted.value)
    }

    // ---------- L'arret subi s'annonce ----------

    /** Le cas de la sortie : l'economie d'energie coupe la localisation, personne n'a rien demande. */
    @Test fun `la localisation coupee s'annonce`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(true)
        LocationHub.setTracking(false, LocationHub.StopReason.SENSOR_OFF)
        assertEquals(LocationHub.StopReason.SENSOR_OFF, LocationHub.stopNotice.value)
    }

    /** Le service tue par le systeme : la raison par defaut, faute d'en connaitre une meilleure. */
    @Test fun `un arret sans raison connue s'annonce quand meme`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(true)
        LocationHub.setTracking(false)
        assertEquals(LocationHub.StopReason.SYSTEM, LocationHub.stopNotice.value)
    }

    /**
     * L'intention survit a l'arret subi.
     *
     * C'est elle qui fait reprendre le suivi quand le capteur revient. Sans elle, une coupure d'une
     * seconde arretait le suivi pour le reste de la sortie.
     */
    @Test fun `un arret subi laisse l'intention en place`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(true)
        LocationHub.setTracking(false, LocationHub.StopReason.SENSOR_OFF)
        assertTrue(LocationHub.wanted.value)
    }

    /** Un arret alors que personne ne voulait de suivi n'apprend rien a personne. */
    @Test fun `un arret non voulu n'annonce rien`() {
        LocationHub.setTracking(false, LocationHub.StopReason.SYSTEM)
        assertNull(LocationHub.stopNotice.value)
    }

    // ---------- L'annonce se consomme ----------

    @Test fun `l'ecran peut retirer l'annonce`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(false, LocationHub.StopReason.SYSTEM)
        LocationHub.clearStopNotice()
        assertNull(LocationHub.stopNotice.value)
    }

    /** Rallumer efface l'annonce : elle decrirait un probleme resolu, et serait lue de travers la fois
     *  suivante. */
    @Test fun `rallumer efface l'annonce`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(false, LocationHub.StopReason.SENSOR_OFF)
        LocationHub.wantTracking()
        assertNull(LocationHub.stopNotice.value)
    }

    // ---------- La position ----------

    /** L'arret oublie la position : la garder ferait reapparaitre le repere a l'endroit d'hier. */
    @Test fun `l'arret oublie la derniere position`() {
        LocationHub.wantTracking()
        LocationHub.setTracking(true)
        LocationHub.publish(position())
        assertNotNull(LocationHub.fix.value)
        LocationHub.setTracking(false, LocationHub.StopReason.SYSTEM)
        assertNull(LocationHub.fix.value)
    }

    /**
     * L'heure de RECEPTION est posee, et sur l'horloge de l'appareil.
     *
     * C'est elle qui dira si le repere ment encore. Sur elapsedRealtime et non sur l'heure murale : une
     * remise a l'heure par le reseau ne doit pas rajeunir une position vieille de dix minutes.
     */
    @Test fun `chaque position porte son heure de reception`() {
        val avant = SystemClock.elapsedRealtime()
        LocationHub.publish(position())
        val recue = requireNotNull(LocationHub.fix.value).receivedAtMs
        assertTrue("posee a la reception", recue >= avant)
        assertTrue("et non l'heure de la mesure", recue < 1_700_000_000_000L)
    }
}
