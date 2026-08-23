package fr.lc4918.trailog.location

import androidx.test.core.app.ApplicationProvider
import fr.lc4918.trailog.domain.model.Sample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * La trace suivie, gardee sur le disque le temps d'une sortie.
 *
 * **Ce qu'on eprouve ici est une reprise apres la mort du processus.** Le service rend `START_STICKY` et
 * le systeme le relance apres avoir repris sa memoire ; [TrackWatch] etant un objet en memoire, la trace
 * suivie revenait a null et l'alerte d'eloignement se retrouvait desarmee sans un mot. Le fichier est ce
 * qui traverse cette coupure - il doit donc se relire tel qu'il a ete ecrit, ECHANTILLONS COMPRIS : c'est
 * sur eux que la position se rabat, et un parcours du planificateur n'a aucune couche ou aller les
 * rechercher.
 */
@RunWith(RobolectricTestRunner::class)
class FollowedStoreTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val trace = TrackWatch.Followed(
        layerId = 7, layerName = "GR 9", trackIndex = 1, trackCount = 3,
        samples = listOf(
            Sample(0.0, 210.0, 0.0, null, 5.70, 45.20),
            Sample(120.0, 224.0, 11.6, null, 5.71, 45.21),
        ),
    )

    @Before fun disqueVierge() { File(ctx.filesDir, "followed-track.json").delete() }

    @Test fun `rien sur le disque, rien a reprendre`() = runTest {
        assertNull(FollowedStore.load(ctx))
    }

    @Test fun `la trace ecrite se relit entiere`() = runTest {
        FollowedStore.save(ctx, trace)
        assertEquals(trace, FollowedStore.load(ctx))
    }

    /**
     * L'effacement compte autant que l'ecriture : sans lui, un suivi arrete a midi reprendrait tout seul
     * au prochain demarrage du service, des heures plus tard et sans que personne l'ait demande.
     */
    @Test fun `arreter le suivi efface la trace gardee`() = runTest {
        FollowedStore.save(ctx, trace)
        FollowedStore.save(ctx, null)
        assertNull(FollowedStore.load(ctx))
    }

    /** Un fichier illisible - version d'avant, ecriture interrompue - vaut "on ne suivait rien". Il ne doit
     *  surtout pas emporter le demarrage du service. */
    @Test fun `un fichier abime ne leve pas`() = runTest {
        File(ctx.filesDir, "followed-track.json").writeText("{ ceci n'est pas du JSON")
        assertNull(FollowedStore.load(ctx))
    }

    /** Le parcours du planificateur passe par le meme chemin : c'est tout l'interet d'ecrire les
     *  echantillons plutot que l'identite d'une couche, puisqu'il n'en a pas. */
    @Test fun `un parcours sans couche se garde comme les autres`() = runTest {
        val parcours = trace.copy(layerId = -1L, layerName = "Itineraire en cours", trackCount = 1)
        FollowedStore.save(ctx, parcours)
        assertEquals(parcours, FollowedStore.load(ctx))
    }
}
