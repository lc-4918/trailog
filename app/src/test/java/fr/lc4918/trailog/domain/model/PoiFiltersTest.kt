package fr.lc4918.trailog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les filtres des points d'interet : ce qu'on coche, et ce que le service recoit.
 *
 * La faute qu'ils attrapent est muette : un filtre mal traduit ne leve pas, il fait disparaitre des lieux
 * de la carte - ou en fait apparaitre qu'on avait decoches - sans que rien ne le dise.
 */
class PoiFiltersTest {

    /**
     * Ce sont les categories MASQUEES qu'on enregistre, et c'est tout l'interet : un reglage vide veut
     * dire "tout montrer", ce qui est le comportement attendu d'une couche qu'on vient d'allumer.
     */
    @Test fun `sans rien de regle, tout est affiche`() {
        val f = PoiFilters()
        assertEquals(PoiCategory.entries.size, f.shown.size)
        PoiCategory.entries.forEach { assertTrue(it.key, f.isShown(it)) }
    }

    /**
     * Et une categorie ajoutee par une version ULTERIEURE apparait d'elle-meme : elle n'est pas dans la
     * liste des masquees, donc elle s'affiche. Une liste d'affichees l'aurait laissee invisible jusqu'a ce
     * que l'utilisateur aille la chercher dans les reglages.
     */
    @Test fun `une categorie inconnue du reglage enregistre reste affichee`() {
        val f = PoiFilters.of("hotels", "")
        assertFalse(f.isShown(PoiCategory.HOTELS))
        assertTrue("les autres restent visibles", f.isShown(PoiCategory.CAMPINGS))
        assertEquals(PoiCategory.entries.size - 1, f.shown.size)
    }

    @Test fun `cocher et decocher une categorie`() {
        val f = PoiFilters().toggle(PoiCategory.BARS)
        assertFalse(f.isShown(PoiCategory.BARS))
        assertTrue(f.toggle(PoiCategory.BARS).isShown(PoiCategory.BARS))
    }

    // ---------- La case du groupe, a trois etats ----------

    @Test fun `un groupe entier coche, vide, ou entre les deux`() {
        val tout = PoiFilters()
        assertEquals(GroupCheck.ALL, tout.groupState(PoiGroup.FOOD))
        val partiel = tout.toggle(PoiCategory.BARS)
        assertEquals(GroupCheck.SOME, partiel.groupState(PoiGroup.FOOD))
        val rien = partiel.toggle(PoiCategory.RESTAURANTS)
        assertEquals(GroupCheck.NONE, rien.groupState(PoiGroup.FOOD))
    }

    /** "Tout selectionner" decoche quand tout etait coche, et coche dans tous les autres cas - y compris
     *  depuis une selection partielle, ou l'on attend qu'il complete plutot qu'il ne vide. */
    @Test fun `tout selectionner coche, sauf si tout etait deja coche`() {
        val vide = PoiFilters().toggleGroup(PoiGroup.FOOD)
        assertEquals(GroupCheck.NONE, vide.groupState(PoiGroup.FOOD))
        val plein = vide.toggleGroup(PoiGroup.FOOD)
        assertEquals(GroupCheck.ALL, plein.groupState(PoiGroup.FOOD))
        val depuisPartiel = PoiFilters().toggle(PoiCategory.BARS).toggleGroup(PoiGroup.FOOD)
        assertEquals(GroupCheck.ALL, depuisPartiel.groupState(PoiGroup.FOOD))
    }

    /** Un groupe ne touche pas a ses voisins : decocher toute la restauration laisse les hebergements. */
    @Test fun `un groupe ne touche pas aux autres`() {
        val f = PoiFilters().toggleGroup(PoiGroup.FOOD)
        assertEquals(GroupCheck.ALL, f.groupState(PoiGroup.LODGING))
    }

    // ---------- Le filtre velo, par groupe ----------

    /** Par groupe et non global : on veut des hebergements qui accueillent les cyclistes sans exiger la
     *  meme chose des points d'eau. */
    @Test fun `le filtre velo se pose groupe par groupe`() {
        val f = PoiFilters().toggleBike(PoiGroup.LODGING)
        assertTrue(f.isBikeOnly(PoiGroup.LODGING))
        assertFalse(f.isBikeOnly(PoiGroup.PRACTICAL))
    }

    /**
     * Deux requetes au plus, et pas une par groupe : le service accepte une liste de classes, et le filtre
     * velo est la seule chose qui separe vraiment deux demandes.
     */
    @Test fun `les categories se repartissent en deux demandes`() {
        val f = PoiFilters().toggleBike(PoiGroup.LODGING)
        val (libres, velo) = f.queries()
        assertTrue("les hebergements passent par le filtre velo",
            velo.containsAll(PoiCategory.of(PoiGroup.LODGING)))
        assertTrue("les autres non", libres.none { it.group == PoiGroup.LODGING })
        assertEquals(PoiCategory.entries.size, libres.size + velo.size)
    }

    /** Une categorie decochee ne pese dans aucune des deux demandes. */
    @Test fun `une categorie decochee n'est demandee nulle part`() {
        val f = PoiFilters().toggle(PoiCategory.TOILETS).toggleBike(PoiGroup.PRACTICAL)
        val (libres, velo) = f.queries()
        assertFalse(PoiCategory.TOILETS in libres)
        assertFalse(PoiCategory.TOILETS in velo)
    }

    /** Rien de coche : aucune demande, et le client s'abstient d'appeler (cf. Datatourisme.catalogUrl). */
    @Test fun `tout decoche ne demande rien`() {
        var f = PoiFilters()
        PoiGroup.entries.forEach { f = f.toggleGroup(it) }
        val (libres, velo) = f.queries()
        assertTrue(libres.isEmpty() && velo.isEmpty())
    }

    // ---------- Forme enregistree ----------

    @Test fun `la forme enregistree se relit a l'identique`() {
        val f = PoiFilters().toggle(PoiCategory.BARS).toggle(PoiCategory.TOILETS)
            .toggleBike(PoiGroup.LODGING)
        val relu = PoiFilters.of(f.hiddenCsv(), f.bikeCsv())
        assertEquals(f, relu)
    }

    /** Une cle inconnue - reglage ecrit par une version plus recente, rouvert par une plus ancienne - est
     *  ignoree et n'emporte pas les autres. */
    @Test fun `une cle inconnue ne fait pas tomber les autres`() {
        val f = PoiFilters.of("bars,categorie-de-demain,toilet", "hebergements,groupe-de-demain")
        assertEquals(setOf(PoiCategory.BARS, PoiCategory.TOILETS), f.hidden)
        assertEquals(setOf(PoiGroup.LODGING), f.bikeGroups)
    }

    @Test fun `un reglage vide ne masque rien`() {
        assertEquals(PoiFilters(), PoiFilters.of("", ""))
        assertEquals(PoiFilters(), PoiFilters.of(null, null))
    }
}
