package fr.lc4918.trailog.ui.routes

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.NetworkStatus
import fr.lc4918.trailog.net.ServiceUrl
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.ui.poi.PoiFilterBubbleAnchored
import fr.lc4918.trailog.ui.poi.PoiState
import kotlin.math.hypot
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.edit.TrackEditState
import fr.lc4918.trailog.ui.geocode.GeocodeSearchBar
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.location.LocationControls
import fr.lc4918.trailog.ui.measure.TrackMeasureState
import fr.lc4918.trailog.ui.offline.OfflineMinimizedButton
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import kotlin.math.roundToInt

/**
 * Les deux barres de boutons du haut de la carte, et ce qu'elles ouvrent.
 *
 * **Pourquoi deux composables et non un.** Les deux coins ne parlent pas de la meme chose. Celui de
 * gauche porte les FONCTIONS - le menu, la position, la recherche, la mesure, la retouche : de quoi
 * ouvrir un mode qui va occuper l'ecran. Celui de droite porte ce qui regarde la CARTE elle-meme -
 * remettre le nord en haut, lire la legende du fond, changer de fond. Les separer leur donne a chacun la
 * demi-douzaine de parametres qui les concerne, la ou un composable unique en aurait pris dix-sept.
 */

/**
 * Ce que les commandes du haut ouvrent : la legende d'un fond, et le gestionnaire de couches.
 *
 * Deux panneaux qui ne s'ouvrent que d'ici, et dont rien d'autre dans l'ecran ne decide. [legendAnchor]
 * est le coin haut-gauche du bouton "info", releve a la pose : la legende s'y adosse sans avoir a
 * supposer une largeur de barre, qui varie avec les boutons affiches.
 */
@Stable
class MapChromeState {
    var basemapControlOpen by mutableStateOf(false)
    var legendOpen by mutableStateOf(false)
    var legendAnchor by mutableStateOf(IntOffset.Zero)
}

/**
 * La colonne de gauche : menu, position, recherche de lieu, mesure, retouche.
 *
 * @param burgerVisible le bouton de menu s'affiche : faux quand le menu ne s'ouvre qu'au balayage du bord.
 */
@Composable
internal fun BoxScope.MapTopLeftControls(
    settings: SettingsEntity,
    chrome: MapChrome,
    insets: MapInsetsState,
    controller: MapController,
    location: LocationControls,
    geo: GeocodeSearchState,
    measure: TrackMeasureState,
    planner: RoutePlannerState,
    edit: TrackEditState,
    vm: MainViewModel,
    offlineDownload: OfflineDownloadState?,
    burgerVisible: Boolean,
    onMenu: () -> Unit,
    onGeocodeTap: () -> Unit,
) {
    // Colonne des boutons du coin haut-gauche : la barre habituelle, puis le bouton de recherche
    // de lieu juste dessous, à l'écart vertical qui sépare déjà le burger du bouton GPS.
    //
    // Les icones sont toutes AU TRAIT. Ce n'est pas une preference de style : le menu, la
    // loupe et la regle le sont deja dans le jeu plein de Material - ce sont des dessins
    // lineaires par nature -, tandis que l'epingle, le marteau et le panneau de direction y
    // sont des aplats. Melangees, trois taches pleines cotoyaient quatre traits dans la meme
    // colonne. Leurs variantes "Outlined" remettent tout le monde au meme poids.
    Column(
        Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
            .onGloballyPositioned { insets.topControlsPx = it.size.height },
        verticalArrangement = Arrangement.spacedBy(MapControlSpacing),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MapControlSpacing)) {
            if (burgerVisible) {
                IconButton(onClick = onMenu, modifier = chrome.buttonBackground) {
                    Icon(Icons.Filled.Menu, stringResource(R.string.action_menu), tint = chrome.fg)
                }
            }
            /*
             * Une lecture nue, sans defaut recopie ici : les reglages ne sont plus nullables
             * (cf. MainViewModel.settings), et une ligne pas encore lue rend les defauts de
             * l'entite - donc "oui" pour ce bouton.
             *
             * Ce chemin-la a coute cher. Les reglages valaient null le temps que Room reponde,
             * et trois lectures traitaient l'inconnu comme un "non" alors que le defaut du
             * reglage est "oui". Un testeur l'a decrit sans le savoir : plus de repere, et "le
             * bouton a disparu". Le processus tue en cours de sortie, l'ecran rallume,
             * l'activite recreee - et la carte ne portait plus QUE le burger. Le type ne peut
             * plus dire "je ne sais pas", et la faute ne peut plus s'ecrire.
             */
            if (settings.showGpsButton) {
                IconButton(
                    onClick = { location.onGpsButtonTap() },
                    // Le fond ne change pas avec l'etat : allume, c'est le DESSIN qui passe au
                    // bleu, comme le bouton de retouche. Le bouton portait auparavant un aplat
                    // bleu plein, seul de son espece dans la colonne - il se lisait comme un
                    // objet d'une autre famille, la ou tous les autres gardent le fond commun
                    // des ornements de carte.
                    modifier = chrome.buttonBackground,
                ) {
                    val gpsTint = if (location.gpsActive) MapChromeActive else chrome.fg
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Place, stringResource(R.string.content_desc_gps_position),
                            modifier = Modifier.size(GpsIconSize), tint = gpsTint)
                        Text(stringResource(R.string.gps_label), fontSize = GpsLabelSp.sp,
                            lineHeight = GpsLabelSp.sp, color = gpsTint)
                    }
                }
            }
            // Popup de progression réduite : bouton orange à droite de l'emplacement du bouton
            // GPS, dans la même barre (donc même espacement latéral de 4.dp).
            offlineDownload?.takeIf { it.minimized }?.let { dl ->
                OfflineMinimizedButton(state = dl, onClick = { vm.setOfflineDownloadMinimized(false) })
            }
        }
        // La recherche de lieu ouvre sa barre de saisie juste dessous : elle reste donc dans la
        // colonne du haut, là où la barre a la place de se déplier.
        if (settings.geocodingEnabled) {
            IconButton(onClick = onGeocodeTap, modifier = chrome.buttonBackground) {
                // Loupe posée sur un globe, et non la cible de visée d'avant : celle-ci disait
                // "se repérer", quand ce bouton cherche un lieu par son nom. Le globe distingue
                // au passage cette recherche-là d'une recherche de texte dans l'application.
                Icon(Icons.Filled.Search, stringResource(R.string.content_desc_geocode_search), tint = chrome.fg)
            }
        }
        // Sous la recherche, et à sa place quand elle est masquée : la colonne se resserre
        // d'elle-même, aucun des deux boutons ne réserve son rang.
        // Masqué pendant le choix des points, que sa bande porte déjà entièrement.
        if (settings.trackMeasureEnabled && !measure.picking) {
            IconButton(onClick = {
                // Le bas de l'écran revient à la bande de consigne : le profil se ferme, le
                // planificateur se replie dans son coin (son trajet, lui, est conservé).
                vm.closeProfile()
                if (planner.open) planner.collapse(true)
                measure.open()
            }, modifier = chrome.buttonBackground) {
                Icon(Icons.Filled.Straighten, stringResource(R.string.measure_title), tint = chrome.fg)
            }
        }
        // Retouche des traces : un bouton, et non une barre permanente. Ouvrir le mode est un
        // geste conscient - il detourne les taps de la carte, qui n'ouvrent plus de profil tant
        // qu'un outil attend son point. Dans la colonne de gauche, sous le burger, avec les
        // deux autres fonctions qui s'ouvrent en mode.
        if (settings.trackEditEnabled) {
            IconButton(onClick = { edit.toggleBar() }, modifier = chrome.buttonBackground) {
                Icon(
                    // Un crayon plutot qu'un marteau : deux traits contre une silhouette
                    // pleine d'outils croises, illisible a 22 dp au-dessus d'une carte.
                    Icons.Outlined.Edit, stringResource(R.string.edit_toolbar),
                    tint = if (edit.open) MapChromeActive else chrome.fg,
                )
            }
        }
        if (geo.searchOpen) {
            GeocodeSearchBar(
                query = geo.query, results = geo.results, searching = geo.searching,
                onQueryChange = { geo.query = it },
                onPick = { place ->
                    geo.select(place)
                    // Un lieu cherche par son nom alimente l'historique du planificateur : le
                    // chercher est deja dire qu'il nous interesse, et le retaper demain dans un
                    // champ d'etape serait refaire le meme travail (cf. PlannerHistory).
                    vm.rememberPlannerPlace(place)
                    controller.centerOnAtLeast(place.lat, place.lon, GeocodeMinZoom)
                },
                onClose = { geo.closeSearch() },
            )
        }
    }
}

/**
 * La rangee de droite : remise du nord en haut, legende du fond, gestionnaire de couches.
 *
 * @param bearing l'orientation de la carte en degres ; le bouton du nord n'apparait qu'une fois tournee.
 */
@Composable
internal fun BoxScope.MapTopRightControls(
    settings: SettingsEntity,
    chrome: MapChrome,
    chromeState: MapChromeState,
    controller: MapController,
    bearing: Double,
    activeLegends: List<String>,
) {
    // réinitialisation de l'orientation (visible seulement si la carte est tournée) + Basemap Control
    Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(MapControlSpacing)) {
        if (kotlin.math.abs(bearing) > 0.5) {
            IconButton(onClick = { controller.resetNorth() }, modifier = chrome.buttonBackground) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.action_reset_north),
                    modifier = Modifier.graphicsLayer { rotationZ = -bearing.toFloat() }, tint = chrome.fg)
            }
        }
        // Légende du fond affiché, s'il en fournit une (cf. ProviderEntity.legendAsset) : à
        // gauche du gestionnaire de couches, l'image se déployant vers la gauche depuis ici.
        // Le bouton reporte sa position : la légende s'y adosse sans supposer de largeur de
        // barre, celle-ci variant selon les boutons affichés (nord, gestionnaire de couches).
        if (activeLegends.isNotEmpty()) {
            IconButton(
                onClick = { chromeState.legendOpen = !chromeState.legendOpen },
                modifier = Modifier.onGloballyPositioned {
                    val p = it.positionInRoot()          // coin haut-gauche du bouton
                    chromeState.legendAnchor = IntOffset(p.x.roundToInt(), p.y.roundToInt())
                },
            ) {
                Icon(Icons.Outlined.Info, stringResource(R.string.content_desc_basemap_legend), tint = chrome.fg)
            }
        }
        if (settings.showBasemapControlButton) {
            IconButton(onClick = { chromeState.basemapControlOpen = true }, modifier = chrome.buttonBackground) {
                // Outlined plutôt que Filled : la version pleine a sa couche du haut remplie
                // en noir, ce qui contraste avec les autres boutons de la carte (tous en contour).
                Icon(Icons.Outlined.Layers, stringResource(R.string.content_desc_basemap_control), tint = chrome.fg)
            }
        }
    }
}

/** Zoom minimal garanti sur le lieu trouvé, un zoom plus serré étant conservé. À 12, la ville et ses
 *  abords tiennent à l'écran : de quoi situer l'épingle, sans plonger sur une adresse à la parcelle. */
internal const val GeocodeMinZoom = 12.0

/**
 * Les commandes du coin bas-droit, a portee du pouce : recentrage, cloche d'alerte, points d'interet,
 * planificateur.
 *
 * **Position FIXE**, a la seule barre de navigation pres : contrairement a l'echelle graphique, elles ne
 * se rangent pas au-dessus de ce qui occupe le bas. Un bouton qui saute de trois cents pixels a
 * l'ouverture d'un panneau se cherche a chaque fois ; il vaut mieux qu'il attende sous lui, la ou la main
 * l'a laisse. C'est la difference entre une lecture, qui doit rester lisible, et une cible, qui doit
 * rester ou on l'a laissee.
 *
 * Posees AVANT ce qui occupe le bas, donc DESSOUS : la bande du planificateur, les consignes de saisie et
 * le profil les recouvrent au lieu de les pousser.
 *
 * @param alerting la trace suivie est perdue de vue : la cloche passe au rouge.
 * @param followedTrack une trace est suivie : la cloche passe au bleu.
 */
@Composable
internal fun BoxScope.MapBottomRightControls(
    settings: SettingsEntity,
    chrome: MapChrome,
    controller: MapController,
    location: LocationControls,
    poi: PoiState,
    planner: RoutePlannerState,
    vm: MainViewModel,
    routingUrl: String,
    alertEnabled: Boolean,
    alerting: Boolean,
    followedTrack: Boolean,
    /** Les categories de points d'interet retenues, et de quoi les changer : la bulle du bouton POI. */
    poiFilters: PoiFilters,
    onPoiFilters: (PoiFilters) -> Unit,
    moveTick: Int,
    idleTick: Int,
    maxWidthPx: Int,
    maxHeightPx: Int,
    onBellTap: () -> Unit,
    onNoConnection: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    // Position hors du centre de la carte : c'est ce qui distingue les deux etats ALLUMES du bouton
    // de suivi - la position est-elle deja revenue sous la croix, ou le silence d'apres-geste
    // court-il encore (cf. MapFollow.tapAction).
    //
    // Mesuré à l'écran plutôt que sur les coordonnées : la question est "la position est-elle
    // au milieu de ce que je vois", et sa réponse doit valoir à tout zoom. Suivie à chaque
    // image du déplacement (moveTick), comme les infobulles.
    val centerTolPx = with(density) { 16.dp.toPx() }
    val viewCenterX = maxWidthPx / 2f
    val viewCenterY = maxHeightPx / 2f
    val positionCentered = remember(location.lastUserLocation, moveTick, idleTick, viewCenterX, viewCenterY) {
        location.lastUserLocation?.let { (la, lo) -> controller.screenOf(lo, la) }?.let { p ->
            hypot(p.x - viewCenterX, p.y - viewCenterY) <= centerTolPx
        } ?: false
    }
    // Commandes du coin bas-droit, à portée du pouce : le suivi de la position, puis le
    // planificateur au plus près du coin - c'est celui qui reste, l'autre n'apparaissant que le
    // capteur allumé, et un bouton qui change de place au gré du GPS se chercherait à chaque fois.
    //
    // Position FIXE, à la seule barre de navigation près : elles ne se rangent pas au-dessus de
    // ce qui occupe le bas (profil, bande du planificateur), contrairement à l'échelle. Un
    // bouton qui saute de trois cents pixels à l'ouverture d'un panneau se cherche à chaque
    // fois ; il vaut mieux qu'il attende sous lui, là où la main l'a laissé.
    //
    // Posées AVANT tout ce qui occupe le bas, donc DESSOUS : la bande du planificateur, les
    // consignes de saisie et le profil les recouvrent au lieu de les pousser. Elles restent
    // au-dessus des infobulles, elles, qui ne doivent pas rendre un bouton intouchable.
    // Meme ecart sous le dernier bouton qu'entre les deux : la colonne se lit alors d'un
    // bloc, sans que le bas de l'ecran serre plus que ses propres intervalles. La barre de
    // navigation l'emporte si elle est plus haute - un bouton ne se glisse pas dessous.
    val navBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    Column(
        Modifier.align(Alignment.BottomEnd)
            .padding(end = 8.dp, bottom = maxOf(MapControlSpacing, navBottomDp)),
        verticalArrangement = Arrangement.spacedBy(MapControlSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        /*
         * Le suivi de la carte, allume et eteint DEPUIS LA CARTE.
         *
         * **Un interrupteur, la ou il n'y avait qu'un recentrage.** Le bouton ne paraissait que la
         * position sortie du centre, et ne savait faire qu'une chose : y revenir. Couper le suivi, lui,
         * demandait d'ouvrir le menu, les reglages, l'onglet Carte - pour un geste qu'on fait chaque fois
         * qu'on veut lire la carte ailleurs qu'a l'endroit ou l'on se tient, c'est-a-dire tout le temps.
         * Un testeur l'a dit sans detour : "c'est pas bien pratique".
         *
         * **Present des que le capteur tourne**, et non plus seulement hors centre : un interrupteur qui
         * disparait quand il est "au repos" ne s'eteint jamais, puisqu'il n'y a rien a toucher au moment
         * precis ou la carte est centree sur soi.
         *
         * **Il DIT l'etat**, au trait bleu du suivi comme les autres commandes de la carte. C'est la
         * seconde moitie du meme retour de terrain : le suivi se suspendait tout seul - bande du
         * planificateur, profil d'une trace, infobulle ouverte, silence d'apres-geste - sans que rien a
         * l'ecran ne le dise, et un suivi qu'on croit allume alors qu'il est en pause se raconte
         * exactement comme une panne.
         *
         * **Trois etats, un seul bouton** (cf. MapFollow.tapAction, qui porte la regle et sa raison) : il
         * allume et recentre, il ramene la position sans toucher au reglage tant qu'elle n'est pas
         * revenue, il eteint une fois qu'elle est centree. Le recentrage seul - ce que ce bouton savait
         * faire avant - garde ainsi sa place, la ou il sert : pendant le silence d'apres-geste.
         *
         * Rallumer et ramener levent tous deux ce silence, le geste en question etant ce bouton-ci (cf.
         * LocationControls.clearUserGesture). Eteindre ne deplace rien - la carte reste ou elle est,
         * c'est bien pour la lire la qu'on vient de couper.
         *
         * Le planificateur, lui, ne bouge pas pour autant : la colonne est alignee en bas, et c'est ce
         * bouton-ci qui s'ajoute ou se retire par le haut.
         */
        if (location.gpsActive) {
            val suit = settings.mapFollowPosition
            val geste = MapFollow.tapAction(following = suit, positionCentered = positionCentered)
            IconButton(
                onClick = {
                    when (geste) {
                        MapFollow.FollowTap.ARM -> {
                            vm.setMapFollowPosition(true)
                            // Le silence d'apres-geste est leve : le geste, c'est ce bouton-ci, et
                            // faire attendre cinq secondes celui qui vient de demander a etre suivi
                            // laisserait croire que l'appui n'a rien fait.
                            location.clearUserGesture()
                            location.recenterOnGps()
                        }
                        MapFollow.FollowTap.RECENTER -> {
                            location.clearUserGesture()
                            location.recenterOnGps()
                        }
                        // Eteindre ne deplace rien : la carte reste ou elle est, c'est bien pour la
                        // lire la qu'on vient de couper.
                        MapFollow.FollowTap.DISARM -> vm.setMapFollowPosition(false)
                    }
                },
                modifier = chrome.buttonBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        // DEUX axes, chacun deja significatif dans cette colonne : la TEINTE dit si le
                        // suivi est arme - bleu comme la cloche, les points d'interet et le
                        // planificateur -, et le DESSIN dit si la position est sous la croix. La croix
                        // pleine est celle du repere qu'elle tient au centre ; vide, elle dit que la
                        // position est ailleurs et que l'appui va l'y ramener.
                        if (suit && positionCentered) Icons.Filled.MyLocation
                        else Icons.Filled.LocationSearching,
                        stringResource(
                            when (geste) {
                                MapFollow.FollowTap.ARM -> R.string.action_follow_position_on
                                MapFollow.FollowTap.RECENTER -> R.string.action_center_on_location
                                MapFollow.FollowTap.DISARM -> R.string.action_follow_position_off
                            },
                        ),
                        tint = if (suit) MapChromeActive else chrome.fg,
                    )
                    // Disque central au bleu du point de position : la carte tient le repere au centre
                    // de l'ecran, et le bouton le porte. Le seul des trois etats ou il y a un point
                    // sous la croix - les deux autres portent la croix vide.
                    if (suit && positionCentered) {
                        Canvas(Modifier.size(MyLocationDotSize)) { drawCircle(color = RecenterDotColor) }
                    }
                }
            }
        }
        // Cloche de l'alerte d'éloignement, juste au-dessus du planificateur : la fonction sert
        // PENDANT la sortie, et le pouce la trouve au même endroit que le reste.
        //
        // Elle se lit à la couleur de son dessin, comme les autres commandes de la carte : gris
        // tant qu'aucune trace n'est suivie, bleu dès qu'on en suit une, rouge quand on s'en est
        // écarté - la bannière du bas dit alors de combien, mais la cloche l'annonce déjà à qui
        // regarde la carte.
        if (alertEnabled) {
            IconButton(onClick = { onBellTap() }, modifier = chrome.buttonBackground) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    stringResource(R.string.content_desc_off_track_alert),
                    tint = when {
                        alerting -> OffTrackAlertColor
                        followedTrack -> MapChromeActive
                        else -> chrome.fg
                    },
                )
            }
        }
        /*
         * Points d'interet : le bouton OUVRE LA BULLE ou l'on choisit ce qu'on veut voir.
         *
         * Il allumait la couche, et les vingt-sept categories se cochaient dans Reglages >
         * Trajets - c'est-a-dire a quatre gestes de la carte, dans un ecran qui recouvre
         * justement ce qu'on essaie de regarder. Or choisir ses points d'interet est un geste
         * de terrain : on cherche un camping en fin d'apres-midi, un point d'eau a la montee.
         *
         * Il n'allume donc plus rien : la couche suit le filtre, et tout masquer l'eteint (cf.
         * PoiFilters). La pastille reste bleue tant que la couche est affichee, comme le suivi
         * de position et l'alerte d'eloignement le font pour la leur - et la bulle ouverte le
         * dit aussi, le bouton restant visible dessous.
         *
         * Un marque-page, et non l'epingle : celle-ci est deja le dessin des marqueurs que ce
         * bouton COMMANDE, et celui du bouton GPS juste a cote - trois epingles pour trois
         * choses differentes. Le marque-page dit ce qu'on cherche ici, des endroits qu'on
         * retient le long du parcours.
         */
        if (settings.poiEnabled) {
            Box {
                /*
                 * La bulle est posee DANS le bouton, et non reperee a l'ecran par ses
                 * coordonnees : `unbounded` la laisse deborder de la place que le bouton
                 * occupe, sans peser sur la colonne ni sur ses voisins. Alignee par le bas, elle
                 * grandit vers le HAUT - le bouton est en bas de l'ecran, et une bulle centree
                 * sur lui sortirait par le bas.
                 *
                 * Declaree AVANT le bouton pour passer dessous : la pointe deborde de quelques
                 * points vers lui, et c'est le bouton qui doit rester touchable.
                 */
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .absoluteOffset(x = -PoiBubbleAnchorWidth)
                        .wrapContentSize(Alignment.BottomEnd, unbounded = true),
                ) {
                    PoiFilterBubbleAnchored(
                        open = poi.bubbleOpen,
                        filters = poiFilters,
                        onFilters = onPoiFilters,
                        // Le centre du bouton, mesure depuis son bas : la pointe le vise.
                        tailFromBottom = PoiBubbleAnchorWidth / 2,
                    )
                }
                IconButton(onClick = { poi.toggleBubble() }, modifier = chrome.buttonBackground) {
                val teinte = if (poi.visible || poi.bubbleOpen) MapChromeActive else chrome.fg
                /*
                 * L'attente prend la place du pictogramme, DANS le bouton.
                 *
                 * Le chargement ne se voyait nulle part : on deplacait la carte et les
                 * marqueurs arrivaient une seconde ou deux plus tard, sans que rien n'ait dit
                 * qu'on les cherchait. Ici, la ou l'on vient de taper.
                 *
                 * Exactement la taille du pictogramme qu'il remplace (24 dp, la taille par
                 * defaut d'une icone Material) : un rond plus petit ferait sauter le bouton a
                 * chaque requete, et c'est le genre de tressautement qu'on remarque bien plus
                 * que l'attente elle-meme.
                 */
                if (poi.loading) {
                    CircularProgressIndicator(
                        Modifier.size(PoiButtonIconSize), color = teinte, strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Outlined.BookmarkBorder, stringResource(R.string.poi_layer_title),
                        tint = teinte,
                    )
                }
                }
            }
        }
        // Masqué tant que sa bande est DÉPLOYÉE, qu'il ne servirait qu'à rouvrir. Réduite, il
        // reparaît et redéploie le trajet en cours : c'est le MÊME bouton qui ouvre et qui
        // rouvre. La bande réduite posait auparavant son propre bouton au coin bas-gauche, si
        // bien qu'un itinéraire en cours en affichait un à chaque bout de l'écran - deux cibles
        // pour une seule fonction, et rien pour dire laquelle faisait quoi.
        if (settings.routePlannerEnabled && !planner.expanded) {
            IconButton(onClick = {
                when {
                    // Trajet en cours, simplement rangé : on le redéploie tel quel. Aucune
                    // requête à faire, donc rien à demander au réseau - le parcours est déjà
                    // calculé, et le sortir de sa réduction ne le recalcule pas.
                    planner.open -> { vm.closeProfile(); planner.collapse(false) }
                    ServiceUrl.needsInternet(routingUrl) && !NetworkStatus.hasInternet(ctx) -> onNoConnection()
                    else -> {
                        vm.closeProfile()          // les deux occupent le bas de l'écran
                        // Le suivi allumé : on part d'où l'on est. Le départ seulement, et
                        // sur ce bouton seulement : les infobulles, elles, posent le lieu
                        // qu'on vient de toucher à l'un des deux bouts et complètent l'AUTRE
                        // (cf. RoutePlannerState.setStart) ; leur "Étape", qui remplit la
                        // première ligne vierge, ne pré-remplit rien.
                        planner.openPlanner(fromCurrentPosition = location.gpsActive)
                        planner.chooseProfile(RoutingProfile.of(settings.routingProfile))
                    }
                }
            }, modifier = chrome.buttonBackground) {
                // Bleu tant qu'un trajet est en cours, comme le suivi de position et la couche
                // des points d'intérêt : le bouton ne dit plus seulement "calculer un
                // itinéraire", il dit aussi "il y en a un rangé là-dessous".
                Icon(
                    Icons.Outlined.Directions, stringResource(R.string.planner_title),
                    tint = if (planner.open) MapChromeActive else chrome.fg,
                )
            }
        }
    }
}
