package fr.lc4918.trailog.ui.poi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.poi.Poi

/**
 * Ce que la carte affiche des points d'intérêt : la couche est-elle allumée, et ce qu'elle montre.
 *
 * Un état à part plutôt que des variables dans l'écran. Il ne charge rien lui-même : c'est le chargeur
 * qui sait ce qu'on a et ce qu'on attend (cf. `PoiLoader`), et cet état n'en garde que ce que l'écran
 * montre.
 */
class PoiState {
    /**
     * La couche est allumée. Éteinte, elle vide aussi la liste affichée ; ce qui a été chargé reste au
     * chargeur et en base, et la rallumer ne coûte donc aucune requête.
     *
     * **Ce n'est plus un interrupteur mais une CONSÉQUENCE** : la couche est allumée quand le filtre
     * retient au moins une catégorie, et éteinte quand il n'en retient aucune (cf. [showLayer], et
     * `PoiFilters`). Le bouton de la carte n'allume donc plus rien - il ouvre la bulle où l'on choisit.
     *
     * Deux commandes pour un seul comportement finissaient par se contredire : on pouvait avoir la couche
     * allumée et toutes les catégories décochées, c'est-à-dire une carte vide qu'aucun réglage
     * n'expliquait.
     */
    var visible by mutableStateOf(false)
        private set

    /**
     * La bulle des catégories est ouverte (cf. `PoiFilterBubble`).
     *
     * Dans l'état de l'écran, qui vit dans un `ViewModel` : une rotation ne doit pas la refermer, et le
     * choix d'onglet qu'on venait de faire s'en irait avec elle.
     */
    var bubbleOpen by mutableStateOf(false)
        private set

    fun toggleBubble() { bubbleOpen = !bubbleOpen }

    fun closeBubble() { bubbleOpen = false }

    /**
     * La couche est MISE DE COTE : ses marqueurs quittent la carte, son filtre reste entier.
     *
     * **Ce n'est pas la meme chose que de tout decocher.** Eteindre par le filtre - la poubelle de la bulle
     * - est un geste destructeur : il faut recocher ses categories une a une pour revoir ce qu'on avait
     * choisi. Or ce qu'on veut le plus souvent est bien plus simple : degager la carte un instant, parce
     * qu'on regarde le relief sous les epingles, puis les remettre. Le testeur l'a dit ainsi : "on ne peut
     * plus masquer les points d'interet".
     *
     * Rien n'est charge tant que la couche est de cote, et rien n'est OUBLIE non plus : les lieux deja
     * recus restent en memoire, et les remontrer ne coute donc pas une requete.
     *
     * Volontairement NON enregistre : c'est un geste de l'instant, pas un reglage. Il traverse une rotation
     * - l'etat vit dans un `ViewModel` - et ne survit pas au redemarrage, ou l'on retrouve la couche telle
     * qu'on l'avait choisie.
     */
    var masked by mutableStateOf(false)
        private set

    /** Les marqueurs sont-ils reellement poses sur la carte. */
    val showingMarkers: Boolean get() = visible && !masked

    fun toggleMask() {
        masked = !masked
        // L'infobulle decrirait un marqueur qui n'est plus la : elle se ferme avec la couche.
        if (masked) selected = null
    }

    var pois by mutableStateOf<List<Poi>>(emptyList())
        private set

    /** Des requêtes sont en cours : de quoi montrer une attente discrète, jamais bloquer la carte. */
    var loading by mutableStateOf(false)
        private set

    /** Le point d'intérêt dont l'infobulle est ouverte. */
    var selected by mutableStateOf<Poi?>(null)
        private set

    /**
     * La carte est trop dézoomée pour charger quoi que ce soit (cf. [PoiLoading.MIN_ZOOM]), et il reste
     * quelque chose à en dire : la couche vient d'être allumée sur une vue trop large, et rien n'apparaît.
     *
     * **Une seule fois, à l'allumage**, et non chaque fois que la vue redevient large : le message
     * resurgissait sinon à chaque dézoom - au moment précis où l'on prend du recul pour se situer. Il
     * devenait un décor, et un décor ne se lit plus (cf. [armed]).
     */
    var tooFar by mutableStateOf(false)
        private set

    /**
     * L'avertissement de zoom est-il encore DÛ. Armé quand la couche s'allume, désarmé dès qu'on a zoomé
     * assez : la question posée a trouvé sa réponse, et elle ne se repose pas au dézoom suivant.
     */
    private var armed = false

    /**
     * Le zoom est redevenu suffisant : le message se lève **tout de suite**, sans attendre les points -
     * sans quoi l'écran continuait de réclamer un zoom qu'on venait de faire.
     */
    fun nearEnough() { tooFar = false; armed = false }

    /**
     * La couche suit le filtre : allumée dès qu'une catégorie est retenue, éteinte quand il n'en reste
     * aucune. Appelée par l'écran à chaque changement de filtre, et à l'ouverture.
     */
    fun showLayer(on: Boolean) {
        if (on == visible) return
        visible = on
        // Allumer la couche ARME l'avertissement de zoom : c'est le seul moment ou il a lieu d'etre dit.
        // Et leve la mise de cote : cocher une categorie est une demande de VOIR, et la laisser sans effet
        // derriere un oeil ferme qu'on a oublie serait une carte vide qu'aucun reglage n'explique.
        if (on) { armed = true; masked = false } else clear()
    }

    fun hide() {
        visible = false
        masked = false
        clear()
    }

    private fun clear() {
        armed = false
        pois = emptyList(); selected = null; loading = false
        tooFar = false; fromCache = false; needsNetwork = false; partial = false; awayFromTracks = false
    }

    fun select(poi: Poi?) { selected = poi }
    fun selectById(uuid: String) { selected = pois.firstOrNull { it.uuid == uuid } }

    /** Une cellule au moins n'a pas pu être chargée, et l'on montre à sa place ce que le cache en gardait.
     *  L'écran le dit, pour qu'une liste ancienne ne passe pas pour une réponse fraîche. */
    var fromCache by mutableStateOf(false)
        private set

    /**
     * Une cellule au moins n'a pas pu être chargée, et le cache n'en savait rien : la zone n'a peut-être
     * pas de point d'intérêt, mais on n'en sait rien. Le dire, plutôt que de laisser croire à une région
     * sans un seul café.
     */
    var needsNetwork by mutableStateOf(false)
        private set

    /**
     * La vue porte plus de cellules qu'on n'en charge pour un écran (cf. [PoiLoading.MAX_CELLS]) : seules
     * les plus proches du centre sont demandées, et la carte le dit plutôt que de laisser croire qu'elle
     * montre tout.
     */
    var partial by mutableStateOf(false)
        private set

    /**
     * La vue est trop loin de toute trace affichee pour que le couloir laisse passer quoi que ce soit
     * (cf. `SettingsEntity.poiTrackCorridorM`). Rien n'est demande aux services dans ce cas, mais la carte
     * le DIT : une couche allumee qui ne montre rien doit s'expliquer, sans quoi elle se lit comme une
     * panne.
     */
    var awayFromTracks by mutableStateOf(false)
        private set

    /**
     * Ce que le chargeur sait a cet instant (cf. `PoiLoader.Snapshot`).
     *
     * Les lieux arrivent cellule par cellule, et chaque arrivee remplace la liste entiere : c'est le
     * chargeur qui accumule, l'ecran ne fait que montrer.
     */
    fun show(list: List<Poi>, pending: Boolean, cache: Boolean, missing: Boolean) {
        pois = list
        loading = pending
        fromCache = cache
        needsNetwork = missing
        dropSelectionIfGone()
    }

    /** Ce que la derniere vue demande : les cellules qu'on n'a pas voulu charger, et le couloir. */
    fun viewed(capped: Boolean, away: Boolean) {
        partial = capped
        awayFromTracks = away
    }

    /**
     * La vue est trop large pour charger quoi que ce soit.
     *
     * Le message ne se leve que si l'avertissement est encore [armed] - la couche vient d'etre allumee et
     * l'on n'a pas encore zoome. Ce qui est deja affiche reste en place : dezoomer pour se situer ne doit
     * pas vider la carte de ce qu'on regardait.
     */
    fun tooFar() {
        tooFar = armed
        partial = false
        awayFromTracks = false
    }

    /** Une categorie qu'on vient de decocher emporte l'infobulle ouverte sur l'un de ses lieux : le
     *  marqueur qu'elle decrit quitte la carte a l'instant meme (cf. le filtre local de `PoiEffects`). */
    fun dropSelectionIfHidden(filters: PoiFilters) {
        val s = selected ?: return
        if (!filters.isShown(s.category)) selected = null
    }

    /** Un point d'intérêt disparu du dernier chargement ne doit pas laisser son infobulle ouverte sur la
     *  carte : elle décrirait un marqueur qui n'y est plus. */
    fun dropSelectionIfGone() {
        val s = selected ?: return
        if (pois.none { it.uuid == s.uuid }) selected = null
    }
}

/** Couleur d'un groupe sur la carte. Quatre teintes franches, lisibles sur un fond topographique clair
 *  comme sur une orthophoto - c'est le groupe qui se lit d'un coup d'oeil, la catégorie s'annonce dans
 *  l'infobulle. */
fun poiGroupColor(group: PoiGroup): String = when (group) {
    PoiGroup.LODGING -> "#7B4FB5"     // violet : dormir
    PoiGroup.FOOD -> "#D2691E"        // orange brûlé : manger
    PoiGroup.LEISURE -> "#2E9B57"     // vert : voir et faire
    PoiGroup.PRACTICAL -> "#1F6FB2"   // bleu : services, la couleur des commandes de l'application
}
