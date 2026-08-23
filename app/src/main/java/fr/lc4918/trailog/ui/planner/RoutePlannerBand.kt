package fr.lc4918.trailog.ui.planner

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.profile.SlopeLegend
import fr.lc4918.trailog.ui.profile.TrackInfoColumns
import fr.lc4918.trailog.ui.profile.routeInfos
import fr.lc4918.trailog.ui.settings.RoutingProfilePicker
import kotlinx.coroutines.delay

/** Hauteur minimale de la bande : le double de celle des barres de consigne existantes, qui n'affichent
 *  qu'une ligne de texte. Le planificateur porte au moins la discipline et deux champs. */
private val BandMinHeight = 96.dp

/** Fond de la bande. Opaque a 94 % : la carte transparait juste assez pour qu'on garde le sentiment de la
 *  survoler, sans nuire a la lecture des champs. */
private const val BandAlpha = 0.94f

/** Gabarit du champ d'une etape, partage entre le champ de saisie et l'affichage replie qui le remplace
 *  au repos : les deux doivent avoir exactement la meme allure, sans quoi la ligne sauterait au focus.
 *
 *  La hauteur est IMPOSEE aux deux, et non laissee a leur contenu : le champ de saisie se mesure sur sa
 *  ligne de texte, l'affichage replie sur sa bordure, et les etapes remplies se collaient les unes aux
 *  autres la ou les vides gardaient un jour entre elles. */
private val FieldShape = RoundedCornerShape(4.dp)
private val FieldHeight = 40.dp
private val FieldTextPadding = 16.dp

/**
 * Titre de la bande, et texte des etapes.
 *
 * Le titre passe de 13 a 16 : c'est le titre d'un ecran, au meme rang que celui d'un profil de trace, et
 * il se lisait plus petit que le nom des lieux saisis dessous. Les etapes font le chemin inverse - un nom
 * de lieu complet ("Grenoble, Isere, France") tient rarement sur une ligne de champ, et chaque point de
 * moins en fait entrer davantage avant l'abreviation.
 *
 * L'indice et la valeur partagent la MEME taille : ce sont deux etats du meme texte, et les voir changer
 * de corps au moment de la saisie ferait sauter la ligne.
 */
private const val BandTitleSp = 16f
private const val FieldTextSp = 13f

/** Ce qui separe deux champs, en toute circonstance : pose autour de chacun, donc compte double entre
 *  deux voisins. */
private val FieldGap = 3.dp

/** Le bouton d'ajout : sa cible tactile, et le dessin qu'elle porte. */
private val AddButtonSize = 32.dp
private val AddIconSize = 20.dp

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
 */
@Composable
fun RoutePlannerBand(
    state: RoutePlannerState,
    imperial: Boolean,
    settings: SettingsEntity,
    lastLabelInsetPx: Float,
    maxHeight: Dp,
    onPickCurrentPosition: (PlannerStep) -> Unit,
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = BandAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            BandHeader(
                recomputing = state.recomputing,
                onCollapse = { state.collapse(true) },
                onClose = { state.close() },
            )
            RoutingProfilePicker(state.profile) { state.chooseProfile(it) }
            StepList(state, onPickCurrentPosition, sensorEnabled, geocoding, history, onPlaceChosen,
                onPlaceForgotten,
                onImport, onDownload,
                Modifier.weight(1f, fill = false).padding(top = 10.dp))
            ResultsZone(state, imperial, settings, lastLabelInsetPx)
        }
    }
}

/** En-tete : reduire a gauche, fermer a l'oppose. */
@Composable
private fun BandHeader(
    recomputing: Boolean,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ExpandMore, stringResource(R.string.planner_collapse), Modifier.size(20.dp))
            }
            Text(stringResource(R.string.planner_title), fontSize = BandTitleSp.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            // Le recalcul se signale ICI, dans une ligne de hauteur fixe, et non en remplacant la zone
            // resultats : celle-ci porte le profil, et la bande se replierait a chaque changement d'etape.
            if (recomputing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Les etapes, le bouton d'ajout sous la derniere, et les deux sorties du parcours a l'oppose.
 *
 * Le bouton `+` est aligne dans la marge gauche, a l'aplomb du bord des champs et non dedans : il
 * n'appartient a aucune etape, il en cree une de plus.
 *
 * Enregistrer et telecharger partagent cette ligne parce qu'elle est la seule libre : les mettre sous les
 * totaux les eloignait de la composition du parcours, alors qu'ils la terminent.
 */
@Composable
private fun StepList(
    state: RoutePlannerState,
    onPickCurrentPosition: (PlannerStep) -> Unit,
    sensorEnabled: Boolean,
    geocoding: GeocodingParams,
    history: PlannerHistory,
    onPlaceChosen: (GeocodePlace) -> Unit,
    onPlaceForgotten: (GeocodePlace) -> Unit,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        state.steps.forEachIndexed { i, step ->
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
                sensorEnabled = sensorEnabled,
                geocoding = geocoding,
                history = history,
                onPlaceChosen = onPlaceChosen,
                onPlaceForgotten = onPlaceForgotten,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                if (state.canAddStep) {
                    // Decale de la moitie de sa marge interne : c'est le DESSIN du plus qui doit tomber
                    // sur le bord des champs, non la cible tactile qui l'entoure.
                    IconButton(
                        onClick = { state.addStep() },
                        modifier = Modifier.offset(x = -(AddButtonSize - AddIconSize) / 2).size(AddButtonSize),
                    ) {
                        Icon(Icons.Filled.Add, stringResource(R.string.planner_add_step),
                            Modifier.size(AddIconSize))
                    }
                }
                Spacer(Modifier.weight(1f))
                // Les deux sorties n'ont de sens qu'une fois le parcours calcule : avant, elles n'auraient
                // rien a ecrire. Memes boutons que ceux de l'en-tete du menu lateral, qui font deja ces
                // gestes-la sur les couches.
                if (state.route is RouteState.Done) {
                    BandAction(Icons.Outlined.Save, stringResource(R.string.planner_import_layer), onImport)
                    BandAction(Icons.Outlined.FileDownload, stringResource(R.string.planner_download_gpx), onDownload)
                }
            }
        }
    }
}

/** Une sortie du parcours : meme gabarit et meme gris que les actions de l'en-tete du menu lateral. */
@Composable
private fun BandAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Une etape : son champ, ses deux fleches de reordonnancement, sa suppression, puis ses propositions. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun StepRow(
    state: RoutePlannerState,
    step: PlannerStep,
    index: Int,
    placeholder: String,
    onPickCurrentPosition: (PlannerStep) -> Unit,
    sensorEnabled: Boolean,
    geocoding: GeocodingParams,
    history: PlannerHistory,
    onPlaceChosen: (GeocodePlace) -> Unit,
    onPlaceForgotten: (GeocodePlace) -> Unit,
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
        var found = Photon.search(geocoding.base, q, geocoding.lang, geocoding.limit)
        if (found == null) {
            delay(300)
            found = Photon.search(geocoding.base, q, geocoding.lang, geocoding.limit)
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
    Column(Modifier.bringIntoViewRequester(bringIntoView)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = FieldGap).height(FieldHeight)) {
                if (step.target != null && !focused && !wantsFocus) {
                    // Etape choisie et champ au repos : on montre le libelle TRONQUE. Un champ de saisie
                    // ne sait pas abreger - il fait defiler son texte et le coupe net au bord, sans dire
                    // qu'il en reste. Le champ reel reprend sa place des qu'on le touche.
                    Box(
                        Modifier.fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outline, FieldShape)
                            .clickable { wantsFocus = true }
                            .padding(horizontal = FieldTextPadding),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(shown, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = FieldTextSp.sp)
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
                        shape = FieldShape,
                        modifier = Modifier.fillMaxSize().focusRequester(focusRequester)
                            .onFocusChanged {
                                focused = it.isFocused
                                state.setEditing(step, it.isFocused)
                                if (it.isFocused) state.focus(step)
                            },
                        textStyle = LocalTextStyle.current.copy(fontSize = FieldTextSp.sp),
                        placeholder = {
                            Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = FieldTextSp.sp)
                        },
                    )
                }
                // Attente et effacement POSES SUR le champ, et non dans son emplacement d'icone de fin :
                // le texte garde ainsi la meme marge a gauche qu'a droite, et court sous eux, que leur
                // transparence laisse lire. Le spinner se tient a gauche de la croix : l'interrogation
                // porte sur ce qu'on vient de taper, elle appartient au champ et non a la liste dessous.
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (step.searching) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                    }
                    if (shown.isNotEmpty()) {
                        // Pastille et croix prises aux roles du theme, et non a deux couleurs fixes
                        // choisies par theme : elles s'echangent d'elles-memes en sombre, la pastille
                        // restant le contraire du fond sur lequel elle se pose.
                        Box(
                            Modifier.size(15.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    CircleShape)
                                .clickable { state.clearStep(step) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, stringResource(R.string.planner_clear_step),
                                Modifier.size(11.dp), tint = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                // Fleches plutot qu'une poignee de glissement : le geste est plus sur au doigt sur une
                // liste courte, et il ne rentre pas en concurrence avec le defilement de la zone.
                // Empilees, et non cote a cote : monter et descendre sont deux sens d'un meme axe, et les
                // poser l'un au-dessus de l'autre le dit sans qu'on ait a lire les icones.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { state.moveStep(index, -1) }, enabled = index > 0,
                        modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.planner_move_up),
                            Modifier.size(18.dp))
                    }
                    IconButton(onClick = { state.moveStep(index, 1) }, enabled = index < state.steps.lastIndex,
                        modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.planner_move_down),
                            Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { state.removeStep(index) }, enabled = state.steps.size > 2,
                    modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.DeleteOutline, stringResource(R.string.planner_remove_step), Modifier.size(18.dp))
                }
            }
        }
        val vierge = focused && step.untouched
        val rappels = if (vierge) history.places.filter { !state.usesPlace(it.label, step) } else emptyList()
        val suggesting = focused && (step.searching || step.results.isNotEmpty() ||
            step.failed || (sensorEnabled && vierge) || rappels.isNotEmpty())
        LaunchedEffect(suggesting, step.results.size, step.searching) {
            if (suggesting) bringIntoView.bringIntoView()
        }
        // Position actuelle : proposee au focus tant que rien n'a ete tape, et non offerte par un bouton
        // permanent. Elle n'est utile qu'a l'instant ou l'on remplit un champ vide.
        // Seulement si la localisation est allumee dans le telephone : sans position connue, le calcul
        // echouerait sur un "Aucun itineraire" que rien n'expliquerait. Une proposition qu'on ne peut pas
        // honorer ne vaut rien. L'AFFICHAGE du repere sur la carte, lui, n'entre pas en compte - la
        // position se demande au capteur le temps du calcul, sans rien poser sur la carte.
        // Et seulement si elle ne sert pas DEJA ailleurs : partir d'ou l'on est pour y revenir donne un
        // trajet de longueur nulle, et la proposer une seconde fois invitait a le demander.
        if (sensorEnabled && vierge && !state.usesCurrentPosition) {
            SuggestionRow(
                label = stringResource(R.string.planner_current_position),
                icon = true,
                onClick = { onPickCurrentPosition(step); settle() },
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
                Modifier.fillMaxWidth().clickable { step.askRetry() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.planner_search_failed), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.planner_retry), fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        step.results.forEach { place ->
            SuggestionRow(label = place.label, icon = false,
                onClick = { onPlaceChosen(place); state.choose(step, StepTarget.Place(place)); settle() })
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) {
            Icon(image, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (icon) 6.dp else 0.dp).weight(1f))
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
                IconButton(onClick = onForget, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Filled.Close, stringResource(R.string.planner_forget_place),
                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        RouteState.Failed -> Text(stringResource(R.string.geocode_no_route),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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
            // Memes colonnes, memes tailles et meme reglage que les infos d'une trace sous son profil :
            // c'est la meme lecture, sur un parcours qu'on vient de calculer plutot que sur un fichier.
            TrackInfoColumns(
                routeInfos(stats, if (state.zoomed) partSeconds else r.seconds, state.zoomed, imperial),
                fontSp = settings.profBarFont,
                bold = settings.profBarBold,
                modifier = Modifier.fillMaxWidth(),
            )
            // Le profil est replie derriere son libelle : il occupe a lui seul la moitie de la hauteur
            // disponible, et il n'a d'interet qu'une fois le trajet compose. La zone resultats se reduit
            // donc a une ligne de totaux et a cette bascule, tant qu'on ne demande pas le relief.
            Row(
                Modifier.fillMaxWidth().clickable { state.toggleProfile() }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (state.profileVisible) R.string.planner_hide_profile
                        else R.string.planner_show_profile
                    ),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                )
                // Retour a la vue complete : sur cette ligne parce qu'il concerne le profil, et non le
                // parcours. Bouton a part DANS une ligne cliquable : son propre clic l'emporte sur celui
                // de la ligne, qui continue d'ouvrir et de fermer le profil partout ailleurs.
                if (state.zoomed) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        IconButton(onClick = { state.resetZoom() }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Fullscreen, stringResource(R.string.planner_zoom_out),
                                Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                // Le chevron montre le SENS DU GESTE a venir, non l'etat courant : profil replie, il
                // pointe vers le bas pour dire qu'il va se deployer ; deploye, vers le haut pour le
                // refermer.
                Icon(
                    if (state.profileVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, Modifier.size(18.dp),
                )
            }
            if (state.profileShown) {
                if (settings.profileSlope && settings.profileSlopeLegend) {
                    SlopeLegend(stats.maxAbsSlope, settings.profLegendFont,
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        bold = settings.profLegendBold)
                }
                Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                    ElevationProfile(
                        samples = samples, stats = stats,
                        grid = settings.profileGrid,
                        slope = settings.profileSlope,
                        lineColor = MaterialTheme.colorScheme.primary,
                        axisFontSp = settings.profAxisFont,
                        axisBold = settings.profAxisBold,
                        cursorX = state.cursor,
                        onScrub = { state.tapProfile(it) },
                        onZoom = { scale, fraction -> state.zoomBy(scale, fraction, r.track.samples.size) },
                        // Double-tap : un grossissement franc au point vise, la ou le pincement dose.
                        onDoubleTap = { fraction -> state.zoomBy(2f, fraction, r.track.samples.size) },
                        lastLabelInsetPx = lastLabelInsetPx,
                        verticalScaleMPerCm = settings.profileVerticalScaleMPerCm,
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                    )
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
    return if (steps.size < 2) a else "$a - $b"
}

/**
 * De quoi interroger le geocodeur depuis une etape : l'instance reglee, la langue, et le nombre de
 * propositions. Un porteur de valeurs plutot qu'une fonction de recherche : une lambda `suspend` traversant
 * un composable perd son caractere suspendu a la compilation, et l'appel ne compile plus.
 */
data class GeocodingParams(val base: String, val lang: String, val limit: Int)

/** Discipline retenue au demarrage du planificateur, tiree des reglages. */
fun initialProfile(settings: SettingsEntity): RoutingProfile = RoutingProfile.of(settings.routingProfile)
