package fr.lc4918.trailog.ui.planner

import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackStats
import fr.lc4918.trailog.geocode.GeocodePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---------- Le depart part d'ou l'on est ----------

    /**
     * Le suivi allume, le planificateur s'ouvre sur un depart deja pose.
     *
     * La position actuelle etait deja offerte en tete des suggestions, au focus d'un champ vierge - encore
     * fallait-il toucher le champ pour la voir, et la choisir. Or quelqu'un qui a le suivi allume et qui
     * demande un itineraire part, presque toujours, de la ou il se tient.
     */
    @Test fun `le planificateur ouvert avec le suivi part de la position`() {
        val etat = RoutePlannerState()
        etat.openPlanner(fromCurrentPosition = true)
        assertEquals(StepTarget.CurrentPosition, etat.steps.first().target)
        assertNull("l'arrivee reste a saisir", etat.steps.last().target)
    }

    /** Sans suivi, rien n'est pose : le champ reste vierge, et la suggestion au focus fait son office. */
    @Test fun `sans suivi, le depart reste vierge`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        assertNull(etat.steps.first().target)
    }

    /**
     * Un depart deja pose n'est pas ecrase.
     *
     * Le cas se produit vraiment : on touche un point d'interet, on demande "Depart", puis on rouvre la
     * bande. Ecraser serait perdre le geste qu'on vient de faire.
     */
    @Test fun `un depart deja pose survit a l'ouverture`() {
        val etat = RoutePlannerState()
        etat.setStart(lieu("Mirepoix"))
        etat.openPlanner(fromCurrentPosition = true)
        assertEquals(StepTarget.Place(lieu("Mirepoix")), etat.steps.first().target)
    }

    /**
     * Un depart qu'on a commence a taper n'est pas ecrase non plus.
     *
     * `untouched` et non la vacuite du champ : effacer entierement une saisie la rend vide sans la rendre
     * vierge, et la position ressurgirait sous les doigts.
     */
    @Test fun `un depart en cours de saisie survit a l'ouverture`() {
        val etat = RoutePlannerState()
        etat.type(etat.steps.first(), "Mire")
        etat.openPlanner(fromCurrentPosition = true)
        assertNull(etat.steps.first().target)
    }

    /**
     * La position deja posee ailleurs n'est pas posee deux fois.
     *
     * Partir d'ou l'on est pour y revenir donne un troncon de longueur nulle, que le moteur refuse.
     */
    @Test fun `la position posee a l'arrivee n'est pas reposee au depart`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.last(), StepTarget.CurrentPosition)
        etat.openPlanner(fromCurrentPosition = true)
        assertNull(etat.steps.first().target)
    }

    // ---------- L'autre bout du trajet se complete tout seul ----------

    /**
     * Une arrivee posee depuis une infobulle, le capteur allume : le depart part d'ou l'on est.
     *
     * Le testeur l'a rencontre sur un camping : designer un camping comme arrivee, c'est demander a s'y
     * rendre - et cela part d'ou l'on se tient. Le depart restait vide, et il fallait deplier la bande
     * pour le remplir a la main.
     */
    @Test fun `une arrivee posee complete le depart par la position`() {
        val etat = RoutePlannerState()
        etat.setEnd(lieu("Camping"), completeFromCurrentPosition = true)
        assertEquals(StepTarget.Place(lieu("Camping")), etat.steps.last().target)
        assertEquals(StepTarget.CurrentPosition, etat.steps.first().target)
    }

    /** Symetrique : un depart pose depuis une infobulle fait de la position l'arrivee. */
    @Test fun `un depart pose complete l'arrivee par la position`() {
        val etat = RoutePlannerState()
        etat.setStart(lieu("Camping"), completeFromCurrentPosition = true)
        assertEquals(StepTarget.Place(lieu("Camping")), etat.steps.first().target)
        assertEquals(StepTarget.CurrentPosition, etat.steps.last().target)
    }

    /** Le capteur eteint, rien ne se complete : une etape "d'ou je suis" que rien ne resout ferait
     *  echouer le calcul au lieu de laisser un champ a remplir. */
    @Test fun `sans capteur, l'autre bout reste vierge`() {
        val etat = RoutePlannerState()
        etat.setEnd(lieu("Camping"))
        assertNull(etat.steps.first().target)
    }

    /** L'autre bout deja pose n'est pas ecrase : c'est le geste de l'utilisateur, pas une place libre. */
    @Test fun `un autre bout deja pose n'est pas ecrase`() {
        val etat = RoutePlannerState()
        etat.setStart(lieu("Mirepoix"))
        etat.setEnd(lieu("Camping"), completeFromCurrentPosition = true)
        assertEquals(StepTarget.Place(lieu("Mirepoix")), etat.steps.first().target)
    }

    /** Ni celui qu'on a commence a taper (cf. `untouched`). */
    @Test fun `un autre bout en cours de saisie n'est pas ecrase`() {
        val etat = RoutePlannerState()
        etat.type(etat.steps.first(), "Mire")
        etat.setEnd(lieu("Camping"), completeFromCurrentPosition = true)
        assertNull(etat.steps.first().target)
    }

    /**
     * La position deja posee en etape intermediaire ne se repose pas au depart : partir d'ou l'on est
     * pour y repasser donne un troncon de longueur nulle, que le moteur refuse.
     */
    @Test fun `la position deja posee ailleurs ne se repose pas`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps[1], StepTarget.CurrentPosition)
        etat.setEnd(lieu("Camping"), completeFromCurrentPosition = true)
        assertNull(etat.steps.first().target)
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

    // ---------- Abandonner un trajet en cours ----------

    /**
     * Le retour Android sur une bande deja repliee DEMANDE : il ne ferme pas.
     *
     * C'est le meme geste que celui qui quitte l'application, et un trajet compose etape par etape ne se
     * perd pas sur un geste distrait. La question posee, rien n'est encore perdu.
     */
    @Test fun `la question posee ne ferme rien`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.askCancel()
        assertTrue("la boite est ouverte", etat.cancelDialog)
        assertTrue("le planificateur aussi", etat.open)
        assertEquals(StepTarget.Place(lieu("Mirepoix")), etat.steps.first().target)
    }

    /** "Non" : la boite se referme sur un trajet intact. */
    @Test fun `renoncer a annuler rend le trajet tel quel`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.askCancel()
        etat.dismissCancel()
        assertFalse(etat.cancelDialog)
        assertTrue(etat.open)
        assertEquals(StepTarget.Place(lieu("Mirepoix")), etat.steps.first().target)
    }

    /** "Oui" : tout part, la boite comprise - elle ne doit pas ressurgir a la reouverture. */
    @Test fun `fermer emporte le trajet et la question`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.askCancel()
        etat.close()
        assertFalse(etat.cancelDialog)
        assertFalse(etat.open)
        assertNull("feuille vierge", etat.steps.first().target)
    }

    /** Repliee, la bande reste OUVERTE : c'est ce qui distingue les deux appuis du retour, et ce qui fait
     *  que le bouton de la carte rouvre le trajet en cours au lieu d'en commencer un autre. */
    @Test fun `replier ne ferme pas`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.collapse(true)
        assertTrue(etat.open)
        assertTrue(etat.collapsed)
        etat.collapse(false)
        assertTrue(etat.open)
        assertFalse(etat.collapsed)
    }

    // ---------- Un planificateur vide se traite comme deja ferme ----------

    private val parcoursVide = ComputedTrack(
        samples = listOf(
            Sample(0.0, 210.0, 0.0, null, 5.70, 45.20),
            Sample(120.0, 224.0, 11.6, null, 5.71, 45.21),
        ),
        stats = TrackStats(120.0, 14.0, 0.0, 210.0, 224.0, 11.6, null, 2),
        hasZ = true,
        hasTime = false,
    )

    @Test fun `un planificateur tout juste ouvert est vide`() {
        val etat = RoutePlannerState()
        assertTrue(etat.isEmpty)
    }

    @Test fun `une etape choisie n'est plus vide`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        assertFalse(etat.isEmpty)
    }

    /** La position actuelle ne compte pas comme une saisie : elle se pose toute seule a l'ouverture (cf.
     *  openPlanner(fromCurrentPosition = true)), et non par un geste de l'utilisateur. */
    @Test fun `la position actuelle seule reste vide`() {
        val etat = RoutePlannerState()
        etat.openPlanner(fromCurrentPosition = true)
        assertEquals(StepTarget.CurrentPosition, etat.steps.first().target)
        assertTrue(etat.isEmpty)
    }

    /** Une frappe en cours compte deja, avant meme qu'un lieu soit choisi. */
    @Test fun `une frappe en cours n'est plus vide`() {
        val etat = RoutePlannerState()
        etat.type(etat.steps.first(), "Mir")
        assertFalse(etat.isEmpty)
    }

    @Test fun `un parcours calcule n'est plus vide`() {
        val etat = RoutePlannerState()
        etat.publish(RouteState.Done(120.0, 600.0, parcoursVide))
        assertFalse(etat.isEmpty)
    }

    /**
     * Reduire un planificateur vide le ferme au lieu de le ranger : il n'y a rien a retrouver plus tard,
     * et le laisser "ouvert-reduit" allumerait pour rien le bouton de la carte.
     */
    @Test fun `reduire un planificateur vide le ferme`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.collapseOrClose()
        assertFalse("ferme, pas seulement reduit", etat.open)
        assertFalse(etat.collapsed)
    }

    /** Un planificateur qui porte quelque chose se reduit normalement, sans se fermer. */
    @Test fun `reduire un planificateur rempli le range`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.collapseOrClose()
        assertTrue("range, pas ferme", etat.open)
        assertTrue(etat.collapsed)
    }

    /** La croix de l'en-tete ferme sans rien demander quand il n'y a rien a perdre. */
    @Test fun `la croix sur un planificateur vide ferme sans demander`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.requestClose()
        assertFalse(etat.open)
        assertFalse("aucune question posee", etat.cancelDialog)
    }

    /** Elle demande des qu'il y a une saisie en cours, un lieu pose, ou un parcours calcule a perdre -
     *  la meme question que le retour Android sur une bande repliee. */
    @Test fun `la croix sur un planificateur rempli demande`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.requestClose()
        assertTrue("le planificateur reste ouvert", etat.open)
        assertTrue("la question est posee", etat.cancelDialog)
    }

    // ---------- Une etape montree du doigt sur la carte ----------

    /**
     * Choisir "un point sur la carte" RANGE la bande.
     *
     * Elle occupe le bas de l'ecran et le clavier le reste : sans ce repli, l'endroit qu'on doit montrer
     * serait justement celui qu'on ne voit pas.
     */
    @Test fun `montrer un point range la bande`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        assertTrue("le mode est actif", etat.pickingOnMap)
        assertTrue("la carte est rendue", etat.collapsed)
        assertTrue("le trajet n'est pas ferme pour autant", etat.open)
    }

    /**
     * Le tap sur la carte pose l'etape et redeploie la bande, avec les coordonnees pour libelle : le
     * calcul n'attend pas l'adresse, qui n'est qu'un nom (cf. `nameMapPoint`).
     */
    @Test fun `le point montre devient une etape et rend la bande`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.pickOnMap(6.08, 44.56, "44.56000, 6.08000")
        val pose = etat.steps.first().target as StepTarget.Place
        assertEquals(6.08, pose.place.lon, 1e-9)
        assertEquals(44.56, pose.place.lat, 1e-9)
        assertEquals("44.56000, 6.08000", pose.place.label)
        assertFalse("le mode est termine", etat.pickingOnMap)
        assertFalse("la bande revient", etat.collapsed)
        assertEquals("une epingle, la ou l'on a montre", listOf(6.08 to 44.56), etat.mapPins)
        assertTrue("l'adresse reste a chercher", etat.steps.first().addressPending)
    }

    /**
     * L'adresse arrivee remplace les coordonnees SANS relancer le calcul : le point n'a pas bouge, seul
     * son nom a change. Recalculer renverrait au moteur un parcours deja calcule, et le perdrait sur un
     * service muet.
     */
    @Test fun `l'adresse remplace les coordonnees sans recalculer`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.pickOnMap(6.08, 44.56, "44.56000, 6.08000")
        val avant = etat.revision
        etat.nameMapPoint(etat.steps.first(), listOf("Col de Vars", "Hautes-Alpes"))
        val pose = etat.steps.first().target as StepTarget.Place
        assertEquals("Col de Vars, Hautes-Alpes", pose.place.label)
        assertEquals("le point n'a pas bouge", 6.08, pose.place.lon, 1e-9)
        assertEquals("rien a recalculer", avant, etat.revision)
        assertFalse(etat.steps.first().addressPending)
    }

    /**
     * Service muet, ou rien a cet endroit : l'etape garde ses coordonnees et reste parfaitement valable.
     * Un point au milieu d'un bois est peut-etre le depart du sentier.
     */
    @Test fun `sans adresse, l'etape garde ses coordonnees`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.pickOnMap(6.08, 44.56, "44.56000, 6.08000")
        etat.nameMapPoint(etat.steps.first(), null)
        val pose = etat.steps.first().target as StepTarget.Place
        assertEquals("44.56000, 6.08000", pose.place.label)
        assertFalse("on cesse de l'attendre", etat.steps.first().addressPending)
    }

    /** Redeployer la bande sort du mode : les deux ne peuvent pas occuper l'ecran ensemble, et c'est la
     *  bande qu'on vient de redemander. */
    @Test fun `redeployer la bande sort du choix`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.collapse(false)
        assertFalse(etat.pickingOnMap)
    }

    /** L'epingle disparait avec l'etape : elle ne montrait qu'elle. */
    @Test fun `effacer l'etape retire son epingle`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.pickOnMap(6.08, 44.56, "44.56000, 6.08000")
        etat.clearStep(etat.steps.first())
        assertTrue(etat.mapPins.isEmpty())
    }

    /** Taper par-dessus la retire aussi : la frappe remplace le point, l'epingle ne montre plus rien. */
    @Test fun `taper par-dessus retire l'epingle`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.pickOnMap(6.08, 44.56, "44.56000, 6.08000")
        etat.type(etat.steps.first(), "Mire")
        assertTrue(etat.mapPins.isEmpty())
    }

    /**
     * L'epingle traverse la mort du processus avec son etape : le parcours revient du disque, et l'endroit
     * qu'on avait montre doit rester montre. Son adresse, elle, est deja dans le libelle - rien a
     * redemander au geocodeur.
     */
    @Test fun `l'epingle revient du disque avec l'etape`() {
        val avant = RoutePlannerState()
        avant.openPlanner()
        avant.startPickingOnMap(avant.steps.first())
        avant.pickOnMap(6.08, 44.56, "44.56000, 6.08000")
        avant.nameMapPoint(avant.steps.first(), listOf("Col de Vars"))
        avant.choose(avant.steps.last(), StepTarget.Place(lieu("Mirepoix")))
        avant.publish(RouteState.Done(120.0, 600.0, parcoursVide))

        val apres = RoutePlannerState()
        apres.restore(avant.snapshot())
        assertEquals(listOf(6.08 to 44.56), apres.mapPins)
        assertFalse("l'adresse est deja la", apres.steps.first().addressPending)
        assertEquals("Col de Vars", (apres.steps.first().target as StepTarget.Place).place.label)
    }

    /** Un lieu cherche au clavier ne pose aucune epingle : il en porte deja une, celle du geocodage. */
    @Test fun `un lieu cherche ne pose pas d'epingle`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        assertTrue(etat.mapPins.isEmpty())
    }

    // ---------- Position introuvable ----------

    /**
     * Une position qu'on ne sait pas resoudre n'est PAS un trajet impossible.
     *
     * Les deux publiaient le meme etat, donc le meme message - "Aucun itineraire" - alors que le moteur
     * n'avait meme pas ete interroge. On cherchait la faute du cote de la discipline ou des etapes, la ou
     * il n'y avait qu'un capteur muet.
     */
    @Test fun `la position introuvable ne se dit pas aucun itineraire`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.publish(RouteState.NoPosition)
        assertEquals(RouteState.NoPosition, etat.route)
        assertNull("rien a afficher", etat.done)
    }

    /** Comme tout echec, elle laisse le planificateur ouvert et le curseur retombe : il designait un
     *  parcours qui n'existe plus. */
    @Test fun `la position introuvable retire le curseur`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.publish(RouteState.Done(120.0, 600.0, parcoursVide))
        etat.tapProfile(50.0)
        etat.publish(RouteState.NoPosition)
        assertNull(etat.cursor)
    }

    /** Fermer le trajet sort du mode : la consigne ne doit pas survivre au planificateur qui l'a ouverte. */
    @Test fun `fermer le trajet sort du choix`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.startPickingOnMap(etat.steps.first())
        etat.close()
        assertFalse(etat.pickingOnMap)
    }
}
