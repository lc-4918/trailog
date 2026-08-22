package fr.lc4918.trailog.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.db.routePrefs
import fr.lc4918.trailog.data.db.routeUrl
import fr.lc4918.trailog.data.db.withRoutePrefs
import fr.lc4918.trailog.data.db.withRouteUrl
import fr.lc4918.trailog.domain.model.GroupCheck
import fr.lc4918.trailog.domain.model.HillPref
import fr.lc4918.trailog.domain.model.PlannerHistory
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.SurfacePref
import fr.lc4918.trailog.domain.model.WayPref
import fr.lc4918.trailog.elevation.IgnElevation
import fr.lc4918.trailog.elevation.OpenTopo
import fr.lc4918.trailog.geocode.Photon
import fr.lc4918.trailog.routing.Router
import fr.lc4918.trailog.ui.poi.poiCategoryLabel
import fr.lc4918.trailog.update.ReleaseInfo
import fr.lc4918.trailog.update.UpdateCheck
import fr.lc4918.trailog.update.UpdateFlow
import fr.lc4918.trailog.update.UpdateManager
import kotlinx.coroutines.launch

/**
 * L'onglet Trajets : profil altimetrique, moteurs d'itineraire, points d'interet, mises a jour.
 *
 * Les libelles des enumerations de trace - discipline, voie, relief, revetement - sont ici et non dans le
 * domaine : ce sont des textes d'interface, traduits, et le domaine ne connait pas les ressources.
 */

/**
 * Onglet "Itineraires" : comment le calcul se fait, non ce qui s'affiche.
 *
 * Les deux services y figurent separement bien que le geocodage ne soit pas du calcul d'itineraire :
 * chercher un lieu est le premier geste de la planification, et les deux URL se reglent ensemble. Les
 * interrupteurs qui montrent leurs boutons sur la carte, eux, restent dans l'onglet "Carte".
 */
@Composable internal fun RoutesTab(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_services), tight = true)
    SettingsCard {
        FieldRow(stringResource(R.string.settings_section_geocoding_service)) {
            SettingsTextField(cur.geocodingUrl, Photon.DEFAULT_URL) { vm.save(cur.copy(geocodingUrl = it.trim())) }
        }
        RowDivider()
        FieldRow(stringResource(R.string.settings_section_routing_service)) {
            // Le champ ENTIER suit le moteur retenu, valeur et gabarit : chaque moteur garde son adresse,
            // si bien que basculer pour comparer ne fait pas perdre celle de l'autre - et qu'on n'envoie
            // jamais la requete d'un moteur au serveur du voisin, faute qui echouerait en silence.
            val moteur = RouteEngine.of(cur.routeEngine)
            SettingsTextField(cur.routeUrl(moteur), Router.defaultUrlOf(moteur)) {
                vm.save(cur.withRouteUrl(moteur, it.trim()))
            }
        }
        Hint(stringResource(R.string.settings_services_hint))
    }

    /*
     * Le moteur se regle sous l'URL qu'il commande, et en puces plutot qu'en menu : les deux valeurs
     * restent lisibles d'un coup d'oeil, et basculer de l'une a l'autre est UN tap. C'est la raison
     * d'etre du reglage - comparer deux moteurs sur le meme trajet, sans rien changer d'autre.
     */
    SectionTitle(stringResource(R.string.settings_section_route_engine))
    SettingsCard {
        SegChips(RouteEngine.entries.map { it.key to routeEngineLabel(it) }, cur.routeEngine) {
            vm.save(cur.copy(routeEngine = it))
        }
        Hint(stringResource(R.string.settings_route_engine_hint))
    }

    SectionTitle(stringResource(R.string.settings_section_default_discipline))
    SettingsCard {
        RoutingProfilePicker(RoutingProfile.of(cur.routingProfile)) { vm.save(cur.copy(routingProfile = it.key)) }
    }

    /*
     * Ce que le calcul doit privilégier - et qu'il ignorait jusqu'ici, d'où cette rubrique : sans elle, un
     * VTC part sur la départementale quand la voie verte longe la même vallée.
     *
     * Discipline par discipline, et non un réglage unique : on ne demande pas la même chose à un vélo de
     * route qu'à un VTT, et la mesure l'a confirmé - accepter les chemins fait gagner au VTT, et fait
     * perdre au vélo de route, qu'un long détour éloigne alors des voies vertes qu'il aurait prises.
     *
     * La discipline réglée ici n'est PAS celle du dessus : celle du dessus est le défaut du planificateur,
     * celle-ci désigne le jeu de préférences qu'on modifie. On peut donc régler le VTT sans rouler en VTT.
     * Elle s'ouvre néanmoins sur la discipline par défaut, la plus probable, et la suit si on la change.
     */
    SectionTitle(stringResource(R.string.settings_section_route_prefs))
    var tuned by remember(cur.routingProfile) { mutableStateOf(RoutingProfile.of(cur.routingProfile)) }
    SettingsCard {
        RoutingProfilePicker(tuned) { tuned = it }
        RowDivider()
        val prefs = cur.routePrefs(tuned)
        PickRow(stringResource(R.string.settings_label_route_ways), prefs.ways,
            WayPref.entries, optionLabel = { wayPrefLabel(it) }) {
            vm.save(cur.withRoutePrefs(tuned, prefs.copy(ways = it)))
        }
        RowDivider()
        PickRow(stringResource(R.string.settings_label_route_hills), prefs.hills,
            HillPref.entries, optionLabel = { hillPrefLabel(it) }) {
            vm.save(cur.withRoutePrefs(tuned, prefs.copy(hills = it)))
        }
        RowDivider()
        PickRow(stringResource(R.string.settings_label_route_surface), prefs.surface,
            SurfacePref.entries, optionLabel = { surfacePrefLabel(it) }) {
            vm.save(cur.withRoutePrefs(tuned, prefs.copy(surface = it)))
        }
    }

    /*
     * Vider l'historique des lieux du planificateur.
     *
     * Il se remplit TOUT SEUL de ce qu'on consulte - un lieu cherche, un point d'interet ouvert, l'adresse
     * d'un appui long - et pas seulement de ce qu'on tape. Ce qui s'inscrit sans qu'on le demande doit
     * pouvoir s'effacer sans reinitialiser tous les reglages : c'est la moindre des choses pour une
     * application dont l'argument est que tout reste sur l'appareil.
     *
     * Le compte en sous-titre, et la ligne eteinte quand il est a zero : un bouton qui n'a rien a effacer
     * ne doit pas repondre comme s'il avait fait quelque chose. Sans confirmation - huit lieux se
     * reconstituent en une promenade, la demander pour cela serait du ceremonial.
     */
    val lieux = PlannerHistory.of(cur.plannerHistory).places
    SectionTitle(stringResource(R.string.settings_section_planner_history))
    SettingsCard {
        SetRow(
            stringResource(R.string.settings_clear_planner_history),
            sub = if (lieux.isEmpty()) stringResource(R.string.settings_planner_history_empty)
            else stringResource(R.string.settings_planner_history_count, lieux.size),
            onClick = if (lieux.isEmpty()) null else ({ vm.save(cur.copy(plannerHistory = "")) }),
            role = Role.Button,
        ) {
            Icon(
                Icons.Filled.DeleteOutline, null,
                tint = if (lieux.isEmpty()) settingsPalette.subtle else settingsPalette.accent,
            )
        }
        Hint(stringResource(R.string.settings_planner_history_hint))
    }

    /*
     * Points d'interet : un groupe par section depliable, ses categories en cases a cocher.
     *
     * Dans l'onglet Trajets et non dans "Carte" : l'interrupteur qui pose le BOUTON sur la carte est une
     * commande d'ecran et reste la-bas, mais ce qu'on affiche dessous decrit un trajet - ou dormir, ou
     * manger, ou reparer un velo - au meme titre que les preferences de trace juste au-dessus.
     */
    SectionTitle(stringResource(R.string.settings_section_poi))
    val filtres = PoiFilters.of(cur.poiHiddenCategories, cur.poiBikeGroups)
    fun sauve(f: PoiFilters) =
        vm.save(cur.copy(poiHiddenCategories = f.hiddenCsv(), poiBikeGroups = f.bikeCsv()))
    SettingsCard {
        Hint(stringResource(R.string.settings_poi_hint))
    }
    PoiGroup.entries.forEach { groupe ->
        var deplie by rememberSaveable(groupe) { mutableStateOf(false) }
        SettingsCard {
            SetRow(
                poiGroupLabel(groupe),
                sub = stringResource(R.string.settings_poi_group_count,
                    PoiCategory.of(groupe).count { filtres.isShown(it) }, PoiCategory.of(groupe).size),
                onClick = { deplie = !deplie },
            ) {
                Icon(
                    if (deplie) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null, tint = settingsPalette.subtle,
                )
            }
            if (deplie) {
                RowDivider()
                // "Tout selectionner" en tete, avec son etat a trois valeurs : coche, vide, ou entre les
                // deux quand une partie seulement du groupe est retenue.
                CheckLine(
                    stringResource(R.string.settings_poi_select_all), filtres.groupState(groupe),
                ) { sauve(filtres.toggleGroup(groupe)) }
                PoiCategory.of(groupe).forEach { cat ->
                    CheckLine(
                        poiCategoryLabel(cat),
                        if (filtres.isShown(cat)) GroupCheck.ALL else GroupCheck.NONE,
                    ) { sauve(filtres.toggle(cat)) }
                }
                RowDivider()
                // Le filtre velo est par GROUPE : on veut des hebergements qui accueillent les cyclistes
                // sans exiger la meme chose des points d'eau.
                SwitchLine(
                    stringResource(R.string.settings_poi_bike_only), filtres.isBikeOnly(groupe),
                    sub = stringResource(R.string.settings_poi_bike_only_sub),
                ) { sauve(filtres.toggleBike(groupe)) }
            }
        }
    }
    /*
     * Vider le cache, sous les categories et non au-dessus : c'est un geste d'entretien, qu'on ne fait pas
     * en decouvrant l'ecran.
     *
     * Le meme gabarit que l'historique du planificateur - un compte, une corbeille, grisee quand il n'y a
     * rien - et pour la meme raison : ce qui s'inscrit sans qu'on le demande doit pouvoir se retirer. Un
     * cache de points d'interet garde aussi ce que le service a rendu de FAUX, et rien d'autre ne permet
     * alors de le forcer a redemander.
     */
    val poiEnCache by vm.poiCached.collectAsState()
    val poiEmportes by vm.poiPinned.collectAsState()
    SettingsCard {
        SetRow(
            stringResource(R.string.settings_clear_poi_cache),
            sub = if (poiEnCache == 0) stringResource(R.string.settings_poi_cache_empty)
            else stringResource(R.string.settings_poi_cache_count, poiEnCache),
            onClick = if (poiEnCache == 0) null else ({ vm.clearPoiCache() }),
            role = Role.Button,
        ) {
            Icon(
                Icons.Filled.DeleteOutline, null,
                tint = if (poiEnCache == 0) settingsPalette.subtle else settingsPalette.accent,
            )
        }
        // Ce que le bouton ne touche pas, dit seulement quand il y a quelque chose a ne pas toucher.
        if (poiEmportes > 0) Hint(stringResource(R.string.settings_poi_cache_pinned, poiEmportes))
        Hint(stringResource(R.string.settings_poi_cache_hint))
    }
    SettingsCard {
        Hint(stringResource(R.string.settings_poi_attribution))
    }

    // Le lissage et l'echelle verticale ont quitte l'onglet "Carte" pour celui-ci : ils ne decrivent pas
    // ce qui s'affiche mais comment le profil se CALCULE, la ligne de partage que les deux onglets
    // revendiquent depuis toujours.
    SectionTitle(stringResource(R.string.settings_section_profile_calc))
    SettingsCard {
        // Valeurs autorisees : 1 m, puis pas de 5 jusqu'a 100 m (souvent ~1 point GPS tous les 80 m). Non
        // equidistantes (1->5) : le curseur parcourt un index et rend la valeur, plutot qu'une plage
        // continue. Un reglage existant hors liste est ramene a la valeur la plus proche.
        val smoothing = remember { listOf(1) + (5..100 step 5).toList() }
        val smoothingIdx = smoothing.indexOf(cur.profileSmoothingM).let { exact ->
            if (exact >= 0) exact
            else smoothing.indices.minByOrNull { kotlin.math.abs(smoothing[it] - cur.profileSmoothingM) } ?: 0
        }
        SliderRow(
            stringResource(R.string.settings_label_smoothing), "${cur.profileSmoothingM} m",
            fractionOf(smoothingIdx, 0, smoothing.lastIndex), steps = smoothing.size - 2,
            onFraction = { vm.save(cur.copy(profileSmoothingM = smoothing[valueOf(it, 0, smoothing.lastIndex)])) },
        )
        Hint(stringResource(R.string.settings_profile_smoothing_hint))
        RowDivider()
        // Echelle verticale : Auto (0 = remplit la hauteur) ou "1 cm = N m" (metres d'altitude par cm
        // physique). Bornes choisies d'apres la hauteur du graphe (~1,6 cm). Valeurs non equidistantes,
        // donc curseur indexe, comme le lissage.
        val scales = remember { listOf(0, 50, 100, 150, 200, 250, 300, 500, 800, 1200) }
        val scaleIdx = scales.indexOf(cur.profileVerticalScaleMPerCm).let { if (it >= 0) it else 0 }
        SliderRow(
            stringResource(R.string.settings_label_vertical_scale),
            if (cur.profileVerticalScaleMPerCm <= 0) stringResource(R.string.settings_vertical_scale_auto)
            else stringResource(R.string.settings_vertical_scale_value, cur.profileVerticalScaleMPerCm),
            fractionOf(scaleIdx, 0, scales.lastIndex), steps = scales.size - 2,
            onFraction = { vm.save(cur.copy(profileVerticalScaleMPerCm = scales[valueOf(it, 0, scales.lastIndex)])) },
        )
        Hint(stringResource(R.string.settings_vertical_scale_hint))
        // Ne remet a zero que les DEUX reglages ci-dessus, les seuls dont la bonne valeur ne se devine pas
        // a l'oeil : un lissage ou une echelle mal regles se remarquent longtemps apres, sur une autre
        // trace. Les autres reglages du profil se jugent immediatement et se defont seuls.
        CardAction(stringResource(R.string.action_reset_defaults)) {
            vm.save(cur.copy(profileSmoothingM = 5, profileVerticalScaleMPerCm = 0))
        }
    }

    // Dans cet onglet et non dans "Carte" : c'est de la matiere du profil qu'il s'agit, comme le lissage
    // au-dessus, et non de ce qui s'affiche. Les deux services ne s'ouvrent qu'une fois le completement
    // demande - trois champs d'URL sous un interrupteur eteint n'auraient rien a regler.
    SectionTitle(stringResource(R.string.settings_section_elevation_fill))
    SettingsCard {
        SwitchLine(
            stringResource(R.string.settings_label_fill_elevation), cur.fillMissingElevation,
        ) { vm.save(cur.copy(fillMissingElevation = it)) }
        Hint(stringResource(R.string.settings_fill_elevation_hint))
        if (cur.fillMissingElevation) {
            RowDivider()
            FieldRow(stringResource(R.string.settings_label_elevation_france)) {
                SettingsTextField(cur.elevationIgnUrl, IgnElevation.DEFAULT_URL) {
                    vm.save(cur.copy(elevationIgnUrl = it.trim()))
                }
            }
            RowDivider()
            FieldRow(stringResource(R.string.settings_label_elevation_world)) {
                SettingsTextField(cur.elevationWorldUrl, OpenTopo.DEFAULT_URL) {
                    vm.save(cur.copy(elevationWorldUrl = it.trim()))
                }
            }
            RowDivider()
            FieldRow(stringResource(R.string.settings_label_elevation_world_key)) {
                SettingsTextField(cur.elevationWorldKey, OpenTopo.DEFAULT_KEY) {
                    vm.save(cur.copy(elevationWorldKey = it.trim()))
                }
            }
            Hint(stringResource(R.string.settings_services_hint))
        }
    }
}

/**
 * Reglages d'apparence du profil altimetrique, communs au profil d'une trace et a celui d'un itineraire
 * planifie. Composable a part, et non un onglet : ils tiennent desormais a la fin de l'onglet "Carte",
 * l'onglet qu'ils occupaient etant rendu au calcul d'itineraire.
 */
@Composable internal fun ProfileSettings(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_display), tight = true)
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_profile_grid), cur.profileGrid) { vm.save(cur.copy(profileGrid = it)) }
        RowDivider()
        // Pas d'interrupteur pour la legende des pentes : elle se demande d'un "i" pose sur le bandeau du
        // profil, la ou elle sert, et se referme du meme geste (cf. SlopeLegendButton).
        SwitchLine(stringResource(R.string.settings_profile_color_by_slope), cur.profileSlope) { vm.save(cur.copy(profileSlope = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_profile_remaining), cur.profileRemaining,
            sub = stringResource(R.string.settings_profile_remaining_sub),
        ) { vm.save(cur.copy(profileRemaining = it)) }
    }

    SectionTitle(stringResource(R.string.settings_section_title_line_info))
    SettingsCard {
        SetRow(stringResource(R.string.settings_label_title_line))
        InfoChipRow(
            listOf("dist" to stringResource(R.string.chip_distance), "asc" to stringResource(R.string.chip_ascent),
                "desc" to stringResource(R.string.chip_descent), "dur" to stringResource(R.string.chip_duration),
                "min" to stringResource(R.string.chip_alt_min), "max" to stringResource(R.string.chip_alt_max)),
            cur.titleInfos, scrollable = true,
        ) { vm.save(cur.copy(titleInfos = it)) }
        RowDivider()
        SetRow(stringResource(R.string.settings_label_current_point))
        InfoChipRow(
            listOf("dist" to stringResource(R.string.chip_distance), "ele" to stringResource(R.string.chip_altitude),
                "slope" to stringResource(R.string.chip_slope), "time" to stringResource(R.string.chip_time)),
            cur.cursorInfos,
        ) { vm.save(cur.copy(cursorInfos = it)) }
    }

    SectionTitle(stringResource(R.string.settings_section_font_sizes))
    SettingsCard {
        StepperLine(stringResource(R.string.font_axes), cur.profAxisFont, 7, 28,
            bold = cur.profAxisBold, onBold = { vm.save(cur.copy(profAxisBold = it)) }) { vm.save(cur.copy(profAxisFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_title), cur.profTitleFont, 7, 28,
            bold = cur.profTitleBold, onBold = { vm.save(cur.copy(profTitleBold = it)) }) { vm.save(cur.copy(profTitleFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_title_bar_info), cur.profBarFont, 7, 28,
            bold = cur.profBarBold, onBold = { vm.save(cur.copy(profBarBold = it)) }) { vm.save(cur.copy(profBarFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.settings_profile_slope_legend), cur.profLegendFont, 7, 28,
            bold = cur.profLegendBold, onBold = { vm.save(cur.copy(profLegendBold = it)) }) { vm.save(cur.copy(profLegendFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_cursor_point), cur.profCursorFont, 7, 28,
            bold = cur.profCursorBold, onBold = { vm.save(cur.copy(profCursorBold = it)) }) { vm.save(cur.copy(profCursorFont = it)) }
    }
}

/**
 * Reglage "Mises a jour" : mode Auto/Manuel, et en Manuel seulement le bouton de verification immediate,
 * pose sur la meme ligne que les puces de mode (d'ou sa hauteur et son libelle calques dessus, sans quoi
 * la ligne deborderait en largeur sur un ecran etroit).
 * En build debug, la verification est inoperante (cf. UpdateManager.isSupported) : on le dit plutot que de
 * laisser un bouton qui ne repondrait jamais rien.
 */
@Composable internal fun ColumnScopeMarker.UpdatesRow(cur: SettingsEntity, vm: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<ReleaseInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val upToDate = stringResource(R.string.update_none_available)
    val failed = stringResource(R.string.update_check_failed)

    SetRow(stringResource(R.string.settings_label_updates)) {
        SettingsChip(stringResource(R.string.update_mode_auto), cur.updateCheckMode != "manual") {
            vm.save(cur.copy(updateCheckMode = "auto"))
        }
        SettingsChip(stringResource(R.string.update_mode_manual), cur.updateCheckMode == "manual") {
            vm.save(cur.copy(updateCheckMode = "manual"))
        }
        if (UpdateManager.isSupported && cur.updateCheckMode == "manual") {
            if (checking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                InlineButton(stringResource(R.string.update_action_check)) {
                    checking = true; message = null
                    scope.launch {
                        when (val r = UpdateManager.check()) {
                            is UpdateCheck.Available -> found = r.release
                            UpdateCheck.UpToDate -> message = upToDate
                            UpdateCheck.Failed -> message = failed
                        }
                        checking = false
                    }
                }
            }
        }
    }
    // En build debug, la verification est inoperante (cf. UpdateManager.isSupported) : on le dit plutot
    // que de laisser un bouton qui ne repondrait jamais rien.
    if (!UpdateManager.isSupported) Hint(stringResource(R.string.update_unsupported_debug))
    message?.let { Hint(it) }
    UpdateFlow(release = found, onDone = { found = null })
}

/** Choix de la discipline : les cinq icônes en ligne, la retenue en pastille pleine. Un select déroulant
 *  aurait caché quatre choix sur cinq derrière un tap, pour un réglage qu'on change au gré de la sortie. */
/** Ligne des disciplines. Partagee avec le planificateur, qui doit offrir exactement le meme choix, dans
 *  la meme forme : c'est le meme reglage, une fois par defaut et une fois pour le trajet en cours. */
@Composable internal fun RoutingProfilePicker(current: RoutingProfile, onSelect: (RoutingProfile) -> Unit) {
    // Marge egale sur les quatre cotes, et non un simple decalage vers le bas : la rangee se pose ainsi au
    // milieu de la carte qui la porte, sans blanc en trop au-dessus de la pastille retenue, et les
    // disciplines des extremites detachent leurs quatre angles arrondis comme celles du milieu - collees au
    // bord, leurs deux angles exterieurs disparaissaient dans l'arrondi de la carte.
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RoutingProfile.entries.forEach { p ->
            val selected = p == current
            val label = routingProfileLabel(p)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    // Aplat d'accent translucide plutot que le couple primary/onPrimary : `onPrimary`
                    // appartient au jeu par defaut de Material, que l'application ne redefinit pas - il
                    // est violet. Le meme bleu, pose a 18 %, dit "retenu" dans les deux themes.
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(routingProfileIcon(p), label, modifier = Modifier.size(24.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                Text(label, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2,
                    textAlign = TextAlign.Center,
                    color = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
        }
    }
}

/** Nom du moteur d'itinéraire. Pas de chaîne traduite : ce sont deux noms propres. */
@Composable private fun routeEngineLabel(e: RouteEngine): String = when (e) {
    RouteEngine.VALHALLA -> "Valhalla"
    RouteEngine.BROUTER -> "BRouter"
}

/** Libellé traduit d'une discipline d'itinéraire. */
@Composable fun routingProfileLabel(p: RoutingProfile): String = stringResource(
    when (p) {
        RoutingProfile.ROAD_BIKE -> R.string.profile_road_bike
        RoutingProfile.GRAVEL -> R.string.profile_gravel
        RoutingProfile.HYBRID_BIKE -> R.string.profile_hybrid_bike
        RoutingProfile.MOUNTAIN_BIKE -> R.string.profile_mtb
        RoutingProfile.FOOT -> R.string.profile_foot
    }
)

/**
 * Libellés des trois préférences de tracé, en mots de sortie et non en options de moteur.
 *
 * La position centrale des trois porte le MÊME libellé, "Sans préférence", parce qu'elle fait la même
 * chose : ne rien demander au service (cf. Valhalla.costingOptionsOf). Trois formulations différentes
 * laisseraient croire à trois comportements.
 */
@Composable private fun wayPrefLabel(p: WayPref): String = stringResource(
    when (p) {
        WayPref.ROADS -> R.string.route_ways_roads
        WayPref.BALANCED -> R.string.route_pref_none
        WayPref.SOFT -> R.string.route_ways_soft
    }
)

@Composable private fun hillPrefLabel(p: HillPref): String = stringResource(
    when (p) {
        HillPref.AVOID -> R.string.route_hills_avoid
        HillPref.BALANCED -> R.string.route_pref_none
        HillPref.SEEK -> R.string.route_hills_seek
    }
)

@Composable private fun surfacePrefLabel(p: SurfacePref): String = stringResource(
    when (p) {
        SurfacePref.PAVED -> R.string.route_surface_paved
        SurfacePref.BALANCED -> R.string.route_pref_none
        SurfacePref.ROUGH -> R.string.route_surface_rough
    }
)

/** Icône d'une discipline. Le libellé l'accompagne toujours : les cinq pictogrammes se distinguent bien
 *  entre eux, mais aucun ne dit à lui seul "gravel" plutôt que "VTC". */
private fun routingProfileIcon(p: RoutingProfile): ImageVector = when (p) {
    RoutingProfile.ROAD_BIKE -> Icons.Filled.DirectionsBike
    RoutingProfile.GRAVEL -> Icons.Filled.Grain
    RoutingProfile.HYBRID_BIKE -> Icons.Filled.PedalBike
    RoutingProfile.MOUNTAIN_BIKE -> Icons.Filled.Terrain
    RoutingProfile.FOOT -> Icons.Filled.DirectionsWalk
}
