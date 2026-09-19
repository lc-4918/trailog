package fr.lc4918.trailog.poi

import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ordonnancement du chargeur : ce qui part, dans quel ordre, ce qui se garde et ce qui s'abandonne.
 *
 * C'est la que se jouait le defaut le plus visible de l'ancienne couche - un geste de carte annulait les
 * requetes en vol, et les restaurants n'arrivaient jamais pour qui bougeait la carte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoiLoaderTest {

    private val a = PoiCell(10, 20)
    private val b = PoiCell(11, 20)
    private val c = PoiCell(12, 20)

    private fun food(cell: PoiCell) = PoiUnit(cell, PoiGroup.FOOD, PoiSource.OSM)
    private fun practical(cell: PoiCell) = PoiUnit(cell, PoiGroup.PRACTICAL, PoiSource.OSM)
    private fun lieu(cell: PoiCell, id: String, cat: PoiCategory = PoiCategory.RESTAURANTS) =
        Poi("osm:node/$id", id, cell.centerLat, cell.centerLon, cat)

    /** Un service bidon : il note ce qu'on lui demande, et repond quand on le lui dit. */
    private class Service {
        val demandes = mutableListOf<PoiJob>()
        val enCours = mutableMapOf<PoiCell, CompletableDeferred<PoiFetch>>()
        var bloquer = false
        var echouer = false
        suspend fun fetch(job: PoiJob): PoiFetch {
            demandes += job
            if (bloquer) return CompletableDeferred<PoiFetch>().also { enCours[job.cell] = it }.await()
            if (echouer) return PoiFetch(emptyMap(), failed = true)
            return PoiFetch(job.groups.associateWith { g ->
                listOf(Poi("osm:node/${job.cell.key}-${g.key}", "x", job.cell.centerLat, job.cell.centerLon,
                    PoiCategory.of(g).first()))
            }, failed = false)
        }
    }

    private var horloge = 0L

    private fun TestScope.chargeur(
        service: Service,
        cache: (List<PoiUnit>) -> PoiLookup = { PoiLookup(emptyMap(), it.associateWith { emptyList() }) },
        parallele: Int = 2,
    ) = PoiLoader(
        scope = this,
        lookup = { cache(it) },
        fetch = { service.fetch(it) },
        now = { horloge },
        parallelPerSource = parallele,
        retryAfterFailMs = 60_000L,
    )

    @Test fun `une unite fraiche du cache ne coute aucune requete`() = runTest {
        val s = Service()
        val l = chargeur(s, cache = { us -> PoiLookup(us.associateWith { listOf(lieu(it.cell, "c")) }, emptyMap()) })
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertTrue(s.demandes.isEmpty())
        assertEquals(1, l.state.value.pois.size)
        assertFalse(l.state.value.pending)
    }

    @Test fun `ce qui manque part au service, et se montre a son retour`() = runTest {
        val s = Service()
        val l = chargeur(s)
        l.want(listOf(food(a), food(b)))
        advanceUntilIdle()
        assertEquals(2, s.demandes.size)
        assertEquals(2, l.state.value.pois.size)
        assertFalse(l.state.value.pending)
    }

    /** Deux groupes d'une meme source sur une meme cellule partent dans une seule requete. */
    @Test fun `les groupes d'une cellule partent ensemble`() = runTest {
        val s = Service()
        val l = chargeur(s)
        l.want(listOf(food(a), practical(a)))
        advanceUntilIdle()
        assertEquals(1, s.demandes.size)
        assertEquals(setOf(PoiGroup.FOOD, PoiGroup.PRACTICAL), s.demandes.single().groups)
        assertEquals(2, l.state.value.pois.size)
    }

    /** Du centre vers les bords : l'ordre de la demande est celui des requetes. */
    @Test fun `les requetes partent dans l'ordre de la demande`() = runTest {
        val s = Service()
        val l = chargeur(s, parallele = 1)
        l.want(listOf(food(c), food(a), food(b)))
        advanceUntilIdle()
        assertEquals(listOf(c, a, b), s.demandes.map { it.cell })
    }

    /**
     * **Un geste ne jette pas ce qui est en vol.** La reponse arrive apres le geste, se garde, et revenir
     * sur la cellule ne coute aucune requete de plus.
     */
    @Test fun `une requete en vol survit au geste suivant`() = runTest {
        val s = Service().apply { bloquer = true }
        val l = chargeur(s, parallele = 1)
        l.want(listOf(food(a)))
        advanceUntilIdle()
        l.want(listOf(food(b)))
        advanceUntilIdle()
        s.bloquer = false
        s.enCours.getValue(a).complete(PoiFetch(mapOf(PoiGroup.FOOD to listOf(lieu(a, "1"))), failed = false))
        advanceUntilIdle()
        val avant = s.demandes.size
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertEquals("rien de redemande", avant, s.demandes.size)
        assertEquals(listOf("osm:node/1"), l.state.value.pois.map { it.uuid })
    }

    /** Ce qui attendait son tour et n'est plus voulu ne part pas. */
    @Test fun `une requete en attente est abandonnee si la vue change`() = runTest {
        val s = Service().apply { bloquer = true }
        val l = chargeur(s, parallele = 1)
        l.want(listOf(food(a), food(b)))
        advanceUntilIdle()
        assertEquals(listOf(a), s.demandes.map { it.cell })
        l.want(listOf(food(c)))
        advanceUntilIdle()
        s.bloquer = false
        s.enCours.getValue(a).complete(PoiFetch(mapOf(PoiGroup.FOOD to emptyList()), failed = false))
        advanceUntilIdle()
        assertEquals("b n'est jamais parti", listOf(a, c), s.demandes.map { it.cell })
    }

    /** Seules les unites voulues se montrent : une vue quittee n'a rien a faire sur la carte. */
    @Test fun `la carte ne montre que la vue demandee`() = runTest {
        val s = Service()
        val l = chargeur(s)
        l.want(listOf(food(a)))
        advanceUntilIdle()
        l.want(listOf(food(b)))
        advanceUntilIdle()
        assertEquals(1, l.state.value.pois.size)
        assertTrue(l.state.value.pois.single().uuid.contains(b.key))
    }

    /** Un echec sans rien en cache se dit : la carte ne sait pas si la zone est vide. */
    @Test fun `un echec sans cache se signale`() = runTest {
        val s = Service().apply { echouer = true }
        val l = chargeur(s)
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertTrue(l.state.value.missing)
        assertFalse(l.state.value.fromCache)
        assertFalse(l.state.value.pending)
    }

    /** Un echec avec un cache perime montre le cache, et le dit. */
    @Test fun `un echec montre ce que le cache gardait`() = runTest {
        val s = Service().apply { echouer = true }
        val l = chargeur(s, cache = { us -> PoiLookup(emptyMap(), us.associateWith { listOf(lieu(it.cell, "vieux")) }) })
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertTrue(l.state.value.fromCache)
        assertFalse(l.state.value.missing)
        assertEquals(1, l.state.value.pois.size)
    }

    /**
     * Une unite en echec attend une minute avant d'etre redemandee : insister est ce qui fait refuser le
     * service plus durement. Le retour du reseau leve ce repos.
     */
    @Test fun `une unite en echec attend son tour, sauf au retour du reseau`() = runTest {
        val s = Service().apply { echouer = true }
        val l = chargeur(s)
        l.want(listOf(food(a)))
        advanceUntilIdle()
        horloge = 1_000L
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertEquals("pas de nouvel essai tout de suite", 1, s.demandes.size)
        s.echouer = false
        l.retryFailed()
        advanceUntilIdle()
        assertEquals(2, s.demandes.size)
        assertFalse(l.state.value.missing)
        assertEquals(1, l.state.value.pois.size)
    }

    @Test fun `apres le delai, l'unite en echec se redemande`() = runTest {
        val s = Service().apply { echouer = true }
        val l = chargeur(s)
        l.want(listOf(food(a)))
        advanceUntilIdle()
        horloge = 61_000L
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertEquals(2, s.demandes.size)
    }

    /** Pendant que le service travaille, la carte le sait. */
    @Test fun `l'attente se lit tant qu'une unite voulue est en vol`() = runTest {
        val s = Service().apply { bloquer = true }
        val l = chargeur(s)
        l.want(listOf(food(a)))
        advanceUntilIdle()
        assertTrue(l.state.value.pending)
        s.enCours.getValue(a).complete(PoiFetch(mapOf(PoiGroup.FOOD to emptyList()), failed = false))
        advanceUntilIdle()
        assertFalse(l.state.value.pending)
    }
}
