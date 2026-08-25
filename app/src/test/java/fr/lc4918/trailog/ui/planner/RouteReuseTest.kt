package fr.lc4918.trailog.ui.planner

import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le parcours calcule : quand il doit etre REFAIT, quand il ne doit surtout pas l'etre, et ce qu'on en
 * garde pour le retrouver apres la mort du processus.
 *
 * JUnit nu, et non Robolectric : instancier un porteur d'etat Compose depuis une classe Robolectric
 * empoisonne la JVM que tous les tests partagent (cf. `PlannerStoreTest`). L'ecriture sur le disque, elle,
 * demande un `Context` et vit la-bas.
 *
 * L'effet de calcul se relance chaque fois que la composition repart de zero, et pas seulement quand ses
 * cles changent : une rotation renvoyait donc au moteur, par le reseau, un itineraire que le `ViewModel`
 * avait garde intact. Un service muet ou un reseau absent le publiait alors en echec - c'est-a-dire
 * l'effacait, pour n'avoir rien change du tout.
 *
 * La faute ne leve rien : elle rend un ecran vide la ou il y avait un trajet.
 */
class RouteReuseTest {

    private val parcours = ComputedTrack(
        samples = listOf(
            Sample(0.0, 210.0, 0.0, null, 5.70, 45.20),
            Sample(120.0, 224.0, 11.6, null, 5.71, 45.21),
        ),
        stats = TrackStats(120.0, 14.0, 0.0, 210.0, 224.0, 11.6, null, 2),
        hasZ = true,
        hasTime = false,
    )

    /** Un trajet tel qu'il a ete garde sur le disque : deux etapes, une discipline, un parcours. */
    private fun instantane(collapsed: Boolean = false) = PlannerSnapshot(
        steps = listOf(
            StepSnapshot(listOf("Mirepoix"), 1.87, 43.09),
            StepSnapshot(listOf("Soreze"), 2.07, 43.45),
        ),
        profile = RoutingProfile.GRAVEL.key,
        collapsed = collapsed,
        meters = 120.0,
        seconds = 600.0,
        track = parcours,
    )

    private fun etatCalcule(): Pair<RoutePlannerState, RouteInputs> {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.setStart(GeocodePlace("Mirepoix", 1.87, 43.09))
        etat.setEnd(GeocodePlace("Soreze", 2.07, 43.45))
        val entrees = entrees(etat)
        etat.publish(RouteState.Done(120.0, 600.0, parcours), entrees)
        return etat to entrees
    }

    private fun entrees(
        etat: RoutePlannerState,
        url: String = "https://valhalla.example/route",
        prefs: RoutingPrefs = RoutingPrefs.Balanced,
        smoothingM: Double = 30.0,
    ) = RouteInputs(etat.revision, url, etat.profile, prefs, smoothingM)

    /** Rien n'a bouge : le parcours a l'ecran repond deja a la question, on ne redemande rien au moteur. */
    @Test fun `un parcours calcule ne se recalcule pas pour les memes entrees`() {
        val (etat, entrees) = etatCalcule()
        assertTrue(etat.adopt(entrees))
    }

    /** Une etape ajoutee, retiree, deplacee ou changee incremente la revision : le parcours affiche n'est
     *  plus celui des etapes, il faut le refaire. */
    @Test fun `une etape changee demande un recalcul`() {
        val (etat, _) = etatCalcule()
        etat.setEnd(GeocodePlace("Revel", 2.00, 43.46))
        assertFalse(etat.adopt(entrees(etat)))
    }

    /** Les reglages de trace entrent dans l'empreinte : changer de service, de preferences ou de lissage
     *  pendant qu'un parcours est affiche le recalcule, comme avant. */
    @Test fun `un reglage de trace change demande un recalcul`() {
        val (etat, _) = etatCalcule()
        assertFalse("autre service", etat.adopt(entrees(etat, url = "https://autre.example/route")))
        assertFalse(
            "autres preferences",
            etat.adopt(entrees(etat, prefs = RoutingPrefs.defaultFor(RoutingProfile.ROAD_BIKE))),
        )
        assertFalse("autre lissage", etat.adopt(entrees(etat, smoothingM = 5.0)))
    }

    /** Sans parcours, il n'y a rien a garder : le calcul doit avoir lieu. */
    @Test fun `sans parcours, il faut calculer`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        assertFalse(etat.adopt(entrees(etat)))
    }

    /** Un echec ne laisse aucune empreinte : le calcul suivant doit repartir, et non se croire a jour. */
    @Test fun `un echec ne se prend pas pour un parcours a jour`() {
        val (etat, entrees) = etatCalcule()
        etat.publish(RouteState.Failed)
        assertNull(etat.computedFrom)
        assertFalse(etat.adopt(entrees))
    }

    /**
     * Un parcours REPRIS DU DISQUE arrive sans empreinte, et il est adopte tel quel pour les entrees du
     * moment : le recalculer serait demander le reseau a une application qui vient de renaitre, pour
     * retrouver au mieux ce qu'on a deja - et au pire le perdre.
     */
    @Test fun `un parcours repris du disque est adopte, jamais recalcule`() {
        val etat = RoutePlannerState()
        etat.restore(instantane())
        assertNull("il arrive sans empreinte", etat.computedFrom)
        val entrees = entrees(etat)
        assertTrue("adopte", etat.adopt(entrees))
        assertTrue("et il la porte desormais", etat.adopt(entrees))
    }

    // ---------- Ce qu'on garde, et ce qu'on repose ----------

    /** Un planificateur sans parcours CALCULE n'a rien a garder : des etapes a moitie saisies ne se
     *  ressuscitent pas, ce qu'on regrette de perdre est le trajet qu'on suivait. */
    @Test fun `sans parcours calcule, il n'y a rien a garder`() {
        val etat = RoutePlannerState()
        assertNull("planificateur ferme", etat.snapshot())
        etat.openPlanner()
        etat.setStart(GeocodePlace("Mirepoix", 1.87, 43.09))
        assertNull("une seule etape, aucun parcours", etat.snapshot())
    }

    /** L'instantane repose le trajet tel qu'il etait : ses etapes, sa discipline, son parcours, et jusqu'a
     *  la bande reduite dans laquelle on roulait. */
    @Test fun `le trajet repris revient tel qu'il etait`() {
        val etat = RoutePlannerState()
        etat.restore(instantane(collapsed = true))
        assertTrue("la bande est la", etat.open)
        assertTrue("reduite, comme on l'avait laissee", etat.collapsed)
        assertEquals(RoutingProfile.GRAVEL, etat.profile)
        assertEquals(
            StepTarget.Place(GeocodePlace(listOf("Mirepoix"), 1.87, 43.09)),
            etat.steps.first().target,
        )
        assertEquals(parcours, etat.done?.track)
        assertEquals(120.0, etat.done?.meters ?: 0.0, 1e-9)
    }

    /** Et il se re-garde a l'identique : ce qui est repris doit pouvoir traverser une seconde coupure. */
    @Test fun `un trajet repris se regarde a l'identique`() {
        val etat = RoutePlannerState()
        val snap = instantane(collapsed = true)
        etat.restore(snap)
        assertEquals(snap, etat.snapshot())
    }

    /**
     * La reprise n'ecrase jamais un trajet en cours : elle n'a lieu qu'au demarrage, et ecraser ce qu'on
     * vient de composer serait pire que la perte qu'on repare.
     */
    @Test fun `la reprise ne touche pas a un trajet deja compose`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.setStart(GeocodePlace("Revel", 2.00, 43.46))
        etat.restore(instantane())
        assertEquals(
            StepTarget.Place(GeocodePlace("Revel", 2.00, 43.46)),
            etat.steps.first().target,
        )
        assertNull("aucun parcours repose", etat.done)
    }

    /** Un instantane tronque - moins de deux etapes, ou un parcours sans geometrie - ne se repose pas :
     *  il n'y aurait rien a montrer, et la bande s'ouvrirait sur du vide. */
    @Test fun `un instantane tronque ne se repose pas`() {
        val etat = RoutePlannerState()
        etat.restore(instantane().copy(steps = instantane().steps.take(1)))
        assertFalse("rien n'est ouvert", etat.open)
        etat.restore(instantane().copy(track = parcours.copy(samples = parcours.samples.take(1))))
        assertFalse("rien n'est ouvert non plus", etat.open)
    }

    /** ... mais la premiere retouche le rend caduc comme n'importe quel autre. */
    @Test fun `un parcours repris se recalcule des qu'on y touche`() {
        val etat = RoutePlannerState()
        etat.restore(instantane())
        etat.adopt(entrees(etat))
        etat.addStep()
        etat.choose(etat.steps.last(), StepTarget.Place(GeocodePlace("Revel", 2.00, 43.46)))
        assertFalse(etat.adopt(entrees(etat)))
    }
}
