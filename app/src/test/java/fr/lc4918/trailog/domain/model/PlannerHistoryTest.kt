package fr.lc4918.trailog.domain.model

import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'historique des lieux du planificateur : ce qu'on repropose au focus d'un champ vide.
 *
 * Un confort, et c'est ce qui dicte sa tenue : une forme enregistree illisible ne doit jamais empecher le
 * planificateur de s'ouvrir. Il vaut mieux perdre l'historique que le trajet.
 */
class PlannerHistoryTest {

    private fun lieu(nom: String, lon: Double = 1.0, lat: Double = 43.0) = GeocodePlace(nom, lon, lat)

    /** Le plus recent en tete : c'est celui qu'on veut reposer le plus souvent. */
    @Test fun `le dernier lieu retenu passe devant`() {
        val h = PlannerHistory() + lieu("Mirepoix") + lieu("Soreze")
        assertEquals(listOf("Soreze", "Mirepoix"), h.places.map { it.label })
    }

    /**
     * Cinq, pas davantage : la liste s'affiche sous un champ, au-dessus du clavier, et au-dela elle
     * chasserait de l'ecran les propositions du geocodeur - celles qu'on est en train de taper.
     */
    @Test fun `l'historique s'arrete a cinq`() {
        var h = PlannerHistory()
        listOf("a", "b", "c", "d", "e", "f", "g").forEach { h += lieu(it) }
        assertEquals(5, h.places.size)
        assertEquals(listOf("g", "f", "e", "d", "c"), h.places.map { it.label })
    }

    /**
     * Le meme lieu deux fois REMONTE au lieu de s'ajouter. Sans cela, quelques allers-retours entre chez
     * soi et le col voisin rempliraient la liste de deux entrees repetees, et l'historique ne se
     * souviendrait plus de rien d'autre.
     */
    @Test fun `un lieu deja connu remonte sans se dupliquer`() {
        val h = PlannerHistory() + lieu("a") + lieu("b") + lieu("a")
        assertEquals(listOf("a", "b"), h.places.map { it.label })
    }

    /** Les coordonnees font le voyage : c'est ce qui permet de reposer le lieu sans redemander quoi que ce
     *  soit au geocodeur. */
    @Test fun `la forme enregistree se relit a l'identique`() {
        val h = PlannerHistory() + lieu("Mirepoix, 09500 Ariege, France", 1.8743, 43.0881)
        val relu = PlannerHistory.of(h.asText())
        assertEquals(h.places.map { it.label }, relu.places.map { it.label })
        assertEquals(1.8743, relu.places.first().lon, 1e-9)
        assertEquals(43.0881, relu.places.first().lat, 1e-9)
    }

    /** Une adresse porte toujours des virgules : le separateur ne peut pas en etre une. */
    @Test fun `un libelle a virgules survit a l'enregistrement`() {
        val nom = "Mirepoix, 09500 Ariege, France"
        assertEquals(nom, PlannerHistory.of((PlannerHistory() + lieu(nom)).asText()).places.single().label)
    }

    /** Une ligne illisible est ignoree, jamais fatale : c'est un confort, il ne doit pas priver le
     *  planificateur de s'ouvrir. */
    @Test fun `une ligne abimee ne fait pas tomber les autres`() {
        val h = PlannerHistory.of("Mirepoix\t1.8\t43.0\nligne cassee\nSoreze\tpasunnombre\t43.4\nRevel\t2.0\t43.4")
        assertEquals(listOf("Mirepoix", "Revel"), h.places.map { it.label })
    }

    @Test fun `un historique vide ne propose rien`() {
        assertTrue(PlannerHistory.of("").places.isEmpty())
        assertTrue(PlannerHistory.of(null).places.isEmpty())
        assertEquals("", PlannerHistory().asText())
    }
}
