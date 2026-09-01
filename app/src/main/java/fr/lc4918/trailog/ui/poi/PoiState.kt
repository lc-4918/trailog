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
    /**
     * La couche est allumée. Éteinte, elle vide aussi la liste : garder des marqueurs invisibles en
     * mémoire n'apporte rien, et les rallumer coûte une requête que le geste vient de justifier.
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
     * recus restent en memoire, et les redemander ne coute donc pas une requete (cf. [visible] et [hide],
     * qui vident tout parce qu'ils repondent, eux, a un vrai changement de filtre).
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

    /**
     * Le réglage "Compléter avec OpenStreetMap" au moment de ce chargement.
     *
     * Il invalide autant que les filtres, et l'oubli se voyait : l'allumer ne changeait rien à l'écran
     * tant qu'on ne déplaçait pas la carte, la vue étant déjà tenue pour chargée. On restait devant une
     * carte inchangée en croyant le réglage sans effet.
     */
    private var loadedOsm: Boolean? = null

    /** Emprise du dernier chargement qui a **echoue**, et l'instant de cet echec : on ne la redemande pas
     *  tout de suite (cf. [PoiLoading.RETRY_AFTER_FAIL_MS]). */
    private var failedBox: Bbox? = null
    private var failedAt = 0L

    /**
     * La carte est trop dézoomée pour charger quoi que ce soit (cf. [PoiLoading.MIN_ZOOM]), et il reste
     * quelque chose à en dire : la couche vient d'être allumée sur une vue trop large, et rien n'apparaît.
     *
     * **Une seule fois, à l'allumage**, et non chaque fois que la vue redevient large. Le message répondait
     * autrefois à la seule question du zoom, si bien qu'il resurgissait à chaque dézoom - au moment précis
     * où l'on prend du recul pour se situer, c'est-à-dire quand on ne cherche justement pas de point
     * d'intérêt. Il devenait un décor, et un décor ne se lit plus.
     *
     * Ce qu'il dit, il ne le dit donc qu'à celui qui vient de demander la couche et à qui la carte ne
     * répond rien (cf. [armed]).
     */
    var tooFar by mutableStateOf(false)
        private set

    /**
     * L'avertissement de zoom est-il encore DÛ.
     *
     * Armé quand la couche s'allume - la première catégorie retenue -, désarmé dès qu'on a zoomé assez :
     * la question posée a trouvé sa réponse, et elle ne se repose pas au dézoom suivant.
     */
    private var armed = false

    /**
     * Le zoom est redevenu suffisant : le message se lève **tout de suite**, sans attendre les points.
     *
     * Il ne se levait qu'à la publication du chargement, soit une demi-seconde d'attente plus une requête
     * réseau plus tard. Pendant tout ce temps, l'écran continuait de réclamer un zoom qu'on venait de faire :
     * on zoomait encore, et encore, croyant n'être jamais assez près.
     *
     * Il désarme au passage : c'est ici que l'avertissement a rempli son office.
     */
    fun nearEnough() { tooFar = false; armed = false }

    /**
     * La couche suit le filtre : allumée dès qu'une catégorie est retenue, éteinte quand il n'en reste
     * aucune. Appelée par l'écran à chaque changement de filtre, et à l'ouverture.
     *
     * L'extinction vide tout, comme avant : des marqueurs qu'on ne montre plus n'ont pas à occuper la
     * mémoire, et les remontrer coûte une requête que le geste vient de justifier.
     */
    fun showLayer(on: Boolean) {
        if (on == visible) return
        visible = on
        // Allumer la couche ARME l'avertissement de zoom : c'est le seul moment ou il a lieu d'etre dit.
        // Et leve la mise de cote : cocher une categorie est une demande de VOIR, et la laisser sans effet
        // derriere un oeil ferme qu'on a oublie serait la meme carte vide qu'aucun reglage n'explique.
        if (on) { armed = true; masked = false }
        if (!on) {
            armed = false
            pois = emptyList(); selected = null; loaded = null; loadedFilters = null; loadedOsm = null
            loadedComplete = false; loadedAt = 0L
            tooFar = false; needsNetwork = false; partial = false; awayFromTracks = false
            failedBox = null
        }
    }

    fun hide() {
        visible = false
        armed = false
        masked = false
        pois = emptyList()
        selected = null
        loaded = null
        loadedFilters = null
        loadedOsm = null
        loadedComplete = false
        loadedAt = 0L
        tooFar = false
        needsNetwork = false
        partial = false
        awayFromTracks = false
        failedBox = null
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

    /**
     * La vue est trop loin de toute trace affichee pour que le couloir laisse passer quoi que ce soit
     * (cf. `SettingsEntity.poiTrackCorridorM`).
     *
     * Rien n'est demande aux services dans ce cas - c'est tout l'objet - mais la carte le DIT : une couche
     * allumee qui ne montre rien doit s'expliquer, sans quoi elle se lit comme une panne. Le message se
     * retire de lui-meme comme les autres constats (cf. `PoiStatusBanner`).
     */
    var awayFromTracks by mutableStateOf(false)
        private set

    /**
     * Rien a demander : aucune trace affichee ne passe assez pres de cette vue.
     *
     * L'emprise est retenue comme CHARGEE, et c'est voulu : il n'y a rien a redemander tant qu'on ne
     * bouge pas, et un chargement par geste de carte serait exactement ce qu'on cherche a eviter.
     */
    fun awayFromTracks(box: Bbox, filters: PoiFilters, osm: Boolean, now: Long) {
        pois = emptyList()
        selected = null
        loaded = box
        loadedComplete = true
        loadedAt = now
        loadedFilters = filters
        loadedOsm = osm
        failedBox = null
        tooFar = false
        fromCache = false
        needsNetwork = false
        partial = false
        awayFromTracks = true
        loading = false
    }

    /** L'emprise retenue l'a-t-elle ete sur un chargement COMPLET. Sinon elle est tronquee, et se
     *  redemande des qu'on resserre ou que le temps passe (cf. [needsLoad]). */
    private var loadedComplete = false

    /** Instant du dernier chargement retenu, en temps depuis le demarrage de l'appareil. */
    private var loadedAt = 0L

    fun publish(
        box: Bbox, filters: PoiFilters, list: List<Poi>, osmComplement: Boolean = true,
        cache: Boolean = false, offline: Boolean = false, incomplete: Boolean = false,
        /** Sans valeur par defaut, et volontairement : c'est l'oubli de cette question qui a fait
         *  disparaitre les restaurants d'Albi. Chaque appelant doit dire s'il a tout recu. */
        complete: Boolean,
        /** Temps depuis le demarrage de l'appareil : sert au frein d'une emprise tronquee. */
        now: Long = 0L,
    ) {
        pois = list
        /*
         * Une emprise n'est retenue comme chargee que si le chargement est alle a son terme, toutes sources
         * arrivees et aucune tronquee.
         *
         * Deux fautes distinctes s'y cachaient, et la seconde est celle qu'on a vue sur Albi.
         *
         * La troncature d'abord : sans ce garde-fou elle se figeait, la vue suivante etant contenue dans
         * celle-ci, et zoomer sur un coin d'une zone a trois mille lieux n'en ramenait jamais un seul de
         * plus - on restait avec les 250 tires au hasard de la vue large. Or c'est exactement le geste qui
         * doit marcher : plus on serre, moins il y a de lieux, et plus la reponse a de chances d'etre
         * complete.
         *
         * L'interruption ensuite. Les sources publient chacune a son arrivee, et la premiere suffisait a
         * marquer l'emprise chargee. DATAtourisme repond en une seconde, Overpass en met trois a trente : un
         * geste de carte de plus annulait le chargement entre les deux, et la vue suivante ne redemandait
         * plus rien. La carte restait sur les seuls lieux de la source rapide, definitivement. Un dezoom
         * suivi d'un zoom sur Albi faisait ainsi disparaitre les cent cinquante restaurants sans retour.
         *
         * Le prix est une requete a chaque geste tant qu'un chargement n'aboutit pas - c'est le prix juste :
         * on sait qu'on ne montre pas tout.
         */
        /*
         * L'emprise est retenue MEME TRONQUEE, et c'est la correction du defaut le plus couteux.
         *
         * Elle ne l'etait qu'a la condition d'un chargement complet, ce qui est parfaitement raisonnable
         * pour une reponse interrompue - on la redemandera - mais ruineux pour une reponse TRONQUEE : dans
         * une ville dense, la reponse l'est toujours, l'emprise n'etait donc jamais retenue, et chaque
         * immobilisation de la carte relancait le cycle entier. Mesure sur Toulouse : une vingtaine de
         * secondes par geste, et de quoi se faire refuser par le service en quelques minutes.
         *
         * Elle est donc retenue, avec ce qu'il faut pour ne pas s'en contenter indefiniment (cf.
         * [needsLoad]) : resserrer nettement la vue redemande, et le temps qui passe aussi.
         *
         * Une emprise INTERROMPUE, elle, n'est toujours pas retenue : une source qui n'a pas repondu ne
         * dit rien de ce qu'elle contient, et ce qui manque doit pouvoir revenir au geste suivant.
         */
        loaded = if (complete || incomplete) box else null
        loadedComplete = complete
        loadedAt = now
        loadedFilters = filters
        loadedOsm = osmComplement
        tooFar = false
        fromCache = cache
        needsNetwork = offline
        partial = incomplete
        awayFromTracks = false
    }

    /**
     * Ce chargement n'a pas abouti : une source n'a pas repondu.
     *
     * On retient l'emprise et l'instant, pour laisser au service le temps de se remettre avant de le
     * redemander. Sans ce frein, l'echec se nourrissait de lui-meme : chaque geste relancait la requete que
     * le service venait de refuser, et il la refusait d'autant plus fort.
     *
     * [now] vient de l'appelant, en temps depuis le demarrage de l'appareil : un changement d'heure - fuseau,
     * mise a l'heure du reseau - ne doit ni prolonger l'attente indefiniment ni l'annuler d'un coup.
     */
    fun loadFailed(box: Bbox, now: Long) { failedBox = box; failedAt = now }

    /**
     * Le reseau est revenu : ce qu'on montre a ete pris sur le cache, ou n'a pas pu etre pris du tout.
     *
     * **Sans cela, rien ne relevait le bandeau "Derniers points connus (hors ligne)".** Il disait vrai a
     * l'instant ou il s'est pose, et le restait indefiniment : une emprise chargee ne se redemande pas, une
     * emprise en echec attend sa minute, et l'un comme l'autre n'est reexamine qu'a l'ARRET DE LA CAMERA -
     * un geste que personne ne fait en repassant sous couverture. Le testeur l'a vu ainsi : "j'ai le message
     * qui reste affiche au milieu, meme si je repasse en ligne".
     *
     * Oublie donc les deux memoires qui retiendraient la requete, et rien d'autre : les lieux affiches
     * restent en place jusqu'a l'arrivee des nouveaux, comme apres n'importe quel changement de filtre.
     *
     * **Ne fait rien apres un chargement REUSSI** : revenir en ligne apres une sortie ne doit pas relancer
     * une requete pour retrouver ce qu'on a deja.
     */
    fun retryAfterReconnect() {
        if (!fromCache && !needsNetwork && failedBox == null) return
        loaded = null
        loadedComplete = false
        failedBox = null
    }

    /**
     * La vue est trop large pour charger quoi que ce soit.
     *
     * Le message ne se leve que si l'avertissement est encore [armed] - la couche vient d'etre allumee et
     * l'on n'a pas encore zoome. Les trois autres constats retombent dans tous les cas : ils parlaient
     * d'un chargement qui n'aura pas lieu.
     */
    fun tooFar() {
        tooFar = armed
        loading = false
        needsNetwork = false
        partial = false
    }


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
    fun needsLoad(box: Bbox, filters: PoiFilters, osm: Boolean, now: Long): Boolean {
        if (loadedFilters != filters || loadedOsm != osm) return true
        // Une zone qui vient d'echouer attend son tour : le service n'aura pas change d'avis en trois
        // secondes, et insister est ce qui le fait refuser plus durement (cf. RETRY_AFTER_FAIL_MS).
        val e = failedBox
        if (e != null && now - failedAt < PoiLoading.RETRY_AFTER_FAIL_MS && contient(e, box)) return false
        val d = loaded ?: return true
        if (!contient(d, box)) return true
        if (loadedComplete) return false
        /*
         * L'emprise retenue etait TRONQUEE : on ne s'en contente pas indefiniment, mais on ne la redemande
         * pas non plus a chaque frisson de la carte.
         *
         * Deux portes de sortie, et deux seulement :
         * - **resserrer nettement** (cf. [PoiLoading.ZOOM_RETRY_RATIO]) : c'est le geste qui rend la
         *   reponse complete, moins de lieux tenant dans une vue plus etroite, et le seul qui ait une
         *   chance d'apporter du neuf. Un simple deplacement dans la zone deja chargee n'en a aucune ;
         * - **le temps** (cf. [PoiLoading.PARTIAL_TTL_MS]) : au bout d'un moment, redemander vaut la
         *   peine, ne serait-ce que parce que le decoupage peut mieux tomber.
         */
        if (aire(box) < aire(d) * PoiLoading.ZOOM_RETRY_RATIO) return true
        return now - loadedAt >= PoiLoading.PARTIAL_TTL_MS
    }

    /** Surface approchee d'une emprise, en degres carres : elle ne sert qu'a comparer deux emprises entre
     *  elles, et l'echelle exacte n'a donc aucune importance. */
    private fun aire(b: Bbox): Double = (b.east - b.west) * (b.north - b.south)

    /** [dehors] contient-elle entierement [dedans]. */
    private fun contient(dehors: Bbox, dedans: Bbox): Boolean =
        dedans.west >= dehors.west && dedans.east <= dehors.east &&
            dedans.south >= dehors.south && dedans.north <= dehors.north

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
