package fr.lc4918.trailog.ui.routes

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.ui.mappoint.AddressState
import fr.lc4918.trailog.domain.model.BubblePosition
import fr.lc4918.trailog.domain.model.PointFeature
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.SchemaItem
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.poi.Poi
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.geocode.GeocodeBubble
import fr.lc4918.trailog.ui.geocode.GeocodeSearchState
import fr.lc4918.trailog.ui.location.LocationControls
import fr.lc4918.trailog.ui.mappoint.MapPointBubble
import fr.lc4918.trailog.ui.mappoint.MapPointState
import fr.lc4918.trailog.ui.planner.RoutePlannerState
import fr.lc4918.trailog.ui.poi.PoiBubble
import fr.lc4918.trailog.ui.poi.PoiState
import fr.lc4918.trailog.ui.poi.poiCategoryLabelRes
import fr.lc4918.trailog.ui.points.AnchoredBubble
import fr.lc4918.trailog.ui.points.BubbleGeometry
import fr.lc4918.trailog.ui.points.InfoBubble
import fr.lc4918.trailog.ui.points.InfoBubbleLoading
import fr.lc4918.trailog.ui.settings.routingProfileLabel

/**
 * Les quatre infobulles de la carte : un waypoint, un point d'interet, un lieu trouve, un point d'appui
 * long.
 *
 * **Ce qu'elles ont en commun, et qui justifie de les reunir.** Toutes les quatre s'accrochent a un
 * endroit precis de la carte, se placent seules pour rester a l'ecran, et se referment sur une croix.
 * Elles partagent donc leur geometrie ([BubbleFrame]) et, pour trois d'entre elles, les trois memes
 * boutons qui composent un trajet - depart, arrivee, etape. Separees dans l'ecran, elles derivaient : ce
 * sont trois copies du meme geste, et une seule des trois s'etait vue corriger un defaut de placement.
 *
 * **Ce qui les distingue.** La bulle d'un waypoint montre des PROPRIETES, chargees apres le tap, et se
 * place a la position reglee dans les preferences. Les trois autres montrent un LIEU, connu d'emblee, et
 * les deux dernieres se posent dans celui des quatre coins qui deplace le moins la carte.
 */

/**
 * La geometrie commune aux quatre infobulles.
 *
 * @property geom ce qu'il faut degager - barre de statut, air au bord de l'ecran, ecart au point designe.
 * @property position le coin ou la bulle se pose, tel que le reglage le decide.
 * @property pan le glissement de carte qu'une bulle demande pour tenir entierement a l'ecran.
 */
@Stable
data class BubbleFrame(
    val geom: BubbleGeometry,
    val position: BubblePosition,
    val pan: (Int, Int) -> Unit,
)

/** La geometrie des infobulles, telle que les reglages et la taille des marqueurs la decident. */
@Composable
internal fun rememberBubbleFrame(
    settings: SettingsEntity,
    markerPx: Float,
    controller: MapController,
): BubbleFrame {
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 10.dp.roundToPx() }
    return remember(topInset, margin, gap, markerPx, settings.bubblePosition, controller) {
        BubbleFrame(
            geom = BubbleGeometry(topInset, margin, gap, markerPx.toInt()),
            position = BubblePosition.of(settings.bubblePosition),
            pan = { x, y -> controller.panByScreen(x.toFloat(), y.toFloat()) },
        )
    }
}

/**
 * L'infobulle d'un waypoint : ses proprietes, ou un spinner tant qu'elles chargent.
 *
 * Affichée dès le tap, placée selon le réglage Carte / Infobulles. Le placement est calculé dans la phase de layout, une fois la
 * taille réelle mesurée : la bulle apparaît donc directement au bon endroit, sans le saut que
 * provoquait un premier passage à taille nulle.
 *
 * Le décalage de carte qu'impose ce placement attend la même mesure : il part à l'instant où
 * la bulle remplace le spinner, les deux mouvements se lisant alors comme un seul. Décaler
 * dès le tap a été essayé et retiré : la hauteur de la bulle étant inconnue tant que ses
 * propriétés chargent, il fallait réserver l'encombrement maximal possible, et la carte
 * bougeait le plus souvent bien plus que nécessaire - parfois là où la bulle réelle, plus
 * courte, n'exigeait aucun mouvement.
 */
@Composable
internal fun BoxScope.MarkerBubbleLayer(
    settings: SettingsEntity,
    frame: BubbleFrame,
    vm: MainViewModel,
    dialogs: MainDialogState,
    selectedMarkerId: String?,
    selectedFeature: PointFeature?,
    schema: List<SchemaItem>,
    bubbleOffset: IntOffset?,
    maxHeightPx: Int,
) {
    val density = LocalDensity.current
    val off = bubbleOffset
    if (off != null && selectedMarkerId != null && !dialogs.editingFeature) {
        val maxH = maxHeightPx
        // Hauteur max de l'infobulle : elle tient sous la barre de statut (avec marges) sans
        // jamais couvrir plus de 60 % de l'écran, pour laisser voir la carte autour.
        val maxBubbleHeightDp = with(density) {
            minOf(maxH - frame.geom.topInset - 2 * frame.geom.margin,
                (maxH * BubbleMaxHeightRatio).toInt()).toDp()
        }
        AnchoredBubble(
            key = selectedMarkerId,
            publish = selectedFeature != null,
            panAllowed = frame.position != BubblePosition.AUTO,
            onPan = frame.pan,
            placement = { bw, bh, vw, vh -> frame.geom.at(frame.position, off.x, off.y, bw, bh, vw, vh) },
        ) {
            if (selectedFeature != null) {
                InfoBubble(feature = selectedFeature, schema = schema,
                    fontSp = settings.bubbleFont, bold = settings.bubbleBold,
                    titleFontSp = settings.bubbleTitleFont, titleBold = settings.bubbleTitleBold,
                    maxHeightDp = maxBubbleHeightDp,
                    backgroundAlpha = (settings.bubbleOpacityPct) / 100f,
                    onEdit = { dialogs.editingFeature = true }, onClose = { vm.closeMarker() })
            } else {
                InfoBubbleLoading()
            }
        }
    }
}

/**
 * Les trois infobulles d'un LIEU : point d'interet, lieu trouve par le geocodeur, point d'appui long.
 *
 * Reunies parce qu'elles portent les trois memes boutons - depart, arrivee, etape - et que ces boutons
 * font exactement la meme chose : ouvrir le planificateur, puis y poser le lieu. "Depart" et "Arrivee"
 * completent en outre l'autre bout du trajet par la position du porteur quand le capteur peut la rendre :
 * designer une arrivee, c'est demander a s'y rendre, et cela part d'ou l'on se tient. Ce que chacune sait de
 * son lieu differe (un point d'interet a une categorie, un point d'appui long n'a parfois qu'une paire de
 * coordonnees), et c'est tout ce qui les separe : [placeOfPoi] et [placeOfPoint] reduisent l'un et l'autre
 * a l'etape que le planificateur attend.
 *
 * @param onOpenPlanner ouvre le planificateur en fermant ce qui lui prendrait la place. L'ecran le porte,
 *   parce que lui seul sait ce qu'il faut refermer.
 */
@Composable
internal fun BoxScope.PlaceBubblesLayer(
    settings: SettingsEntity,
    frame: BubbleFrame,
    controller: MapController,
    poi: PoiState,
    geo: GeocodeSearchState,
    mapPoint: MapPointState,
    planner: RoutePlannerState,
    location: LocationControls,
    routingProfile: RoutingProfile,
    imperial: Boolean,
    idleTick: Int,
    moveTick: Int,
    onOpenPlanner: () -> Unit,
    onDistanceFromPosition: () -> Unit,
    onDistanceFromPoint: () -> Unit,
    // Un geste de l'infobulle n'a rien produit : l'ecran le dit (cf. MainDialogState.failure).
    onFailure: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val selPoi = poi.selected
    if (selPoi != null) {
        // idleTick SEUL, sans moveTick : l'infobulle garde sa place pres de son point pendant
        // le geste, comme celle d'un waypoint, au lieu de courir apres la carte a chaque image.
        val pOff = remember(selPoi, idleTick) {
            controller.screenOf(selPoi.lon, selPoi.lat)?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
        }
        if (pOff != null) {
            AnchoredBubble(
                key = selPoi.uuid,
                publish = true,
                // La position reglee pour les infobulles (cf. BubblePosition), et non le coin
                // qui deplace le moins la carte : un point d'interet est un marqueur comme un
                // autre, son infobulle doit s'ouvrir la ou l'utilisateur l'attend.
                panAllowed = frame.position != BubblePosition.AUTO,
                onPan = frame.pan,
                placement = { bw, bh, vw, vh -> frame.geom.at(frame.position, pOff.x, pOff.y, bw, bh, vw, vh) },
            ) {
                PoiBubble(
                    poi = selPoi,
                    onOpenWeb = { url ->
                        // Aucun navigateur installe : sans ce message, le lien serait une pastille qui
                        // ne fait rien.
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                            .onFailure { onFailure(R.string.error_no_app_link) }
                    },
                    // Les trois actions remplissent le planificateur et l'ouvrent : c'est
                    // l'ecran qui l'ouvre, parce que lui seul sait ce qu'il doit fermer
                    // pour lui laisser la place (cf. RoutePlannerState).
                    onSetStart = {
                        onOpenPlanner()
                        planner.setStart(placeOfPoi(selPoi, ctx), location.sensorEnabled)
                        poi.select(null)
                    },
                    onSetEnd = {
                        onOpenPlanner()
                        planner.setEnd(placeOfPoi(selPoi, ctx), location.sensorEnabled)
                        poi.select(null)
                    },
                    onAddStep = {
                        onOpenPlanner()
                        if (planner.addWaypoint(placeOfPoi(selPoi, ctx))) poi.select(null)
                    },
                    onClose = { poi.select(null) },
                    fontSp = settings.bubbleFont,
                    backgroundAlpha = (settings.bubbleOpacityPct) / 100f,
                )
            }
        }
    }
    // Infobulle du lieu trouvé : posée dans celui des quatre coins du lieu qui déplace le moins
    // la carte (cf. computeGeocodePlacement), et non à la position réglée pour les marqueurs.
    val gPlace = geo.place
    if (gPlace != null) {
        // Recalculee a chaque image du deplacement (moveTick) et non a la seule immobilisation :
        // l'infobulle reste ainsi collee a son epingle pendant tout le geste, au lieu de rester
        // sur place puis de la rejoindre d'un saut. Seul son coin change en cours de route.
        val gOff = remember(gPlace, idleTick, moveTick) {
            controller.screenOf(gPlace.lon, gPlace.lat)?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
        }
        if (gOff != null) {
            // Décalage de carte pour que l'épingle ET la bulle tiennent à l'écran ; le lieu
            // hors de la vue courante ramène l'ensemble au centre (cf. computeGeocodePlacement).
            //
            // Attendu que la caméra soit arrêtée : retenir un lieu la lance vers lui
            // (centerOnAtLeast), et tant qu'elle vole, la projection lue est celle d'AVANT le
            // vol - un décalage calculé dessus s'ajouterait au mouvement en cours au lieu de le
            // corriger, et posait le lieu hors de la carte. Son immobilisation incrémente
            // idleTick, d'où la comparaison au tick du moment où le lieu a été retenu.
            val pickedAtTick = remember(gPlace) { idleTick }
            AnchoredBubble(
                key = gPlace,
                publish = true,
                panAllowed = idleTick != pickedAtTick,
                onPan = frame.pan,
                placement = { bw, bh, vw, vh -> frame.geom.atNearestCorner(gOff.x, gOff.y, bw, bh, vw, vh) },
            ) {
                GeocodeBubble(
                    lines = gPlace.lines,
                    // Le lieu part tel quel dans le planificateur : il porte deja son
                    // adresse et ses coordonnees, c'est exactement ce qu'attend une etape.
                    onSetStart = { onOpenPlanner(); planner.setStart(gPlace, location.sensorEnabled); geo.clear() },
                    onSetEnd = { onOpenPlanner(); planner.setEnd(gPlace, location.sensorEnabled); geo.clear() },
                    onAddStep = {
                        onOpenPlanner()
                        if (planner.addWaypoint(gPlace)) geo.clear()
                    },
                    onClose = { geo.clear() },
                    fontSp = settings.bubbleFont,
                    backgroundAlpha = (settings.bubbleOpacityPct) / 100f,
                )
            }
        }
    }
    // Infobulle du point désigné par un appui long : même placement que celle d'un lieu trouvé,
    // dans celui des quatre coins du point qui déplace le moins la carte.
    val mPoint = mapPoint.point
    if (mPoint != null && mapPoint.bubbleVisible) {
        // Suit l'epingle image par image, comme celle d'un lieu trouve (cf. gOff).
        val mOff = remember(mPoint, idleTick, moveTick) {
            controller.screenOf(mPoint.first, mPoint.second)
                ?.let { p -> IntOffset(p.x.toInt(), p.y.toInt()) }
        }
        if (mOff != null) {
            // Décalage de carte seulement si aucun des quatre coins ne tenait : le point désigné
            // est en plein écran dans le cas ordinaire, et rien ne bouge. L'épingle, elle, ne
            // bouge jamais d'un pouce : elle reste sur le point, c'est la carte qui glisse.
            AnchoredBubble(
                key = mPoint,
                // Publié une fois l'adresse arrivée : mesurée au spinner, la bulle est plus
                // courte que la bulle réelle, et le recentrage - à usage unique - serait
                // consommé sur une hauteur qui n'est pas la sienne.
                publish = mapPoint.address != AddressState.Loading,
                panAllowed = true,
                onPan = frame.pan,
                placement = { bw, bh, vw, vh -> frame.geom.atNearestCorner(mOff.x, mOff.y, bw, bh, vw, vh) },
            ) {
                MapPointBubble(
                    address = mapPoint.address,
                    profileLabel = routingProfileLabel(routingProfile),
                    // La localisation éteinte dans le téléphone, la ligne disparaît : une
                    // mesure depuis une position inconnue ne partirait jamais (cf.
                    // MapPointBubble). L'affichage du repère, lui, n'y est pour rien - le
                    // capteur suffit. Une mesure déjà demandée retient la ligne : son
                    // résultat vaut pour l'endroit d'où elle est partie, et son tracé est
                    // sur la carte - rien ne l'expliquerait plus.
                    showPositionRow = location.sensorEnabled || mapPoint.positionMeasure != null,
                    positionMeasure = mapPoint.positionMeasure,
                    pointMeasure = mapPoint.pointMeasure,
                    imperial = imperial,
                    onDistanceFromPosition = { onDistanceFromPosition() },
                    onDistanceFromPoint = { onDistanceFromPoint() },
                    onSetStart = {
                        onOpenPlanner()
                        planner.setStart(placeOfPoint(mapPoint), location.sensorEnabled)
                        mapPoint.clear()
                    },
                    onSetEnd = {
                        onOpenPlanner()
                        planner.setEnd(placeOfPoint(mapPoint), location.sensorEnabled)
                        mapPoint.clear()
                    },
                    onAddStep = {
                        onOpenPlanner()
                        if (planner.addWaypoint(placeOfPoint(mapPoint))) mapPoint.clear()
                    },
                    onClose = { mapPoint.clear() },
                    fontSp = settings.bubbleFont,
                    backgroundAlpha = (settings.bubbleOpacityPct) / 100f,
                )
            }
        }
    }
}

/**
 * Un point d'interet en etape de trajet.
 *
 * Le nom de sa categorie a defaut de nom propre, comme dans l'infobulle : un lieu d'OpenStreetMap en est
 * souvent depourvu, et une etape sans libelle ne se relit pas dans l'historique.
 */
internal fun placeOfPoi(p: Poi, ctx: Context): GeocodePlace = GeocodePlace(
    listOfNotNull(p.label.ifBlank { ctx.getString(poiCategoryLabelRes(p.category)) }, p.city),
    p.lon, p.lat,
)

/**
 * Le point d'un appui long, en lieu d'etape.
 *
 * Son adresse quand on la connait, ses COORDONNEES sinon : un point au milieu d'un bois est une etape
 * parfaitement legitime - c'est peut-etre le depart du sentier - et refuser d'en faire une parce que le
 * geocodeur n'a pas de nom a lui donner reviendrait a n'accepter que les endroits qui ont une adresse.
 *
 * Point decimal impose : la virgule d'une locale francaise separerait a la fois les decimales et les deux
 * valeurs, et donnerait "44,56, 6,08" (cf. Photon.parse, meme repli).
 */
private fun placeOfPoint(mapPoint: MapPointState): GeocodePlace {
    val (lon, lat) = mapPoint.point ?: (0.0 to 0.0)
    val adresse = (mapPoint.address as? AddressState.Done)?.lines
    return GeocodePlace(adresse ?: listOf("%.5f, %.5f".format(java.util.Locale.US, lat, lon)), lon, lat)
}

/** Part de la hauteur d'écran que l'infobulle ne dépasse pas ; au-delà, ses propriétés défilent. */
private const val BubbleMaxHeightRatio = 0.6f
