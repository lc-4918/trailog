package fr.lc4918.trailog.ui.planner

import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * L'itineraire temporaire, garde sur le disque le temps d'une sortie.
 *
 * **Ce qu'on eprouve ici est une reprise apres la mort du processus.** Le planificateur vit dans un
 * `ViewModel`, ce qui lui fait traverser une rotation - mais pas la reprise de memoire que le systeme
 * s'accorde des que l'application passe assez longtemps derriere. Un testeur l'a rapporte ainsi :
 * "l'itineraire temporaire se perd, sans meme cliquer" ; veille souvent, bascule d'application rarement,
 * rotation jamais - exactement le classement des durees passees en arriere-plan.
 *
 * La faute ne levait rien, et laissait pire qu'un vide : la veille, elle, revenait du disque (cf.
 * `FollowedStore`), si bien que la cloche continuait de surveiller un parcours que plus rien ne montrait.
 *
 * **Le DISQUE seulement.** Ce qui touche a [RoutePlannerState] - l'instantane qu'on en tire, le trajet
 * qu'on y repose - est eprouve dans `RouteReuseTest`, en JUnit nu. Ce n'est pas un rangement : instancier
 * un porteur d'etat Compose depuis une classe Robolectric empoisonne la JVM que TOUS les tests partagent,
 * et les tests d'interface qui passent apres n'atteignent plus jamais l'inactivite - douze d'entre eux
 * mouraient sur leur delai de soixante secondes, pour onze minutes de suite. Le depot separe deja les deux
 * genres partout ailleurs ; c'est la regle, pas une preference.
 */
@RunWith(RobolectricTestRunner::class)
class PlannerStoreTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val trajet = PlannerSnapshot(
        steps = listOf(
            StepSnapshot(currentPosition = true),
            StepSnapshot(listOf("Camping du Lac", "Monteynard"), 5.71, 45.21),
        ),
        profile = RoutingProfile.GRAVEL.key,
        collapsed = true,
        meters = 120.0,
        seconds = 600.0,
        track = ComputedTrack(
            samples = listOf(
                Sample(0.0, 210.0, 0.0, null, 5.70, 45.20),
                Sample(120.0, 224.0, 11.6, null, 5.71, 45.21),
            ),
            stats = TrackStats(120.0, 14.0, 0.0, 210.0, 224.0, 11.6, null, 2),
            hasZ = true,
            hasTime = false,
        ),
    )

    @Before fun disqueVierge() { File(ctx.filesDir, "planner-route.json").delete() }

    @Test fun `rien sur le disque, rien a reprendre`() = runTest {
        assertNull(PlannerStore.load(ctx))
    }

    /** Le parcours EST garde, echantillons compris : le recalculer demanderait le reseau au pire moment,
     *  et une etape "d'ou je suis" ne partirait plus du meme endroit. */
    @Test fun `le trajet ecrit se relit entier`() = runTest {
        PlannerStore.save(ctx, trajet)
        assertEquals(trajet, PlannerStore.load(ctx))
    }

    /**
     * L'effacement compte autant que l'ecriture : sans lui, un trajet abandonne a midi reviendrait tout
     * seul au prochain lancement, des heures plus tard et sans que personne l'ait demande.
     */
    @Test fun `fermer le planificateur efface le trajet garde`() = runTest {
        PlannerStore.save(ctx, trajet)
        PlannerStore.save(ctx, null)
        assertNull(PlannerStore.load(ctx))
    }

    /** Un fichier illisible - version d'avant, ecriture interrompue - vaut "il n'y avait pas de trajet".
     *  Il ne doit surtout pas emporter le demarrage de l'ecran. */
    @Test fun `un fichier abime ne leve pas`() = runTest {
        File(ctx.filesDir, "planner-route.json").writeText("{ ceci n'est pas du json")
        assertNull(PlannerStore.load(ctx))
    }
}
