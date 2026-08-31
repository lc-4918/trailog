package fr.lc4918.trailog.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le delai accorde au moteur d'itineraire selon la longueur demandee.
 *
 * **Ce que ces cas verrouillent est un defaut sans symptome lisible** : un delai trop court ne leve rien,
 * il rend "Aucun itineraire" pour un trajet parfaitement calculable - et l'utilisateur cherche alors la
 * faute du cote de la discipline ou des etapes. Releve sur un 500 km : "j'ai souvent aucun itineraire
 * trouve la premiere fois".
 */
class RouteTimeoutTest {

    /** Toulouse, Carcassonne, Narbonne : trois etapes, environ 150 km de vol d'oiseau au total. */
    private val toulouse = 43.6045 to 1.4442
    private val carcassonne = 43.2130 to 2.3491
    private val narbonne = 43.1839 to 3.0036
    /** Brest et Nice : environ 1 100 km de vol d'oiseau, de quoi depasser le plafond. */
    private val brest = 48.3904 to -4.4861
    private val nice = 43.7102 to 7.2620

    /** Un trajet de quelques kilometres reste au delai de base, a quelques centiemes pres : le supplement
     *  est au prorata de la longueur, et six kilometres n'en demandent presque rien. */
    @Test fun `un trajet de proximite reste au delai de base`() {
        val court = listOf(43.0765 to 1.9476, 43.0881 to 1.8743)
        val ms = RouteTimeout.msFor(court)
        assertTrue("delai obtenu : $ms", ms in RouteTimeout.BASE_MS..(RouteTimeout.BASE_MS + 1_000))
    }

    /**
     * LE cas du signalement : un long trajet obtient sensiblement plus que le delai de base.
     *
     * Toulouse - Narbonne fait environ 150 km a vol d'oiseau, soit une tranche et demie.
     */
    @Test fun `un long trajet obtient davantage que le delai de base`() {
        val ms = RouteTimeout.msFor(listOf(toulouse, carcassonne, narbonne))
        assertTrue("delai obtenu : $ms", ms > RouteTimeout.BASE_MS)
        assertTrue("delai obtenu : $ms", ms >= RouteTimeout.BASE_MS + RouteTimeout.PER_100KM_MS)
    }

    /** Les etapes INTERMEDIAIRES comptent : c'est la somme des segments, non la distance des deux bouts.
     *  Un aller-retour a le meme depart et la meme arrivee, et deux fois le chemin a calculer. */
    @Test fun `les etapes intermediaires comptent dans la longueur`() {
        val direct = RouteTimeout.msFor(listOf(toulouse, narbonne))
        val allerRetour = RouteTimeout.msFor(listOf(toulouse, narbonne, toulouse))
        assertTrue("$allerRetour doit depasser $direct", allerRetour > direct)
    }

    /** Un delai est aussi une promesse faite a qui regarde le rond tourner : au-dela du plafond, l'attente
     *  ne se distingue plus d'un blocage. */
    @Test fun `le delai est plafonne`() {
        assertEquals(RouteTimeout.MAX_MS, RouteTimeout.msFor(listOf(brest, nice)))
    }

    /** Moins de deux etapes : rien a calculer, mais une valeur utilisable plutot que zero - l'appelant
     *  s'arrete avant la requete, et n'a pas de cas particulier a ecrire. */
    @Test fun `moins de deux etapes rend le delai de base`() {
        assertEquals(RouteTimeout.BASE_MS, RouteTimeout.msFor(emptyList()))
        assertEquals(RouteTimeout.BASE_MS, RouteTimeout.msFor(listOf(toulouse)))
    }

    /** Le vol d'oiseau MINORE le trajet reel - un itineraire cyclable est toujours plus long que la ligne
     *  droite - donc l'estimation est prudente par construction. */
    @Test fun `le vol d'oiseau se mesure en kilometres`() {
        val km = RouteTimeout.crowKm(listOf(toulouse, carcassonne))
        assertTrue("Toulouse-Carcassonne : $km km", km in 70.0..90.0)
    }
}
