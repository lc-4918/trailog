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

    // ---------- Un lieu deja pose ne se repropose pas ----------

    /**
     * Le depart pose, l'historique de l'arrivee ne doit plus l'offrir : deux etapes au meme endroit font
     * un troncon de longueur nulle.
     */
    @Test fun `un lieu deja pose ailleurs est reconnu`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        assertTrue("vu depuis l'arrivee", etat.usesPlace("Mirepoix", etat.steps.last()))
        assertFalse("un lieu jamais pose", etat.usesPlace("Soreze", etat.steps.last()))
    }

    /** Mais l'etape qui le PORTE continue de se le voir offrir : c'est elle qu'on est en train de
     *  remplacer, et se le reproposer revient a annuler son geste. */
    @Test fun `l'etape qui porte le lieu se le voit encore offrir`() {
        val etat = RoutePlannerState()
        val depart = etat.steps.first()
        etat.choose(depart, StepTarget.Place(lieu("Mirepoix")))
        assertFalse(etat.usesPlace("Mirepoix", depart))
    }

    // ---------- Un champ deja rempli redevient vierge au focus ----------

    /**
     * Reprendre le focus d'un champ DEJA REMPLI le vide a l'ecran : l'utilisateur le voit vide et attend
     * qu'on lui propose de quoi le remplir. Sans cela, apres un calcul, taper sur le depart ne proposait
     * plus rien du tout.
     */
    @Test fun `un champ rempli redevient vierge quand il reprend le focus`() {
        val etat = RoutePlannerState()
        val depart = etat.steps.first()
        etat.choose(depart, StepTarget.Place(lieu("Mirepoix")))
        assertFalse("une saisie retenue n'est plus vierge", depart.untouched)
        etat.focus(depart)
        assertTrue("le focus le rend vierge a nouveau", depart.untouched)
    }

    // ---------- Le profil s'efface le temps d'une saisie ----------

    /**
     * Le clavier prend la moitie basse de l'ecran : entre lui et le profil altimetrique, il ne restait
     * plus de place pour les propositions, qui naissaient hors de la zone visible.
     *
     * Le REGLAGE ne bouge pas - le profil revient de lui-meme quand le champ rend le focus, sans que
     * l'utilisateur ait a le redemander.
     */
    @Test fun `le profil se retire le temps d'une saisie`() {
        val etat = RoutePlannerState()
        etat.toggleProfile()
        assertTrue(etat.profileShown)
        etat.setEditing(etat.steps.first(), true)
        assertFalse("le profil laisse la place", etat.profileShown)
        assertTrue("mais le reglage tient", etat.profileVisible)
        etat.setEditing(etat.steps.first(), false)
        assertTrue("et il revient seul", etat.profileShown)
    }

    /**
     * Passer d'un champ a l'autre : le premier perd le focus APRES que le second l'ait pris. Un simple
     * drapeau se serait rabaisse juste apres avoir ete leve, et le profil aurait reparu sous le clavier.
     */
    @Test fun `passer d'un champ a l'autre garde le profil retire`() {
        val etat = RoutePlannerState()
        etat.toggleProfile()
        val depart = etat.steps.first()
        val arrivee = etat.steps.last()
        etat.setEditing(depart, true)
        etat.setEditing(arrivee, true)
        etat.setEditing(depart, false)      // l'ancien rend le focus en dernier
        assertFalse(etat.profileShown)
        etat.setEditing(arrivee, false)
        assertTrue(etat.profileShown)
    }

    /** Profil non demande : la saisie n'y change rien, il n'y a rien a retirer. */
    @Test fun `sans profil demande, la saisie ne change rien`() {
        val etat = RoutePlannerState()
        assertFalse(etat.profileShown)
        etat.setEditing(etat.steps.first(), true)
        assertFalse(etat.profileShown)
    }

    /**
     * Ce qui reste exclu : le champ vide APRES UNE FRAPPE. Efface caractere par caractere, il n'est pas
     * vierge pour autant - voir la liste ressurgir sous les doigts au dernier retour arriere serait une
     * surprise.
     */
    @Test fun `un champ efface a la main ne redevient pas vierge`() {
        val etat = RoutePlannerState()
        val depart = etat.steps.first()
        etat.type(depart, "Mir")
        etat.type(depart, "")
        assertFalse(depart.untouched)
    }
}
