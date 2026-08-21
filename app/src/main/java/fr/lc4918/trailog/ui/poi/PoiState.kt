package fr.lc4918.trailog.ui.poi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.poi.Poi

/**
 * Ce que la carte affiche des points d'intérêt : la couche est-elle allumée, ce qu'on a chargé, et pour
 * quelle emprise.
 *
 * Un état à part plutôt que des variables dans l'écran : le chargement se déclenche sur un geste de carte,
 * s'annule quand la couche s'éteint, et se souvient de ce qu'il a déjà demandé. Trois choses qui se lisent
 * mal éparpillées dans une fonction de mille lignes.
 */
class PoiState {
    /** La couche est allumée. Éteinte, elle vide aussi la liste : garder des marqueurs invisibles en
     *  mémoire n'apporte rien, et les rallumer coûte une requête que le geste vient de justifier. */
    var visible by mutableStateOf(false)
        private set

    var pois by mutableStateOf<List<Poi>>(emptyList())
        private set

    /** Une requête est en cours : de quoi montrer une attente discrète, jamais bloquer la carte. */
    var loading by mutableStateOf(false)
        private set

    /** Le point d'intérêt dont l'infobulle est ouverte. */
    var selected by mutableStateOf<Poi?>(null)
        private set

    /** Emprise du dernier chargement réussi, pour ne pas redemander la même chose au moindre frémissement. */
    private var loaded: Bbox? = null
    /** Filtres de ce chargement : les changer invalide tout, la réponse ne portait pas les mêmes lieux. */
    private var loadedFilters: PoiFilters? = null

    /** La carte est trop dézoomée pour charger quoi que ce soit (cf. [PoiLoading.MIN_ZOOM]) : le dire,
     *  plutôt que de laisser une carte vide sans explication. */
    var tooFar by mutableStateOf(false)
        private set

    /**
     * Le zoom est redevenu suffisant : le message se lève **tout de suite**, sans attendre les points.
     *
     * Il ne se levait qu'à la publication du chargement, soit une demi-seconde d'attente plus une requête
     * réseau plus tard. Pendant tout ce temps, l'écran continuait de réclamer un zoom qu'on venait de faire :
     * on zoomait encore, et encore, croyant n'être jamais assez près.
     */
    fun nearEnough() { tooFar = false }

    fun toggle() {
        visible = !visible
        if (!visible) {
            pois = emptyList(); selected = null; loaded = null; loadedFilters = null
            tooFar = false; needsNetwork = false; partial = false
        }
    }

    fun hide() {
        visible = false
        pois = emptyList()
        selected = null
        loaded = null
        loadedFilters = null
        tooFar = false
        needsNetwork = false
        partial = false
    }

    fun select(poi: Poi?) { selected = poi }
    fun selectById(uuid: String) { selected = pois.firstOrNull { it.uuid == uuid } }

    fun beginLoad() { loading = true }

    /** Ces points d'intérêt viennent du cache et non du service : rien n'a répondu, on montre le dernier
     *  état connu. L'écran le dit, pour qu'une liste incomplète ne passe pas pour une réponse fraîche. */
    var fromCache by mutableStateOf(false)
        private set

    /**
     * Rien à montrer, et le réseau manque : la zone n'a peut-être pas de point d'intérêt, mais on n'en
     * sait rien - le service n'a pas répondu et le cache ne connaît pas cet endroit. Le dire, plutôt que
     * de laisser croire à une région sans un seul café.
     */
    var needsNetwork by mutableStateOf(false)
        private set

    /**
     * Une reponse de plus.
     *
     * **N'eteint pas l'attente** : les deux sources publient chacune a son arrivee (cf. `poiStream`), et
     * couper le rond au premier arrive laisserait croire que tout est la alors que la seconde travaille
     * encore. C'est [endLoad], appele quand le flux se termine, qui l'eteint.
     */
    /**
     * Une source connaît **plus de lieux qu'elle n'en rend** : ce qu'on affiche n'est qu'une partie, et le
     * reste est écarté dans un ordre que rien ne fixe.
     *
     * Le taire donnait une carte qui avait l'air juste et dont les marqueurs changeaient d'un déplacement au
     * suivant - un loueur de canoës visible, puis absent, sans explication. C'est le seul des quatre messages
     * qui parle de ce qu'on ne voit PAS.
     */
    var partial by mutableStateOf(false)
        private set

    fun publish(
        box: Bbox, filters: PoiFilters, list: List<Poi>,
        cache: Boolean = false, offline: Boolean = false, incomplete: Boolean = false,
    ) {
        pois = list
        /*
         * Une emprise incompletement rendue n'est PAS retenue comme chargee.
         *
         * Sans cela, la troncature se figeait : la vue suivante etant contenue dans celle-ci, [needsLoad]
         * repondait non, et zoomer sur un coin d'une zone a trois mille lieux n'en ramenait jamais un seul de
         * plus - on restait avec les 250 tires au hasard de la vue large, dont une poignee seulement tombe
         * dans le nouveau cadre. Or c'est exactement le geste qui doit marcher : plus on serre, moins il y a
         * de lieux, et plus la reponse a de chances d'etre complete.
         *
         * Le prix est une requete a chaque geste tant qu'on reste dans une zone trop dense - c'est le prix
         * juste : on sait qu'on ne montre pas tout.
         */
        loaded = if (incomplete) null else box
        loadedFilters = filters
        tooFar = false
        fromCache = cache
        needsNetwork = offline
        partial = incomplete
    }

    fun tooFar() { tooFar = true; loading = false; needsNetwork = false; partial = false }

    /**
     * Fin d'un chargement qui ne publie rien : vue déjà chargée, abandon, ou geste suivant qui annule
     * celui-ci.
     *
     * Appelé sur **tous** les chemins de sortie, faute de quoi l'attente reste allumée pour toujours - et
     * le bouton tourne indéfiniment sur une requête qui n'existe plus.
     */
    fun endLoad() { loading = false }

    /**
     * Faut-il redemander pour [box] ?
     *
     * Non si la zone déjà chargée la contient : on vient de déplacer la carte de trois pixels, et les
     * points d'intérêt ne bougent pas. C'est ce qui tient les requêtes loin du quota du service, bien plus
     * que le délai d'attente qui précède l'appel.
     *
     * La comparaison se fait sur l'emprise **élargie** au moment du chargement (cf. `PoiLoading.grow`) :
     * charger un peu plus large que l'écran permet justement à un petit déplacement de ne rien coûter.
     */
    fun needsLoad(box: Bbox, filters: PoiFilters): Boolean {
        if (loadedFilters != filters) return true
        val d = loaded ?: return true
        return !(box.west >= d.west && box.east <= d.east && box.south >= d.south && box.north <= d.north)
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
