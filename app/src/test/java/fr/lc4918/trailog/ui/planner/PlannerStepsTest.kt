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
     * La position du porteur sert deja quelque part : les poses AUTOMATIQUES s'en abstiennent alors (cf.
     * les cas plus bas). L'utilisateur, lui, reste libre de la designer ailleurs.
     */
    @Test fun `la position actuelle posee se voit`() {
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

    // ---------- La boucle : d'ou l'on est, et retour ----------

    /**
     * Le depart pose sur la position du porteur, une etape intermediaire posee ailleurs : l'arrivee peut
     * reprendre la position, et le trajet fait une boucle. C'est le trajet le plus courant a pied comme a
     * velo, et la bande le refusait.
     */
    @Test fun `la position ferme une boucle`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        etat.choose(etat.steps[1], StepTarget.Place(lieu("Col de Peyresourde")))
        assertTrue("l'arrivee peut la reprendre", etat.canUseCurrentPosition(etat.steps.last()))
        etat.choose(etat.steps.last(), StepTarget.CurrentPosition)
        assertEquals(3, etat.targets.size)
    }

    /** Deux etapes VOISINES sur la position, en revanche : le troncon entre elles serait de longueur
     *  nulle, et le moteur refuse la requete entiere. La bande ne le propose donc pas. */
    @Test fun `la position ne se propose pas a cote d'elle-meme`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        assertFalse("la voisine", etat.canUseCurrentPosition(etat.steps[1]))
        assertTrue("deux rangs plus loin", etat.canUseCurrentPosition(etat.steps.last()))
    }

    /** Et si un deplacement de lignes colle malgre tout deux poses identiques, le doublon ne part pas au
     *  moteur : le trajet garde ses deux bouts. */
    @Test fun `deux poses collees ne font qu'un point`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        etat.choose(etat.steps[1], StepTarget.Place(lieu("Col de Peyresourde")))
        etat.choose(etat.steps.last(), StepTarget.CurrentPosition)
        etat.moveStep(2, -1)
        assertEquals(listOf(StepTarget.CurrentPosition, StepTarget.Place(lieu("Col de Peyresourde"))),
            etat.targets)
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

    // ---------- L'etape ajoutee trouve son segment ----------

    /**
     * Une etape ajoutee depuis une infobulle se glisse dans le segment auquel elle appartient : celui
     * dont les deux bouts, additionnes, sont les plus proches d'elle.
     *
     * Elle remplissait la premiere ligne vierge venue, et se posait avant l'arrivee a defaut : sur un
     * trajet deja compose, sa place disait l'etat des champs et non l'endroit qu'on venait de montrer du
     * doigt, et il fallait la remonter a la main.
     */
    @Test fun `l'etape ajoutee se pose dans le bon segment`() {
        val etat = RoutePlannerState()
        poseTrajet(etat, 0.0, 1.0, 2.0, 3.0)
        assertTrue(etat.addWaypoint(borne("Tarabel", 2.4)))
        assertEquals(listOf(0.0, 1.0, 2.0, 2.4, 3.0), longitudes(etat))
    }

    /** Le meme point pres du PREMIER segment s'y pose : rien ne le pousse plus vers l'arrivee. */
    @Test fun `l'etape ajoutee pres du depart s'y pose`() {
        val etat = RoutePlannerState()
        poseTrajet(etat, 0.0, 1.0, 2.0, 3.0)
        assertTrue(etat.addWaypoint(borne("Tarabel", 0.4)))
        assertEquals(listOf(0.0, 0.4, 1.0, 2.0, 3.0), longitudes(etat))
    }

    /** Une ligne vierge ne capte plus l'etape : elle reste vide a sa place, et l'etape va dans son
     *  segment. Une etape montree sur la carte n'est pas la reponse a un champ vide. */
    @Test fun `une ligne vierge ne capte plus l'etape ajoutee`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps.first(), StepTarget.Place(borne("Toulouse", 0.0)))
        etat.choose(etat.steps.last(), StepTarget.Place(borne("Foix", 3.0)))
        assertTrue(etat.addWaypoint(borne("Saverdun", 1.0)))
        assertEquals(listOf(0.0, 1.0, 3.0), longitudes(etat))
        assertTrue("la vierge est restee vierge", etat.steps.any { it.target == null })
    }

    /** Moins de deux etapes posees : aucun segment a comparer, et la ligne vierge qu'on a devant soi
     *  reprend la main - c'est elle que l'utilisateur attend de voir se remplir. */
    @Test fun `sans segment, l'etape ajoutee remplit la ligne vierge`() {
        val etat = RoutePlannerState()
        etat.choose(etat.steps.first(), StepTarget.Place(borne("Toulouse", 0.0)))
        assertTrue(etat.addWaypoint(borne("Foix", 3.0)))
        assertEquals(2, etat.steps.size)
        assertEquals(listOf(0.0, 3.0), longitudes(etat))
    }

    /**
     * Une etape posee sur la position du porteur borne un segment comme une autre - a condition qu'on
     * sache ou l'on est : elle ne porte aucune coordonnee, et c'est la derniere mesure recue qui lui en
     * prete le temps du choix.
     */
    @Test fun `la position actuelle borne un segment quand on la connait`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        etat.choose(etat.steps[1], StepTarget.Place(borne("Auterive", 2.0)))
        etat.choose(etat.steps.last(), StepTarget.Place(borne("Foix", 3.0)))
        assertTrue(etat.addWaypoint(borne("Saverdun", 0.5), currentPos = 0.0 to 43.0))
        assertEquals(listOf(0.5, 2.0, 3.0), longitudes(etat))
    }

    /** Sans position connue, elle ne borne rien : le seul segment qui reste est celui des deux lieux. */
    @Test fun `la position actuelle inconnue ne borne rien`() {
        val etat = RoutePlannerState()
        etat.addStep()
        etat.choose(etat.steps.first(), StepTarget.CurrentPosition)
        etat.choose(etat.steps[1], StepTarget.Place(borne("Auterive", 2.0)))
        etat.choose(etat.steps.last(), StepTarget.Place(borne("Foix", 3.0)))
        assertTrue(etat.addWaypoint(borne("Saverdun", 0.5)))
        assertEquals(listOf(2.0, 0.5, 3.0), longitudes(etat))
    }

    /** Un lieu a sa longitude, sur une meme latitude : les distances se comparent alors comme des
     *  ecarts de longitude, et le segment attendu se lit dans l'enonce du cas. */
    private fun borne(nom: String, lon: Double) = GeocodePlace(nom, lon, 43.0)

    /** Un trajet deja compose : autant d'etapes posees que de longitudes donnees, dans l'ordre. */
    private fun poseTrajet(etat: RoutePlannerState, vararg lons: Double) {
        repeat(lons.size - 2) { etat.addStep() }
        lons.forEachIndexed { i, lon -> etat.choose(etat.steps[i], StepTarget.Place(borne("P$i", lon))) }
    }

    /** Les longitudes des etapes posees, dans l'ordre de la liste : la forme du trajet en une ligne. */
    private fun longitudes(etat: RoutePlannerState) =
        etat.steps.mapNotNull { (it.target as? StepTarget.Place)?.place?.lon }

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

    // ---------- Ranger un trajet, ou le remettre a blanc ----------

    /**
     * La croix de l'en-tete RANGE : le trajet en cours ne s'y perd pas.
     *
     * Elle fermait le planificateur, donc effacait le trajet, et il fallait une question pour l'en
     * empecher - a cote d'un "reduire" qui, lui, gardait tout. Composer un itineraire demande plusieurs
     * gestes, avec des allers-retours vers la carte entre deux : aucun de ces gestes ne peut couter le
     * travail deja fait.
     */
    @Test fun `la croix range sans rien perdre`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.collapseOrClose()
        assertTrue("le planificateur reste ouvert", etat.open)
        assertTrue("mais range", etat.collapsed)
        assertEquals(StepTarget.Place(lieu("Mirepoix")), etat.steps.first().target)
    }

    /** "Reinitialiser" : la feuille redevient vierge, et la bande reste sous les yeux pour le trajet
     *  suivant - c'est bien pour en composer un autre qu'on efface celui-la. */
    @Test fun `reinitialiser vide la feuille et garde la bande`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.reset()
        assertTrue("la bande est toujours la", etat.expanded)
        assertNull("feuille vierge", etat.steps.first().target)
        assertEquals(2, etat.steps.size)
    }

    /** Fermer emporte tout : rouvrir doit donner une feuille vierge, pas le trajet d'hier a moitie
     *  efface. */
    @Test fun `fermer emporte le trajet`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.close()
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

    /** La croix sur un planificateur vide FERME au lieu de ranger : il n'y a rien a retrouver plus tard,
     *  et le laisser "ouvert-reduit" allumerait pour rien le bouton de la carte. */
    @Test fun `la croix sur un planificateur vide ferme`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.collapseOrClose()
        assertFalse("ferme, pas seulement range", etat.open)
        assertFalse(etat.collapsed)
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

    /**
     * Le moteur injoignable ne se dit pas non plus "aucun itineraire".
     *
     * Reseau absent, liaison coupee, delai depasse : la requete n'est jamais arrivee (cf. RouteOutcome).
     * Le message d'un trajet impossible envoie changer de discipline ou deplacer une etape, alors qu'il
     * n'y a rien a corriger - seulement a redemander.
     */
    @Test fun `le moteur injoignable ne se dit pas aucun itineraire`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.publish(RouteState.NoNetwork)
        assertEquals(RouteState.NoNetwork, etat.route)
        assertNull("rien a afficher", etat.done)
    }

    /** "Reessayer" redemande le MEME trajet : rien ne change dans les etapes, seule la revision avance -
     *  c'est elle qui relance le calcul (cf. RouteInputs). */
    @Test fun `reessayer relance le calcul sans toucher aux etapes`() {
        val etat = RoutePlannerState()
        etat.openPlanner()
        etat.choose(etat.steps.first(), StepTarget.Place(lieu("Mirepoix")))
        etat.publish(RouteState.NoNetwork)
        val avant = etat.revision
        etat.retryRoute()
        assertTrue("la revision avance", etat.revision > avant)
        assertEquals("les etapes sont intactes",
            StepTarget.Place(lieu("Mirepoix")), etat.steps.first().target)
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
