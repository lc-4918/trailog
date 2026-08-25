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
 *
 * **Le filtre EST desormais l'interrupteur de la couche** (cf. [PoiFilters]) : la carte montre exactement
 * ce qu'il retient, et tout masquer l'eteint. C'est ce qui donne son poids a [PoiFilters.nothingShown],
 * eprouve plus bas.
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
        val f = PoiFilters.of("hotels")
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

    // ---------- Tout masquer : l'extinction de la couche ----------

    /**
     * Tout masquer d'un geste ETEINT la couche : c'est la seule facon de le faire depuis que le filtre a
     * remplace l'interrupteur, et il ne doit donc rien laisser passer.
     */
    @Test fun `tout masquer ne laisse rien`() {
        val f = PoiFilters().hideAll()
        assertTrue(f.nothingShown)
        assertTrue(f.shown.isEmpty())
        PoiCategory.entries.forEach { assertFalse(it.key, f.isShown(it)) }
    }

    /** Une seule categorie retenue suffit a rallumer la couche : l'extinction est un TOUT, pas un seuil. */
    @Test fun `une seule categorie retenue rallume la couche`() {
        val f = PoiFilters().hideAll().toggle(PoiCategory.WATER)
        assertFalse(f.nothingShown)
        assertEquals(setOf(PoiCategory.WATER), f.shown)
    }

    /** Decocher les quatre groupes un a un revient au meme que tout masquer : deux chemins, un seul etat. */
    @Test fun `decocher les quatre groupes equivaut a tout masquer`() {
        var f = PoiFilters()
        PoiGroup.entries.forEach { f = f.toggleGroup(it) }
        assertEquals(PoiFilters().hideAll(), f)
        assertTrue(f.nothingShown)
    }

    /** Le neuf part de rien : la forme enregistree d'une installation neuve n'affiche aucune categorie,
     *  faute de quoi la couche s'allumerait toute seule au premier lancement (cf. `SettingsEntity`). */
    @Test fun `la forme enregistree d'une installation neuve ne montre rien`() {
        assertTrue(PoiFilters.of(PoiFilters.allHiddenCsv()).nothingShown)
    }

    // ---------- Forme enregistree ----------

    @Test fun `la forme enregistree se relit a l'identique`() {
        val f = PoiFilters().toggle(PoiCategory.BARS).toggle(PoiCategory.TOILETS)
        assertEquals(f, PoiFilters.of(f.hiddenCsv()))
        val tout = PoiFilters().hideAll()
        assertEquals(tout, PoiFilters.of(tout.hiddenCsv()))
    }

    /** Une cle inconnue - reglage ecrit par une version plus recente, rouvert par une plus ancienne - est
     *  ignoree et n'emporte pas les autres. */
    @Test fun `une cle inconnue ne fait pas tomber les autres`() {
        val f = PoiFilters.of("bars,categorie-de-demain,toilet")
        assertEquals(setOf(PoiCategory.BARS, PoiCategory.TOILETS), f.hidden)
    }

    @Test fun `un reglage vide ne masque rien`() {
        assertEquals(PoiFilters(), PoiFilters.of(""))
        assertEquals(PoiFilters(), PoiFilters.of(null))
    }
}
