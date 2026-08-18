package fr.lc4918.trailog.ui.planner

import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que le planificateur accepte comme etapes.
 *
 * La faute que ces cas attrapent ne leve rien : elle laisse composer un trajet que le moteur refusera,
 * ou pire, un trajet de longueur nulle qui s'affiche comme un vrai.
 */
class PlannerStepsTest {

    private fun lieu(nom: String) = GeocodePlace(nom, 1.0, 43.0)

    /**
     * La position du porteur ne peut etre qu'UNE etape : partir d'ou l'on est pour y revenir ne fait
     * aucun trajet. Tant qu'elle sert, la bande cesse de la proposer.
     */
    @Test fun `la position actuelle ne sert qu'une fois`() {
        val etat = RoutePlannerState()
        assertFalse("rien de pose", etat.usesCurrentPosition)
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        assertTrue("le depart la porte", etat.usesCurrentPosition)
    }

    /** ... et elle redevient disponible des qu'on la retire du trajet. */
    @Test fun `effacer l'etape rend la position disponible`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        etat.clearStep(etat.steps.first())
        assertFalse(etat.usesCurrentPosition)
    }

    /** Un lieu ordinaire ne la consomme pas : deux villes ne sont pas la position du porteur. */
    @Test fun `un lieu ordinaire ne consomme pas la position`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.choose(etat.steps.last(), StepTarget.Place(lieu("Soreze")))
        assertFalse(etat.usesCurrentPosition)
    }
}
