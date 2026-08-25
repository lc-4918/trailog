package fr.lc4918.trailog.ui.planner

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.ui.profile.ProfileZoom
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.GeocodePlace

/** Nombre maximal d'etapes. Au-dela, le moteur d'itineraire refuse la requete et la liste devient illisible. */
const val MaxPlannerSteps = 25

/** Ce qu'une etape designe : un lieu trouve par le geocodeur, ou la position du porteur. */
sealed interface StepTarget {
    data class Place(val place: GeocodePlace) : StepTarget
    /**
     * La position du porteur, resolue au moment du calcul et non au moment du choix : l'utilisateur peut
     * poser ses etapes puis se deplacer avant de lancer le trajet, et c'est bien d'ou il est alors qu'il
     * veut partir.
     */
    data object CurrentPosition : StepTarget
}

/** Ou en est le calcul du parcours. */
sealed interface RouteState {
    /** Moins de deux etapes renseignees : il n'y a rien a calculer, et rien a afficher. */
    data object Idle : RouteState
    data object Loading : RouteState
    /** Etapes non reliees dans cette discipline, service muet, reseau absent, ou position inconnue. */
    data object Failed : RouteState
    data class Done(val meters: Double, val seconds: Double, val track: ComputedTrack) : RouteState
}

/**
 * Une etape de l'itineraire : sa saisie, ses propositions, et ce qu'elle designe une fois choisie.
 *
 * [id] stable et non l'index : les etapes se reordonnent et se suppriment, et Compose doit pouvoir suivre
 * une ligne d'une recomposition a l'autre sans confondre son champ de saisie avec celui du voisin.
 */
@Stable
class PlannerStep(val id: Long) {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<GeocodePlace>>(emptyList())
    var searching by mutableStateOf(false)

    /** Le service n'a pas repondu a la derniere frappe. Distinct d'une liste vide, qui signifie qu'il a
     *  bien repondu mais n'a rien trouve : les deux appellent des gestes opposes de l'utilisateur. */
    var failed by mutableStateOf(false)

    /** Incremente par le bouton "Reessayer" : sert de cle de relance a la recherche, la frappe n'ayant
     *  pas change. */
    var retry by mutableStateOf(0)
        private set

    fun askRetry() { retry++ }
    var target by mutableStateOf<StepTarget?>(null)
        internal set

    /**
     * L'utilisateur a-t-il touche au champ depuis qu'il a pris le focus ?
     *
     * Commande la proposition de position actuelle, offerte au focus tant que rien n'a ete tape. Un
     * drapeau et non un test sur la vacuite de [query] : effacer entierement un champ deja saisi le rend
     * vide sans le rendre vierge, et la proposition ressurgirait sous les doigts.
     */
    var untouched by mutableStateOf(true)
        internal set
}

/**
 * Etat du planificateur d'itineraire : ses etapes, sa discipline, le parcours calcule et l'apparence de sa
 * bande.
 *
 * Regroupe ici, et non disperse dans l'ecran de carte : presque tout se declenche en cascade (choisir un
 * lieu recalcule, supprimer une etape recalcule, reordonner recalcule), et ces enchainements sont fautifs
 * a ecrire deux fois.
 */
@Stable
class RoutePlannerState {

    var open by mutableStateOf(false)
        private set

    /** Bande reduite a son bouton de reouverture, la carte restant entierement visible dessous. */
    var collapsed by mutableStateOf(false)
        private set

    /** Toujours au moins deux : depart et arrivee, meme vierges - c'est la forme minimale d'un trajet. */
    val steps = mutableStateListOf(PlannerStep(0), PlannerStep(1))

    var profile by mutableStateOf(RoutingProfile.HYBRID_BIKE)
        private set

    var route by mutableStateOf<RouteState>(RouteState.Idle)
        private set

    /**
     * Un nouveau calcul est en route, le parcours affiche etant celui d'avant.
     *
     * Le parcours n'est PAS retire pendant le recalcul : le retirer ferait disparaitre la zone resultats,
     * donc le profil, donc la moitie de la hauteur de la bande - qui se serait effondree puis retablie a
     * chaque reordonnancement d'etape, pendant une a trois secondes de requete.
     */
    var recomputing by mutableStateOf(false)
        private set

    /**
     * Profil altimetrique deplie sous les totaux.
     *
     * Replie par defaut, et memorise dans l'etat plutot que dans le composable : la bande se recompose a
     * chaque frappe, et un profil qu'on vient d'ouvrir ne doit pas se refermer sous les doigts. Il occupe
     * a lui seul la moitie de la hauteur de la bande, d'ou le repli : on compose d'abord son trajet, on
     * regarde le relief ensuite.
     */
    var profileVisible by mutableStateOf(false)
        private set

    fun toggleProfile() { profileVisible = !profileVisible }

    /**
     * Un champ d'etape a le focus : on est en train de saisir.
     *
     * Sert a rendre le profil altimetrique le temps de la saisie (cf. [profileShown]). Un identifiant et
     * non un booleen : passer d'un champ a l'autre fait perdre le focus au premier APRES que le second
     * l'ait pris, et un simple drapeau se serait rabaisse juste apres avoir ete leve.
     */
    var editingId by mutableStateOf<Long?>(null)
        private set

    fun setEditing(step: PlannerStep, focused: Boolean) {
        if (focused) editingId = step.id
        else if (editingId == step.id) editingId = null
    }

    /**
     * Le profil s'affiche-t-il ?
     *
     * Il se retire le temps d'une saisie, sans que le reglage bouge : le clavier prend la moitie basse de
     * l'ecran, et entre lui et le profil il ne restait plus de place pour les propositions - historique et
     * position actuelle naissaient hors de la zone visible. Il revient des que le champ rend le focus.
     */
    val profileShown: Boolean get() = profileVisible && editingId == null

    /** Point courant sur le parcours, en metres depuis son debut (repere sur la carte + infos du point).
     *  Une abscisse et non un indice, comme pour le profil d'une trace : elle se pose entre deux sommets. */
    var cursor by mutableStateOf<Double?>(null)
        private set

    // ---------- Zoom du profil ----------
    // Pincement et double-tap, et non une selection de bornes : sur un graphique large de quelques
    // centimetres, viser deux points a poser demande plus de precision que le doigt n'en a. Le pincement
    // dit d'un seul geste ou l'on regarde et de combien on grossit.

    /** Portion visible du parcours (indices absolus), ou null pour tout le parcours. */
    var zoomRange by mutableStateOf<IntRange?>(null)
        private set

    /** Premier index de la fenetre affichee : les index rendus par le graphique y sont relatifs. */
    val windowStart: Int get() = zoomRange?.first ?: 0

    val zoomed: Boolean get() = zoomRange != null

    /**
     * Grossit de [scale] autour de [focusFraction] (0 a gauche de la fenetre visible, 1 a droite).
     *
     * [total] est le nombre de points du parcours : la fenetre ne peut ni le depasser ni descendre sous
     * [MinZoomSamples], en deca duquel le graphique n'aurait plus de forme.
     */
    fun zoomBy(scale: Float, focusFraction: Float, total: Int) {
        val next = ProfileZoom.window(zoomRange, total, scale, focusFraction)
        if (next == zoomRange) return
        zoomRange = next
        cursor = null
    }

    fun resetZoom() {
        zoomRange = null
        cursor = null
    }

    /**
     * Numero d'ordre du parcours a calculer, incremente par tout ce qui le rend caduc.
     *
     * Cle de relance du calcul, et non les etapes elles-memes : c'est le seul moyen de distinguer "la
     * liste a change" de "elle a ete recomposee", et de relancer sur un reordonnancement qui laisse la
     * liste identique a l'egalite pres.
     */
    var revision by mutableStateOf(0)
        private set

    private var nextId = 2L

    /** Les etapes reellement posees, dans l'ordre. En deca de deux, il n'y a pas de trajet. */
    val targets: List<StepTarget> get() = steps.mapNotNull { it.target }

    val canAddStep: Boolean get() = steps.size < MaxPlannerSteps

    /**
     * La position du porteur est deja une etape du trajet.
     *
     * Elle ne peut en etre qu'une : un itineraire qui partirait d'ou l'on est pour y revenir n'a pas de
     * longueur, et le moteur rendrait un trajet nul. Tant qu'elle sert, on cesse de la proposer.
     */
    val usesCurrentPosition: Boolean get() = steps.any { it.target == StepTarget.CurrentPosition }

    val done: RouteState.Done? get() = route as? RouteState.Done

    /**
     * Ouvre le planificateur, en partant d'ou l'on est si [fromCurrentPosition].
     *
     * **Pre-rempli et non propose.** La position actuelle etait deja offerte en tete des suggestions, au
     * focus d'un champ vierge (cf. RoutePlannerBand) - encore fallait-il toucher le champ pour la voir, et
     * la choisir. Or quelqu'un qui a le suivi allume et qui demande un itineraire part, presque toujours,
     * de la ou il se tient : c'est la reponse par defaut, pas une proposition parmi d'autres. La saisie la
     * remplace des la premiere frappe, comme n'importe quelle etape posee.
     *
     * Ne touche qu'un depart VIERGE : rouvrir sur un trajet en cours, ou un depart qu'on vient de poser
     * depuis une infobulle, ne doit rien ecraser.
     */
    fun openPlanner(fromCurrentPosition: Boolean = false) {
        open = true
        collapsed = false
        if (fromCurrentPosition) startFromCurrentPosition()
    }

    /**
     * Le depart part d'ou l'on est - si le depart est encore vierge, et si la position ne sert pas deja
     * ailleurs dans le trajet.
     */
    fun startFromCurrentPosition() {
        steps.firstOrNull()?.let { useCurrentPosition(it) }
    }

    /**
     * [step] designe la position du porteur, si elle est encore vierge et si la position ne sert pas
     * deja ailleurs dans le trajet.
     *
     * La seconde garde importe : partir d'ou l'on est pour y revenir donne un troncon de longueur nulle,
     * que le moteur refuse. C'est la meme regle que celle de la suggestion, et pour la meme raison.
     *
     * La premiere garde ne REMPLACE jamais : une etape deja posee, ou seulement touchee, est le fait de
     * l'utilisateur, et un automatisme n'a pas a l'effacer sous ses doigts.
     */
    private fun useCurrentPosition(step: PlannerStep) {
        if (usesCurrentPosition) return
        if (step.target != null || !step.untouched) return
        choose(step, StepTarget.CurrentPosition)
    }

    /**
     * Le retour Android demande a abandonner le trajet : la question est posee, rien n'est encore perdu.
     *
     * Elle ne se pose qu'au retour, et sur une bande DEJA repliee - le premier appui replie, le second
     * demande. La croix de l'en-tete, elle, ferme sans rien demander : c'est un geste vise, pose sur le
     * bouton qui dit "fermer", quand le retour est le meme geste que celui qui quitte l'application.
     */
    var cancelDialog by mutableStateOf(false)
        private set

    fun askCancel() { cancelDialog = true }

    fun dismissCancel() { cancelDialog = false }

    /** Ferme et remet a zero : rouvrir le planificateur doit donner une feuille vierge, pas le trajet
     *  d'hier a moitie efface. */
    fun close() {
        open = false
        collapsed = false
        cancelDialog = false
        steps.clear()
        steps.add(PlannerStep(nextId++))
        steps.add(PlannerStep(nextId++))
        route = RouteState.Idle
        recomputing = false
        cursor = null
        resetZoom()
        revision++
    }

    fun collapse(v: Boolean) { collapsed = v }

    /**
     * La bande OCCUPE l'ecran, par opposition a [open], qui dit seulement qu'un trajet existe.
     *
     * **Les deux se confondaient, et cela se voyait.** Presque tout ce qui interroge le planificateur veut
     * savoir s'il prend la place ou l'attention : l'echelle graphique se range sous la bande, le bouton de
     * la carte s'efface sous elle, le suivi de la camera se suspend pendant qu'on compose un trajet. Ces
     * questions-la parlent de la bande, pas du trajet - et lire [open] y repondait "oui" pour une bande
     * reduite, c'est-a-dire justement quand la carte est rendue a l'utilisateur.
     *
     * Le prix se payait sur le terrain : suivre un parcours calcule demande de garder le planificateur
     * ouvert, reduit dans son coin, et la carte cessait alors de suivre la position - le suivi automatique
     * etait allume, et il ne se passait rien.
     */
    val expanded: Boolean get() = open && !collapsed

    fun chooseProfile(p: RoutingProfile) {
        if (p == profile) return
        profile = p
        invalidate()
    }

    fun addStep() {
        if (!canAddStep) return
        steps.add(PlannerStep(nextId++))
        // Pas d'invalidation : une etape vierge n'ajoute aucun point au trajet, qui reste celui d'avant.
    }

    fun removeStep(index: Int) {
        // Jamais moins de deux lignes : un trajet a un depart et une arrivee, meme vierges.
        if (index !in steps.indices || steps.size <= 2) return
        val hadTarget = steps[index].target != null
        steps.removeAt(index)
        if (hadTarget) invalidate()
    }

    /** Deplace l'etape [index] de [delta] rangs, en restant dans la liste. */
    fun moveStep(index: Int, delta: Int) {
        val to = index + delta
        if (index !in steps.indices || to !in steps.indices) return
        val s = steps.removeAt(index)
        steps.add(to, s)
        // Ne recalcule que si l'ordre des points POSES change : intervertir deux lignes vierges, ou une
        // vierge et une posee, laisse le trajet exactement tel qu'il etait.
        if (s.target != null || steps.getOrNull(index)?.target != null) invalidate()
    }

    fun choose(step: PlannerStep, target: StepTarget) {
        step.target = target
        step.untouched = false
        step.results = emptyList()
        step.searching = false
        step.failed = false
        step.query = ""
        invalidate()
    }

    /**
     * Les trois gestes qui construisent un trajet depuis un point deja affiche sur la carte - un point
     * d'interet, un resultat de recherche, une epingle posee.
     *
     * Ils vivent ici et non dans l'ecran qui les propose : il y en aura plusieurs a les offrir, et trois
     * copies de la meme regle finiraient par diverger. Ils ne font que REMPLIR le planificateur ; c'est
     * a l'appelant de l'ouvrir, parce que lui seul sait ce qu'il doit fermer pour laisser la place.
     *
     * @param completeFromCurrentPosition l'AUTRE bout du trajet prend la position du porteur, s'il est
     *   encore vierge. Passe vrai quand le capteur peut la rendre : designer un camping comme arrivee,
     *   c'est demander a s'y rendre, et cela part d'ou l'on se tient - laisser le depart vide obligeait a
     *   deplier la bande et a le remplir a la main pour obtenir le trajet qu'on venait de demander.
     */
    fun setStart(place: GeocodePlace, completeFromCurrentPosition: Boolean = false) {
        choose(steps.first(), StepTarget.Place(place))
        if (completeFromCurrentPosition) useCurrentPosition(steps.last())
    }

    fun setEnd(place: GeocodePlace, completeFromCurrentPosition: Boolean = false) {
        choose(steps.last(), StepTarget.Place(place))
        if (completeFromCurrentPosition) useCurrentPosition(steps.first())
    }

    /**
     * "Enregistrer comme trace" : la boite qui demande son nom et son dossier est ouverte.
     *
     * Ici plutot que dans l'ecran : c'est la bande qui porte le bouton, et le parcours qu'elle enregistre
     * est celui qu'elle vient de calculer.
     */
    var importDialog by mutableStateOf(false)

    /**
     * Le planificateur est plein et vient de le refuser : l'ecran le DIT.
     *
     * Pose par [addWaypoint] lui-meme, et non par ses trois appelants : le refus et son annonce sont le
     * meme fait, et les separer laissait a chaque bulle le soin de se souvenir de le dire.
     */
    var full by mutableStateOf(false)

    /**
     * Ajoute [place] comme etape. Faux quand le planificateur est plein (cf. [MaxPlannerSteps]) - le
     * moteur refuse au-dela, et [full] le fait dire plutot que de laisser un tap sans effet.
     *
     * Une ligne vierge existante d'abord : c'est celle que l'utilisateur voit vide devant lui, et la
     * remplir est ce qu'il attend. A defaut, l'etape s'insere AVANT l'arrivee - une etape ajoutee est un
     * point de passage, pas une nouvelle destination.
     */
    fun addWaypoint(place: GeocodePlace): Boolean {
        val vierge = steps.firstOrNull { it.target == null }
        if (vierge != null) { choose(vierge, StepTarget.Place(place)); return true }
        if (!canAddStep) { full = true; return false }
        val etape = PlannerStep(nextId++)
        steps.add(steps.size - 1, etape)
        choose(etape, StepTarget.Place(place))
        return true
    }

    /** Frappe dans un champ : la saisie remplace le lieu qui y etait, le trajet perd donc ce point. */
    fun type(step: PlannerStep, text: String) {
        step.query = text
        step.untouched = false
        if (step.target != null) {
            step.target = null
            invalidate()
        }
    }

    /**
     * Le champ prend le focus : tant qu'il ne porte aucune frappe, les propositions d'un champ vierge -
     * position actuelle et historique - peuvent s'y afficher.
     *
     * Y COMPRIS s'il porte deja un lieu : prendre le focus vide le champ a l'ecran, et l'utilisateur qui
     * le voit vide attend qu'on lui propose de quoi le remplir. Il vient justement de taper dessus pour en
     * changer.
     *
     * Ce qui reste exclu, c'est le champ VIDE APRES UNE FRAPPE : efface caractere par caractere, il n'est
     * pas vierge pour autant, et voir ressurgir la liste sous les doigts au dernier retour arriere serait
     * une surprise (cf. [PlannerStep.untouched], que [type] baisse et que rien ne releve avant le focus
     * suivant).
     */
    fun focus(step: PlannerStep) {
        if (step.query.isEmpty()) step.untouched = true
    }

    /**
     * Le lieu [label] est deja une etape du trajet, [except] mise a part.
     *
     * Sert a ne pas reproposer, dans l'historique d'un champ, un lieu qui est deja ailleurs dans le meme
     * trajet : le choisir donnerait deux etapes au meme endroit, donc un troncon de longueur nulle.
     * Compare sur le libelle, seul identifiant qu'un lieu de geocodeur porte de facon stable.
     */
    fun usesPlace(label: String, except: PlannerStep? = null): Boolean =
        steps.any { it !== except && (it.target as? StepTarget.Place)?.place?.label == label }

    fun clearStep(step: PlannerStep) {
        val had = step.target != null
        step.query = ""
        step.results = emptyList()
        step.searching = false
        step.failed = false
        step.target = null
        step.untouched = true
        if (had) invalidate()
    }

    fun beginRecompute() { recomputing = true }

    fun publish(r: RouteState) {
        route = r
        recomputing = false
        if (r !is RouteState.Done) cursor = null
    }

    /** Tap sur le graphique : [alongM] est une abscisse absolue sur le parcours. */
    fun tapProfile(alongM: Double) { cursor = alongM }

    /** Le parcours affiche ne correspond plus aux etapes : on le retire avant d'en recalculer un. */
    private fun invalidate() {
        // Le parcours affiche reste en place jusqu'a l'arrivee du nouveau (cf. recomputing). Seuls le
        // curseur et le zoom retombent : ils designaient un parcours qui n'aura plus la meme longueur.
        cursor = null
        resetZoom()
        revision++
    }
}
