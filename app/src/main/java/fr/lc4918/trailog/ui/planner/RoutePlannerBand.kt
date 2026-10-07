package fr.lc4918.trailog.ui.planner

import fr.lc4918.trailog.ui.profile.profileChartHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.ui.routes.strongHaptic
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import fr.lc4918.trailog.ui.settings.routingProfileIcon
import fr.lc4918.trailog.ui.settings.routingProfileShortLabel
import fr.lc4918.trailog.ui.theme.Spacing
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.geocode.PlaceSearch
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.components.SheetRoundButton
import fr.lc4918.trailog.ui.components.SheetTop
import fr.lc4918.trailog.ui.components.swipeDownToCollapse
import fr.lc4918.trailog.ui.components.tintedFieldColors
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.routes.SlopeLegendInfo
import fr.lc4918.trailog.ui.routes.SlopeLegendButtonSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import fr.lc4918.trailog.ui.profile.SlopeLegend
import fr.lc4918.trailog.ui.profile.SlopeRamp
import fr.lc4918.trailog.ui.profile.TrackInfoColumns
import fr.lc4918.trailog.ui.profile.routeInfos
import kotlinx.coroutines.delay
import fr.lc4918.trailog.ui.theme.TrailogIcons

/** Hauteur minimale de la bande : le double de celle des barres de consigne existantes, qui n'affichent
 *  qu'une ligne de texte. Le planificateur porte au moins la discipline et deux champs. */
private val BandMinHeight = 96.dp

/** Fond de la bande. Presque opaque : la carte transparait juste assez pour qu'on garde le sentiment de la
 *  survoler, sans nuire a la lecture des champs. */
private const val BandAlpha = 0.96f

/**
 * Hauteur du champ d'une etape, partagee entre le champ de saisie et l'affichage replie qui le remplace
 * au repos : les deux doivent avoir exactement la meme allure, sans quoi la ligne sauterait au focus.
 *
 * La hauteur est IMPOSEE aux deux, et non laissee a leur contenu : le champ de saisie se mesure sur sa
 * ligne de texte, l'affichage replie sur sa bordure, et les etapes remplies se collaient les unes aux
 * autres la ou les vides gardaient un jour entre elles.
 */
private val FieldHeight = 44.dp
private val FieldTextPadding = 14.dp

/** Ce qui separe deux champs : pose au-dessus et au-dessous de chacun, donc compte double entre voisins. */
private val FieldGap = Spacing.xs

/**
 * Le rail, a gauche des champs : un trait qui relie le depart a l'arrivee en passant par chaque etape,
 * avec un repere a hauteur de chaque champ - cercle creux au depart, point aux etapes, rond plein a
 * l'arrivee. Il dit d'un coup d'oeil que les champs sont les etapes d'UN trajet, et dans quel ordre.
 */
private val RailWidth = 24.dp

/**
 * Bande du planificateur d'itineraire, posee au bas de l'ecran.
 *
 * Elle prend le theme de l'application, comme tout ce qui se pose sur la carte : elle a longtemps porte le
 * sien, bascule par un bouton soleil/lune de son en-tete, mais une bande claire devant une carte sombre -
 * ou l'inverse - se lisait comme un morceau d'une autre application, et le bouton occupait la place d'une
 * commande du parcours pour un reglage qui n'en est pas un.
 *
 * **Reduite, elle ne pose plus rien** : la carte redevient entierement visible, ce qui est le geste attendu
 * quand on veut regarder le trace qu'on vient de calculer. Elle laissait auparavant un bouton de
 * reouverture au coin bas-gauche - un second bouton d'itineraire, en face de celui du coin bas-droit qui
 * disparaissait pour lui. Deux boutons pour la meme fonction, chacun a un bout de l'ecran : c'est le bouton
 * habituel qui rouvre desormais le trajet en cours (cf. MapBottomRightControls), et il ne bouge pas.
 *
 * Mise en page : maquette "Trailog - theme et maquettes", ecrans du calcul d'itineraire. De haut en bas,
 * separes par les crans de [Spacing] : l'en-tete, la ligne discipline et actions, les etapes sur leur
 * rail, l'ajout d'une etape, puis les resultats sur une carte a eux.
 */
@Composable
fun RoutePlannerBand(
    state: RoutePlannerState,
    imperial: Boolean,
    settings: SettingsEntity,
    lastLabelInsetPx: Float,
    maxHeight: Dp,
    onPickCurrentPosition: (PlannerStep) -> Unit,
    onPickOnMap: (PlannerStep) -> Unit,
    sensorEnabled: Boolean,
    geocoding: GeocodingParams,
    history: PlannerHistory,
    onPlaceChosen: (GeocodePlace) -> Unit,
    onPlaceForgotten: (GeocodePlace) -> Unit,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.collapsed) return
    var confirmerReset by remember { mutableStateOf(false) }
    // Coins hauts arrondis, bas droits : la bande est une feuille posee au bord de l'ecran.
    //
    // La forme DESSINE le fond et l'ombre, mais ne decoupe pas le contenu : un Surface a forme decoupe, et
    // un contenu decoupe ne recoit un toucher qu'apres un test de contour. Ce test echoue sous Robolectric
    // pour des coins inegaux - tous les touchers de la bande s'y perdaient. Rien ne deborde de toute
    // facon : le contenu garde sa marge, loin des coins.
    val bandShape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
    Surface(
        modifier = modifier.fillMaxWidth()
            .shadow(8.dp, bandShape, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = BandAlpha), bandShape),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // La bande ne depasse jamais [maxHeight] : au-dela, elle recouvrirait la carte qu'elle sert a
        // composer. C'est la LISTE DES ETAPES qui absorbe le reste, l'en-tete, les disciplines et les
        // resultats gardant leur hauteur propre - une etape de plus fait donc defiler la liste plutot
        // que grandir la bande.
        Column(
            // Le plancher cede devant le plafond : sur un petit ecran, un clavier haut peut ne laisser
            // moins que [BandMinHeight], et une hauteur minimale superieure au maximum ferait a
            // nouveau deborder la bande hors de l'ecran.
            Modifier.heightIn(min = minOf(BandMinHeight, maxHeight), max = maxHeight)
                .swipeDownToCollapse { state.collapseOrClose() }
                .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
        ) {
            BandHeader(
                recomputing = state.recomputing,
                canRefresh = state.usesCurrentPosition,
                onRefresh = { state.refreshCurrentPosition() },
                onClose = { state.collapseOrClose() },
            )
            ProfileAndActions(
                state = state,
                onImport = onImport,
                onDownload = onDownload,
                onReset = { confirmerReset = true },
                modifier = Modifier.padding(top = Spacing.s),
            )
            // La legende des pentes se lit sous "Ajouter une etape" tant que le profil est replie ;
            // deplie, son "i" passe sur la ligne du profil (cf. ResultsZone).
            val slopeLegend = settings.takeIf {
                it.routeSlopeLine && state.route is RouteState.Done && !state.profileVisible
            }
            if (state.detailsShown) {
                /*
                 * Details ouverts, les etapes et les resultats defilent D'UN SEUL TENANT. Chacun sa part de
                 * hauteur ne marchait pas : la bande pleine - etapes, totaux, profil - ne laissait aux
                 * details qu'une hauteur nulle, et ils etaient la sans qu'on puisse les voir. Les etapes ne
                 * defilent plus d'elles-memes ici : deux defilements dans le meme sens ne s'imbriquent pas.
                 * La saisie d'une etape referme les details (cf. detailsShown), et rend la liste a son
                 * propre defilement, celui qui ramene les propositions sous le champ.
                 */
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    StepList(state, onPickCurrentPosition, onPickOnMap, sensorEnabled, geocoding, history,
                        onPlaceChosen, onPlaceForgotten, slopeLegend = slopeLegend, scrollable = false,
                        modifier = Modifier.padding(top = Spacing.m))
                    ResultsZone(state, imperial, settings, lastLabelInsetPx)
                }
            } else {
                StepList(state, onPickCurrentPosition, onPickOnMap, sensorEnabled, geocoding, history, onPlaceChosen,
                    onPlaceForgotten, slopeLegend = slopeLegend,
                    modifier = Modifier.weight(1f, fill = false).padding(top = Spacing.m))
                ResultsZone(state, imperial, settings, lastLabelInsetPx)
            }
        }
    }
    if (confirmerReset) {
        AlertDialog(
            onDismissRequest = { confirmerReset = false },
            title = { Text(stringResource(R.string.planner_reset_confirm_title)) },
            text = { Text(stringResource(R.string.planner_reset_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmerReset = false; state.reset() }) {
                    Text(stringResource(R.string.planner_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmerReset = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * En-tete : le titre, puis la croix qui range la bande.
 *
 * **Un seul bouton pour ranger, la ou il y en avait deux.** Le chevron "reduire" gardait le trajet, la
 * croix le detruisait apres une question : deux boutons voisins, presque identiques, dont l'un se
 * rattrapait et l'autre non. La croix range desormais, comme le chevron qu'elle remplace, et le seul
 * bouton qui perd quelque chose le dit dans son libelle.
 *
 * Au-dessus du titre, la poignee (cf. SheetHandle) range la bande d'un toucher ou d'un glissement vers le
 * bas : le geste des feuilles des applications de cartographie, que la main tente d'elle-meme. Elle range
 * comme la croix : toutes deux gardent le trajet (cf. collapseOrClose).
 */
@Composable
private fun BandHeader(
    recomputing: Boolean,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    // La croix s'aligne sur le bord des champs, et se tient aussi loin du haut de la bande.
    SheetTop(onCollapse = onClose, onClose = onClose, edgeGap = Spacing.l,
        handleModifier = Modifier.testTag("planner_handle"),
        // Une etape sur la position actuelle : le parcours se refait d'ou l'on est maintenant.
        extraAction = if (canRefresh) { {
            SheetRoundButton(Icons.Filled.Refresh, stringResource(R.string.action_refresh), onRefresh,
                Modifier.testTag("planner_refresh"))
        } } else null) {
        // Le style des titres d'ecran, ceux de la bibliotheque et des reglages.
        Text(stringResource(R.string.planner_title), style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // Le recalcul se signale ICI, dans une ligne de hauteur fixe, et non en remplacant la zone
        // resultats : celle-ci porte le profil, et la bande se replierait a chaque changement d'etape.
        if (recomputing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/**
 * La discipline, puis les trois gestes du parcours : enregistrer, exporter, reinitialiser.
 *
 * **Un selecteur, et non une rangee de cinq pastilles.** La rangee prenait toute une ligne pour un choix
 * qu'on fait une fois par trajet, et ses cinq libelles, serres, passaient sur deux lignes. Ferme, le
 * selecteur ne montre que la discipline en cours ; il libere a cote la place des trois gestes, qui se
 * perdaient auparavant sous les etapes, en icones seules.
 *
 * Enregistrer et exporter sont GRISES tant que rien n'est calcule, et non retires : la ligne ne bouge pas
 * a l'arrivee du trajet. Reinitialiser, seul geste qui efface, est en rouge - et demande confirmation.
 */
@Composable
private fun ProfileAndActions(
    state: RoutePlannerState,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calcule = state.route is RouteState.Done
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ProfileSelect(state.profile, { state.chooseProfile(it) }, Modifier.weight(1f))
        val neutre = MaterialTheme.colorScheme.onSurfaceVariant
        BandAction(Icons.Outlined.Folder, stringResource(R.string.planner_action_save), neutre, calcule, onImport)
        BandAction(Icons.Filled.Download, stringResource(R.string.planner_action_export), neutre, calcule, onDownload)
        BandAction(TrailogIcons.Trash, stringResource(R.string.planner_reset),
            MaterialTheme.colorScheme.error, true, onReset)
    }
}

/**
 * Le selecteur de discipline : l'icone et le libelle court de la discipline en cours, et le menu des
 * cinq, chacune avec son icone. Meme fond et meme contour que les champs des etapes, dont il est le voisin.
 */
@Composable
private fun ProfileSelect(current: RoutingProfile, onSelect: (RoutingProfile) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(FieldHeight).clip(shape)
                .background(scheme.surfaceContainerLow)
                .border(if (open) 2.dp else 1.dp, if (open) scheme.primary else scheme.outlineVariant, shape)
                .clickable { open = true }
                .padding(start = Spacing.m, end = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(routingProfileIcon(current), null, Modifier.size(20.dp), tint = scheme.primary)
            Text(routingProfileShortLabel(current), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = scheme.surfaceContainer,
            modifier = Modifier.width(212.dp),
        ) {
            RoutingProfile.entries.forEach { p ->
                val retenue = p == current
                DropdownMenuItem(
                    text = {
                        Text(routingProfileShortLabel(p), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (retenue) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    leadingIcon = { Icon(routingProfileIcon(p), null, Modifier.size(20.dp)) },
                    trailingIcon = if (retenue) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
                    onClick = { open = false; onSelect(p) },
                    colors = MenuDefaults.itemColors(
                        textColor = if (retenue) scheme.onPrimaryContainer else scheme.onSurface,
                        leadingIconColor = if (retenue) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        trailingIconColor = scheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp).clip(MaterialTheme.shapes.small)
                        .then(if (retenue) Modifier.background(scheme.primaryContainer) else Modifier),
                )
            }
        }
    }
}

/** Un geste du parcours : l'icone, et son libelle dessous. Grise et inerte tant qu'il n'a rien a faire. */
@Composable
private fun BandAction(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).height(52.dp).clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.sp, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Les etapes sur leur rail, et le bouton d'ajout sous la derniere.
 *
 * L'ajout se pose a l'aplomb du rail, son "+" a la place d'un repere : il n'appartient a aucune etape, il
 * en cree une de plus au bout du trajet.
 */
@Composable
private fun StepList(
    state: RoutePlannerState,
    onPickCurrentPosition: (PlannerStep) -> Unit,
    onPickOnMap: (PlannerStep) -> Unit,
    sensorEnabled: Boolean,
    geocoding: GeocodingParams,
    history: PlannerHistory,
    onPlaceChosen: (GeocodePlace) -> Unit,
    onPlaceForgotten: (GeocodePlace) -> Unit,
    /** Les reglages de la legende des pentes, quand son "i" doit se poser au bout de la ligne d'ajout. */
    slopeLegend: SettingsEntity? = null,
    /** Faux quand un conteneur exterieur defile deja (cf. les details ouverts, dans la bande). */
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val drag = remember { StepDrag() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    Column(if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier) {
        state.steps.forEachIndexed { i, step ->
            // Par identifiant, et non par rang : une ligne deposee ailleurs garde son champ, son focus et
            // ses propositions, au lieu de les laisser a celle qui prend sa place.
            key(step.id) {
                StepRow(
                    state = state, step = step, index = i,
                    placeholder = stringResource(
                        when {
                            i == 0 -> R.string.planner_start
                            i == state.steps.lastIndex -> R.string.planner_end
                            else -> R.string.planner_via
                        }
                    ),
                    onPickCurrentPosition = onPickCurrentPosition,
                    onPickOnMap = onPickOnMap,
                    sensorEnabled = sensorEnabled,
                    geocoding = geocoding,
                    history = history,
                    onPlaceChosen = onPlaceChosen,
                    onPlaceForgotten = onPlaceForgotten,
                    modifier = Modifier
                        .onSizeChanged { drag.heights[step.id] = it.height }
                        .zIndex(if (drag.from == i) 1f else 0f)
                        .graphicsLayer { translationY = drag.shift(i, state.steps.map { it.id }) }
                        // La ligne tenue se detache par une TEINTE, et non par une ombre : une ombre tombe
                        // vers le bas, et laissait sous la ligne une marge plus large qu'au-dessus. La teinte
                        // est opaque - les lignes qu'on survole ne doivent pas transparaitre dessous.
                        .then(
                            if (drag.from == i) Modifier.background(
                                MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall)
                            else Modifier
                        ),
                    handle = Modifier.pointerInput(step.id) {
                        // Des que le doigt bouge, sans appui long : la poignee ne sert qu'a ca. L'ecart de
                        // toucher du systeme fait la difference avec un effleurement, et la vibration dit
                        // l'instant ou la ligne est prise en main.
                        detectDragGestures(
                            onDragStart = {
                                // Le clavier et le focus se retirent : on range des etapes, on n'en saisit plus.
                                focusManager.clearFocus()
                                drag.start(state.steps.indexOfFirst { it.id == step.id })
                                strongHaptic(context)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val avant = drag.to
                                drag.move(amount.y, state.steps.map { it.id })
                                if (drag.to != avant) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = { drag.end()?.let { (a, b) -> state.moveStepTo(a, b) } },
                            onDragCancel = { drag.end() },
                        )
                    },
                )
            }
        }
        if (state.canAddStep || slopeLegend != null) {
            var legende by rememberSaveable { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { if (state.canAddStep) AddStepButton { state.addStep() } }
                if (slopeLegend != null) {
                    Box(
                        Modifier.padding(top = Spacing.xs).size(SlopeLegendButtonSize).clip(CircleShape)
                            .clickable { legende = !legende }.testTag("planner_slope_legend_info"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.settings_profile_slope_legend),
                            Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Sous la ligne, et non dans une fenetre : le trace colorie reste visible au-dessus de la bande,
            // et la legende se lit a cote de lui.
            if (slopeLegend != null && legende) {
                SlopeLegend(SlopeRamp.DefaultClassTenths, slopeLegend.profLegendFont,
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("planner_slope_legend"),
                    bold = slopeLegend.profLegendBold)
            }
        }
    }
}

/** "Ajouter une etape" : un "+" cercle en pointille a l'aplomb du rail, et le libelle a cote. */
@Composable
private fun AddStepButton(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.padding(top = Spacing.xs).height(40.dp).clip(CircleShape).clickable(onClick = onClick)
            .padding(end = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RailWidth), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(20.dp).drawBehind {
                    val w = 1.5.dp.toPx()
                    drawCircle(primary, radius = (size.minDimension - w) / 2, style = Stroke(
                        width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                    ))
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = primary)
            }
        }
        Text(stringResource(R.string.planner_add_step), style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = primary, modifier = Modifier.padding(start = Spacing.m))
    }
}

/**
 * Le rail d'une etape : le trait qui la relie a ses voisines, et son repere a hauteur du champ. Chaque
 * ligne dessine sa part - la moitie haute vers la precedente, la moitie basse vers la suivante, jusqu'au
 * bas de ses propositions si elles sont ouvertes - et les parts se rejoignent d'une ligne a l'autre.
 */
private fun Modifier.stepRail(index: Int, count: Int, line: Color, mark: Color, hole: Color, via: Color) =
    drawBehind {
        val x = RailWidth.toPx() / 2
        val y = (FieldGap + FieldHeight / 2).toPx()
        val w = 2.dp.toPx()
        if (index > 0) drawLine(line, Offset(x, 0f), Offset(x, y), w)
        if (index < count - 1) drawLine(line, Offset(x, y), Offset(x, size.height), w)
        val c = Offset(x, y)
        when (index) {
            0 -> {
                drawCircle(hole, 7.dp.toPx(), c)
                drawCircle(mark, 5.5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            }
            count - 1 -> {
                drawCircle(mark, 9.5.dp.toPx(), c)
                drawCircle(hole, 8.dp.toPx(), c)
                drawCircle(mark, 5.dp.toPx(), c)
            }
            else -> {
                drawCircle(hole, 5.dp.toPx(), c)
                drawCircle(via, 3.dp.toPx(), c)
            }
        }
    }

/** Une etape : son champ, sa poignee de glissement, sa suppression, puis ses propositions. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun StepRow(
    state: RoutePlannerState,
    step: PlannerStep,
    index: Int,
    placeholder: String,
    onPickCurrentPosition: (PlannerStep) -> Unit,
    onPickOnMap: (PlannerStep) -> Unit,
    sensorEnabled: Boolean,
    geocoding: GeocodingParams,
    history: PlannerHistory,
    onPlaceChosen: (GeocodePlace) -> Unit,
    onPlaceForgotten: (GeocodePlace) -> Unit,
    modifier: Modifier = Modifier,
    /** Le geste de la poignee : le glisser-deposer, pose par la liste qui sait ou deposer (cf. StepDrag). */
    handle: Modifier = Modifier,
) {
    var focused by remember(step.id) { mutableStateOf(false) }
    // Le clic sur l'affichage replie ne peut PAS demander le focus lui-meme : tant qu'il tient la place du
    // champ, celui-ci n'est pas compose et son FocusRequester n'a aucun noeud a saisir - la demande levait
    // une exception, et l'application se fermait des qu'on revenait sur une etape deja remplie. Le clic
    // reclame donc le champ, et le focus lui est donne a la composition suivante, une fois qu'il existe.
    var wantsFocus by remember(step.id) { mutableStateOf(false) }
    // La liste des etapes defile sur elle-meme : un champ situe en bas peut voir ses propositions naitre
    // hors de la zone visible. On les y ramene des qu'elles apparaissent, faute de quoi la premiere ligne
    // - la position actuelle, ou le spinner - resterait invisible sous le bord.
    val bringIntoView = remember(step.id) { BringIntoViewRequester() }
    val focusRequester = remember(step.id) { FocusRequester() }
    // Choisir une proposition termine la saisie : on rend le clavier et on relache le focus, faute de quoi
    // le clavier resterait leve devant une bande dont il n'y a plus rien a lire.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun settle() { keyboard?.hide(); focusManager.clearFocus() }
    // Interrogation du geocodeur, une frappe stabilisee - meme delai et meme seuil que la recherche de
    // lieu de la carte : c'est le meme service, et il refuserait une requete par lettre.
    val ctx = LocalContext.current
    LaunchedEffect(step.query, step.target, step.retry) {
        val q = step.query.trim()
        if (step.target != null || q.length < 3) {
            step.results = emptyList(); step.searching = false; step.failed = false; return@LaunchedEffect
        }
        step.searching = true
        step.failed = false
        delay(350)
        // Une seconde tentative avant d'abandonner : le premier appel paie l'ouverture de la liaison et
        // echoue parfois au delai, la ou le suivant, sur connexion deja etablie, repond aussitot.
        var found = PlaceSearch.search(ctx, geocoding.base, q, geocoding.lang, geocoding.limit, geocoding.center)
        if (found == null) {
            delay(300)
            found = PlaceSearch.search(ctx, geocoding.base, q, geocoding.lang, geocoding.limit, geocoding.center)
        }
        step.results = found ?: emptyList()
        step.failed = found == null
        step.searching = false
    }
    // Ce que l'etape AFFICHE au repos : le lieu retenu s'il y en a un, sinon la frappe en cours. Un lieu
    // retenu n'est pas modifiable en place - on tape par-dessus, ce qui le remplace (cf.
    // RoutePlannerState.type) : le champ de saisie, lui, ne porte donc jamais que [PlannerStep.query].
    val shown = when (val t = step.target) {
        is StepTarget.Place -> t.place.label
        StepTarget.CurrentPosition -> stringResource(R.string.planner_current_position)
        null -> step.query
    }
    // Le champ vient d'apparaitre a la demande d'un clic : il est desormais compose, on peut lui donner le
    // focus. Sous garde malgre tout - un focus refuse doit rendre la main a l'affichage replie, pas fermer
    // l'application.
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) {
            runCatching { focusRequester.requestFocus() }
            wantsFocus = false
        }
    }
    val scheme = MaterialTheme.colorScheme
    val fieldShape = MaterialTheme.shapes.small
    val fieldStyle = MaterialTheme.typography.bodyMedium
    Column(
        modifier.bringIntoViewRequester(bringIntoView).stepRail(
            index, state.steps.size,
            line = scheme.outlineVariant, mark = scheme.primary, hole = scheme.surface, via = scheme.onSurfaceVariant,
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(RailWidth + Spacing.s))
            Box(Modifier.weight(1f).padding(vertical = FieldGap).height(FieldHeight)) {
                if (step.target != null && !focused && !wantsFocus) {
                    // Etape choisie et champ au repos : on montre le libelle TRONQUE. Un champ de saisie
                    // ne sait pas abreger - il fait defiler son texte et le coupe net au bord, sans dire
                    // qu'il en reste. Le champ reel reprend sa place des qu'on le touche.
                    Box(
                        Modifier.fillMaxSize().clip(fieldShape)
                            .background(scheme.surfaceContainerLow)
                            .border(1.dp, scheme.outlineVariant, fieldShape)
                            .clickable { wantsFocus = true }
                            // A droite, la place de la croix : le libelle s'abrege avant elle.
                            .padding(start = FieldTextPadding, end = 36.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(shown, maxLines = 1, overflow = TextOverflow.Ellipsis, style = fieldStyle)
                    }
                } else {
                    // La saisie, et elle seule : y poser le libelle du lieu retenu ferait ecrire la
                    // nouvelle frappe A COTE de lui plutot qu'a sa place, et c'est le tout - "VoiGrenoble,
                    // Isere, France" - qui partait au geocodeur. Le champ s'ouvre donc vide sur une etape
                    // deja remplie, son intitule ("Depart", "Arrivee") rappelant de quelle etape il s'agit ;
                    // le lieu reste pose tant qu'on n'a rien tape, et revient si l'on ressort du champ.
                    CompactOutlinedTextField(
                        value = step.query,
                        onValueChange = { state.type(step, it) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = tintedFieldColors(),
                        modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
                            .onFocusChanged {
                                focused = it.isFocused
                                state.setEditing(step, it.isFocused)
                                if (it.isFocused) state.focus(step)
                            },
                        textStyle = fieldStyle,
                        placeholder = {
                            Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = fieldStyle, color = scheme.onSurfaceVariant)
                        },
                    )
                }
                // Attente et effacement POSES SUR le champ, et non dans son emplacement d'icone de fin :
                // le texte garde ainsi la meme marge a gauche qu'a droite, et court sous eux, que leur
                // transparence laisse lire. Le spinner se tient a gauche de la croix : l'interrogation
                // porte sur ce qu'on vient de taper, elle appartient au champ et non a la liste dessous.
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (step.searching) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    if (shown.isNotEmpty()) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).clickable { state.clearStep(step) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, stringResource(R.string.planner_clear_step),
                                Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                // Une poignee de glissement, qui remplace les deux fleches : une etape se range en un geste,
                // la ou il fallait taper autant de fois qu'elle avait de rangs a franchir. Le geste part de la
                // poignee seule, apres un appui long, et la ne dispute rien au defilement de la liste ni au
                // champ de saisie.
                Box(
                    handle.size(width = 28.dp, height = FieldHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    // Le meme dessin que la poignee des couches et des dossiers du menu lateral : meme icone,
                    // meme taille, meme gris - c'est le meme geste.
                    Icon(Icons.Filled.DragIndicator, stringResource(R.string.planner_drag_step),
                        Modifier.size(18.dp), tint = scheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                // Le bord droit de la corbeille, et non celui de sa cible, tombe sur le bord de la bande.
                IconButton(onClick = { state.removeStep(index) }, enabled = state.steps.size > 2,
                    modifier = Modifier.offset(x = Spacing.s).size(width = 36.dp, height = 40.dp)) {
                    Icon(TrailogIcons.Trash, stringResource(R.string.planner_remove_step), Modifier.size(20.dp),
                        tint = scheme.onSurfaceVariant.copy(alpha = if (state.steps.size > 2) 1f else 0.38f))
                }
            }
        }
        val vierge = focused && step.untouched
        val rappels = if (vierge) history.places.filter { !state.usesPlace(it.label, step) } else emptyList()
        // Un champ vierge propose TOUJOURS quelque chose - au minimum le point a montrer sur la carte, qui
        // ne depend d'aucun capteur : la zone de propositions se ramene donc dans le champ des qu'il est vierge.
        val suggesting = focused && (step.searching || step.results.isNotEmpty() ||
            step.failed || vierge || rappels.isNotEmpty())
        LaunchedEffect(suggesting, step.results.size, step.searching) {
            if (suggesting) bringIntoView.bringIntoView()
        }
        // Les propositions tiennent dans UNE carte, sous le champ et a l'aplomb de ses bords : elles se
        // lisent comme ce que le champ offre, et non comme des lignes de la bande entre deux etapes.
        if (vierge || rappels.isNotEmpty() || step.failed || step.results.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = scheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(start = RailWidth + Spacing.s, end = 64.dp, bottom = Spacing.xs),
            ) {
            Column(Modifier.padding(vertical = Spacing.xs)) {
            // Position actuelle : proposee au focus tant que rien n'a ete tape, et non offerte par un bouton
            // permanent. Elle n'est utile qu'a l'instant ou l'on remplit un champ vide.
            // Seulement si la localisation est allumee dans le telephone : sans position connue, le calcul
            // echouerait sur un "Aucun itineraire" que rien n'expliquerait. Une proposition qu'on ne peut pas
            // honorer ne vaut rien. L'AFFICHAGE du repere sur la carte, lui, n'entre pas en compte - la
            // position se demande au capteur le temps du calcul, sans rien poser sur la carte.
            // Elle peut servir PLUSIEURS fois dans un meme trajet : partir d'ou l'on est, passer par un col et
            // y revenir, c'est la boucle, et c'est le trajet le plus courant a pied comme a velo. Seul le
            // doublon COLLE - deux etapes voisines sur le meme point - reste hors de portee : le troncon entre
            // les deux serait de longueur nulle, et le moteur refuse la requete entiere.
            if (sensorEnabled && vierge && state.canUseCurrentPosition(step)) {
                SuggestionRow(
                    label = stringResource(R.string.planner_current_position),
                    icon = true,
                    onClick = { onPickCurrentPosition(step); settle() },
                )
            }
            /*
             * Un point MONTRE sur la carte, juste apres la position actuelle - et en tete quand celle-ci sert
             * deja ailleurs dans le trajet, la ligne ci-dessus ne s'affichant plus.
             *
             * **Ce qu'aucune frappe ne trouve.** Un depart de sentier, un col, un croisement de pistes, le coin
             * d'un parking : le geocodeur n'a pas de nom pour eux, et les chercher au clavier ne rend rien. Le
             * seul moyen de les designer est de les montrer, et la carte est deja dessous.
             *
             * Offerte sans condition de capteur ni de reseau, a la difference de la position actuelle : montrer
             * un endroit ne demande rien a personne. L'adresse qui suivra, elle, passe par le geocodeur - mais
             * son silence ne coute que le nom (cf. RoutePlannerState.nameMapPoint), jamais l'etape.
             */
            if (vierge) {
                SuggestionRow(
                    label = stringResource(R.string.planner_pick_on_map),
                    icon = true,
                    image = Icons.Filled.Place,
                    onClick = { onPickOnMap(step); settle() },
                )
            }
            /*
             * Historique : les huit derniers lieux retenus, proposes au focus d'un champ vide, comme la
             * position actuelle et au meme moment.
             *
             * Ils s'effacent des la premiere frappe : ce qu'on tape prime toujours sur ce qu'on a fait hier,
             * et deux listes superposees au-dessus d'un clavier ne se lisent pas.
             *
             * Un lieu DEJA POSE ailleurs dans le trajet n'y figure pas : le choisir donnerait deux etapes au
             * meme endroit, donc un troncon de longueur nulle. Celui de l'etape courante, lui, reste offert -
             * c'est elle qu'on est en train de remplacer.
             */
            if (rappels.isNotEmpty()) {
                rappels.forEach { lieu ->
                    SuggestionRow(
                        label = lieu.label,
                        icon = true,
                        image = Icons.Filled.History,
                        onClick = { onPlaceChosen(lieu); state.choose(step, StepTarget.Place(lieu)); settle() },
                        onForget = { onPlaceForgotten(lieu) },
                    )
                }
            }
            // Echec du service : on le DIT, avec de quoi reessayer. Le silence laissait croire que le lieu
            // n'existait pas.
            if (step.failed) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { step.askRetry() }
                        .padding(horizontal = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.planner_search_failed), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    // Le style d'un bouton de dialogue (labelLarge) : c'est le meme genre de geste.
                    Text(stringResource(R.string.planner_retry), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            step.results.forEach { place ->
                SuggestionRow(label = place.label, icon = false,
                    onClick = { onPlaceChosen(place); state.choose(step, StepTarget.Place(place)); settle() })
            }
            }
            }
        }
    }
}

/** Une proposition sous un champ : la position actuelle, ou un lieu rendu par le geocodeur. */
@Composable
private fun SuggestionRow(
    label: String,
    icon: Boolean,
    onClick: () -> Unit,
    image: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.MyLocation,
    onForget: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick)
            .padding(start = Spacing.m, end = if (onForget != null) Spacing.xs else Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            // L'historique en gris : ce n'est qu'un rappel. Les deux gestes - ou je suis, un point de la
            // carte - dans l'accent.
            val teinte = if (onForget != null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            Icon(image, null, Modifier.size(18.dp), tint = teinte)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (icon) Spacing.m else 0.dp, top = 6.dp, bottom = 6.dp).weight(1f))
        /*
         * La croix des seules propositions d'HISTORIQUE (cf. [onForget] : les autres ne la passent pas).
         *
         * Visible, et non un appui long : l'historique se remplit tout seul de ce qu'on consulte, il faut
         * donc pouvoir en retirer ce qu'on n'y a pas mis expres - et un geste que rien n'annonce n'existe
         * pas pour qui ne le connait pas deja.
         *
         * Rien a fermer ni a rouvrir : la ligne disparait, les autres remontent, le champ garde le focus.
         */
        if (onForget != null) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                IconButton(onClick = onForget, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close, stringResource(R.string.planner_forget_place),
                        Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Ce que donne le calcul : les totaux, et le profil.
 *
 * Rien tant que le parcours n'a pas deux etapes : la zone n'apparait qu'avec quelque chose a dire.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsZone(
    state: RoutePlannerState,
    imperial: Boolean,
    settings: SettingsEntity,
    lastLabelInsetPx: Float,
) {
    when (val r = state.route) {
        RouteState.Idle -> Unit
        // Premier calcul : rien a montrer encore, et le spinner de l'en-tete le dit deja. Ne rien poser
        // ici evite d'ouvrir puis refermer une zone de 40 dp a chaque frappe.
        RouteState.Loading -> Unit
        // Deux echecs, deux messages : le trajet qu'on ne sait pas relier, et la position qu'on ne sait pas
        // trouver. Le second n'a jamais atteint le moteur, et le dire "Aucun itineraire" envoyait chercher
        // la faute du cote de la discipline ou des etapes.
        RouteState.Failed -> Text(stringResource(R.string.geocode_no_route),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m))
        RouteState.NoPosition -> Text(stringResource(R.string.planner_no_position),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m))
        // Le reseau, lui, porte de quoi REDEMANDER : rien n'est a corriger dans le trajet, il n'y a qu'a
        // recommencer une fois la liaison revenue. Meme ligne que l'echec de la recherche d'un lieu, au
        // dessus : c'est le meme genre de panne, et le meme geste la repare.
        RouteState.NoNetwork -> Row(
            Modifier.padding(top = Spacing.s).fillMaxWidth().heightIn(min = 44.dp)
                .clickable { state.retryRoute() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.planner_no_network), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.planner_retry), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        is RouteState.Done -> {
            // Fenetre affichee du profil : la plage zoomee, ou tout le parcours. Le kilometrage n'est
            // jamais remis a zero, seules les stats du bandeau sont recalculees sur la portion visible.
            val zoom = state.zoomRange
            val samples = remember(r.track, zoom) {
                val s = r.track.samples
                if (zoom != null && zoom.last < s.size) s.subList(zoom.first, zoom.last + 1) else s
            }
            val stats = remember(r.track, zoom, samples) {
                if (zoom != null) TrackMath.statsOf(samples) else r.track.stats
            }
            // Zoome, la duree est ESTIMEE au prorata de la distance : le moteur ne la rend que pour
            // le trajet entier. C'est une approximation - une portion qui monte se parcourt plus
            // lentement qu'une portion plate de meme longueur - d'ou le "~" qui la precede.
            val partSeconds = if (r.track.stats.distance > 0)
                r.seconds * stats.distance / r.track.stats.distance else 0.0
            // Les resultats sur une carte a eux : ils se lisent a part des etapes qui les ont produits.
            Column(
                Modifier.padding(top = Spacing.m).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                    .padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.xs),
            ) {
            // Memes colonnes, memes tailles et meme reglage que les infos d'une trace sous son profil :
            // c'est la meme lecture, sur un parcours qu'on vient de calculer plutot que sur un fichier.
            TrackInfoColumns(
                routeInfos(stats, if (state.zoomed) partSeconds else r.seconds, state.zoomed, imperial),
                fontSp = settings.profBarFont,
                bold = settings.profBarBold,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // Le profil est replie derriere son libelle : il occupe a lui seul la moitie de la hauteur
            // disponible, et il n'a d'interet qu'une fois le trajet compose. La zone resultats se reduit
            // donc a une ligne de totaux et a cette bascule, tant qu'on ne demande pas le relief.
            Row(
                Modifier.fillMaxWidth().height(40.dp).clickable { state.toggleProfile() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    stringResource(
                        if (state.profileVisible) R.string.planner_hide_profile
                        else R.string.planner_show_profile
                    ),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                // La legende des pentes, derriere un "i", quand le profil est colorie par pente : comme celui
                // d'une trace, elle ne se deplie plus dans la bande (cf. SlopeLegendInfo).
                if (settings.routeSlopeLine && state.profileVisible) SlopeLegendInfo(settings)
                // Calcule sur le telephone : dit en passant, sans en faire un evenement. C'est ce qui
                // explique un calcul plus lent qu'a l'habitude, ou un trajet trouve sans reseau.
                if (r.offline) {
                    Text(stringResource(R.string.planner_computed_offline), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .padding(horizontal = Spacing.s, vertical = 3.dp))
                }
                // Retour a la vue complete : sur cette ligne parce qu'il concerne le profil, et non le
                // parcours. Bouton a part DANS une ligne cliquable : son propre clic l'emporte sur celui
                // de la ligne, qui continue d'ouvrir et de fermer le profil partout ailleurs.
                if (state.zoomed) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        IconButton(onClick = { state.resetZoom() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Fullscreen, stringResource(R.string.planner_zoom_out),
                                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Le chevron montre le SENS DU GESTE a venir, non l'etat courant : profil replie, il
                // pointe vers le bas pour dire qu'il va se deployer ; deploye, vers le haut pour le
                // refermer.
                Icon(
                    if (state.profileVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.profileShown) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val hauteurGraphe = profileChartHeight(
                    settings.profileVerticalScale, stats.min, stats.max,
                    samples.last().x - samples.first().x,
                    constraints.maxWidth, PlannerChartMaxHeight, settings.profAxisFont,
                )
                Box(Modifier.fillMaxWidth().height(hauteurGraphe), contentAlignment = Alignment.Center) {
                    ElevationProfile(
                        samples = samples, stats = stats,
                        grid = settings.profileGrid,
                        slope = settings.routeSlopeLine,
                        lineColor = MaterialTheme.colorScheme.primary,
                        axisFontSp = settings.profAxisFont,
                        axisBold = settings.profAxisBold,
                        cursorX = state.cursor,
                        onScrub = { state.tapProfile(it) },
                        // Un appui simple ouvre le profil en grand, le curseur pose ou l'on a touche.
                        onTap = { state.tapProfile(it); state.openViewer(PlannerViewer.PROFILE) },
                        onZoom = { scale, fraction -> state.zoomBy(scale, fraction, r.track.samples.size) },
                        // Double-tap : un grossissement franc au point vise, la ou le pincement dose.
                        onDoubleTap = { fraction -> state.zoomBy(2f, fraction, r.track.samples.size) },
                        lastLabelInsetPx = lastLabelInsetPx,
                        verticalScale = settings.profileVerticalScale,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("planner_profile"),
                    )
                }
                }
            }
            // La zone "Details", sous le profil et repliee comme lui : les surfaces et les types de voies.
            HorizontalDivider(Modifier.padding(top = 2.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // Ouverts, les details se ramenent a l'ecran : ils naissent sous le profil, souvent sous le bord.
            val detailsInView = remember { BringIntoViewRequester() }
            LaunchedEffect(state.detailsShown) {
                if (state.detailsShown) { delay(50); detailsInView.bringIntoView() }
            }
            Column(Modifier.bringIntoViewRequester(detailsInView)) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).clickable { state.toggleDetails() }.testTag("planner_details_toggle"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (state.detailsVisible) R.string.planner_hide_details else R.string.planner_show_details
                    ),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                Icon(
                    if (state.detailsVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.detailsShown) {
                DetailsZone(
                    done = r, imperial = imperial,
                    onOpen = { v, initial -> state.openViewer(v, initial) },
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            }
            }
            }
        }
    }
}

/** Libelle par defaut d'une couche importee : les deux bouts du trajet. */
fun defaultRouteName(steps: List<StepTarget>, currentPositionLabel: String): String {
    fun label(t: StepTarget) = when (t) {
        is StepTarget.Place -> t.place.label.substringBefore(',')
        StepTarget.CurrentPosition -> currentPositionLabel
    }
    val a = steps.firstOrNull()?.let(::label) ?: return ""
    val b = steps.lastOrNull()?.let(::label) ?: return a
    // Une boucle revient a son depart : "Position actuelle - Position actuelle" ne dit rien de plus que
    // "Position actuelle", et fait deux fois plus long dans la bibliotheque.
    return if (steps.size < 2 || a == b) a else "$a - $b"
}

/**
 * De quoi interroger le geocodeur depuis une etape : l'instance reglee, la langue, le nombre de propositions
 * et l'endroit autour duquel chercher. Un porteur de valeurs plutot qu'une fonction de recherche : une
 * lambda `suspend` traversant un composable perd son caractere suspendu a la compilation, et l'appel ne
 * compile plus.
 *
 * [center], en (lon, lat), classe les propositions par proximite sans perdre la notoriete des lieux (cf.
 * Photon.rank). Nul - aucune position connue, aucune carte encore cadree -, le service repond comme avant.
 */
data class GeocodingParams(
    val base: String, val lang: String, val limit: Int, val center: Pair<Double, Double>? = null,
)

/** Discipline retenue au demarrage du planificateur, tiree des reglages. */
fun initialProfile(settings: SettingsEntity): RoutingProfile = RoutingProfile.of(settings.routingProfile)

/**
 * Le glisser-deposer d'une etape, le temps du geste.
 *
 * **L'ordre ne change qu'au lacher.** Pendant le geste, les lignes ne font que se DECALER a l'ecran : la
 * ligne tenue suit le doigt, et ses voisines glissent d'un cran pour lui faire place. Reordonner la liste a
 * chaque ligne franchie aurait relance le calcul de l'itineraire autant de fois, pour des ordres de passage
 * que personne n'a demandes.
 *
 * Hors de la composition, et sans Compose au-dela de l'etat : c'est ici que se decide ou la ligne tombe, et
 * cela se teste (cf. `StepDragTest`).
 */
internal class StepDrag {
    /** La hauteur de chaque ligne, par identifiant d'etape : c'est elle qui dit quand une voisine est franchie. */
    val heights = mutableStateMapOf<Long, Int>()

    /** Le rang de la ligne tenue, ou null hors du geste. */
    var from by mutableStateOf<Int?>(null)
        private set

    /** Le rang ou elle tomberait si l'on lachait maintenant. */
    var to by mutableStateOf<Int?>(null)
        private set

    /** Le chemin parcouru par le doigt depuis le debut du geste, en pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(index: Int) {
        if (index < 0) return
        from = index; to = index; offset = 0f
    }

    /**
     * Le doigt a bouge de [dy] : la ligne tombe au-dela de chaque voisine dont elle a franchi la MOITIE,
     * comme on s'y attend d'une liste qu'on range a la main.
     */
    fun move(dy: Float, ids: List<Long>) {
        val f = from ?: return
        offset += dy
        var t = f
        var parcouru = 0f
        if (offset > 0) {
            while (t < ids.lastIndex) {
                val h = heights[ids[t + 1]] ?: break
                if (offset < parcouru + h / 2f) break
                parcouru += h; t++
            }
        } else {
            while (t > 0) {
                val h = heights[ids[t - 1]] ?: break
                if (-offset < parcouru + h / 2f) break
                parcouru += h; t--
            }
        }
        to = t
    }

    /** Le decalage a l'ecran de la ligne [index] : le doigt pour la ligne tenue, un cran pour celles
     *  qu'elle a franchies, rien pour les autres. */
    fun shift(index: Int, ids: List<Long>): Float {
        val f = from ?: return 0f
        val t = to ?: return 0f
        if (index == f) return offset
        val h = heights[ids.getOrNull(f)]?.toFloat() ?: return 0f
        return when {
            index in (f + 1)..t -> -h
            index in t until f -> h
            else -> 0f
        }
    }

    /** Fin du geste : le deplacement a appliquer, ou null si la ligne revient a sa place. */
    fun end(): Pair<Int, Int>? {
        val f = from; val t = to
        from = null; to = null; offset = 0f
        return if (f != null && t != null && f != t) f to t else null
    }
}

/** Hauteur maximale du profil dans la bande du planificateur. */
private val PlannerChartMaxHeight = 110.dp
