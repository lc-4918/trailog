package fr.lc4918.trailog.ui.settings

import fr.lc4918.trailog.domain.geo.ProfileScale
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
import fr.lc4918.trailog.ui.profile.SlopeLegend
import fr.lc4918.trailog.ui.profile.SlopeRamp
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
import fr.lc4918.trailog.poi.Overpass
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
 * Discipline et preferences de trace en tete, moteur et services en queue : les premieres se rouvrent a
 * chaque sortie, les seconds se reglent une fois pour toutes et se laissent oublier. Le geocodage figure
 * parmi les services bien qu'il ne soit pas du calcul d'itineraire : chercher un lieu est le premier geste
 * de la planification, et les deux URL se reglent ensemble. Les interrupteurs qui montrent leurs boutons
 * sur la carte, eux, restent dans l'onglet "Carte".
 */
@Composable internal fun RoutesTab(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_default_discipline), tight = true)
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
    // Mode expert seulement, comme le moteur et les services plus bas (cf. SettingsEntity.expertMode).
    if (cur.expertMode) {
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
    }


    /*
     * La coloration par pente de l'itineraire CALCULE, et de lui seul : son trace sur la carte et l'aire de
     * son profil. Celle des traces se regle ailleurs - trace par trace dans le menu de chaque couche, et
     * pour leur profil dans l'onglet Carte - parce qu'on la veut souvent pour l'une et non pour l'autre.
     */
    SectionTitle(
        stringResource(R.string.settings_section_route_slope),
        info = stringResource(R.string.settings_route_slope_info),
    )
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_route_slope_line), cur.routeSlopeLine) {
            vm.save(cur.copy(routeSlopeLine = it))
        }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_route_slope_profile), cur.routeSlopeProfile) {
            vm.save(cur.copy(routeSlopeProfile = it))
        }
    }

    /*
     * Les CATEGORIES de points d'interet ne sont plus ici : elles se choisissent dans une bulle ouverte
     * depuis la carte (cf. PoiFilterBubble).
     *
     * Quatre sections depliables de cases a cocher vivaient a cet endroit, c'est-a-dire a quatre gestes de
     * la carte, dans un ecran qui recouvre justement ce qu'on essaie de regarder. Or choisir ses points
     * d'interet est un geste de TERRAIN : on cherche un camping en fin d'apres-midi, un point d'eau a la
     * montee. L'attribution de la source reste, elle : elle est due des lors qu'on affiche ces lieux, et
     * n'a pas sa place sur une bulle qu'on ouvre et referme en roulant.
     *
     * Vider le cache et effacer l'historique des lieux ont suivi vers l'onglet Systeme, rubrique CACHE :
     * ce sont des gestes d'entretien, et le seul lien qu'ils avaient avec cet onglet-ci etait la rubrique
     * qui vient d'en partir.
     */
    SectionTitle(stringResource(R.string.settings_section_poi))
    SettingsCard {
        /*
         * Completer DATAtourisme par OpenStreetMap : venu de l'onglet Carte, ou il suivait l'interrupteur
         * qui POSE le bouton des points d'interet.
         *
         * Ce n'est ni un bouton ni un geste : c'est le choix des SOURCES qu'on interroge, au meme titre
         * que le geocodeur et le moteur d'itineraire regles en tete de cet onglet. Une requete Overpass de
         * plus par chargement, et elle est longue - d'ou l'interrupteur.
         *
         * Seulement quand la couche est allumee : un reglage qui ne peut rien faire n'a rien a montrer.
         */
        if (cur.poiEnabled) {
            SwitchLine(
                stringResource(R.string.settings_sw_poi_osm), cur.poiOsmComplement,
                info = stringResource(R.string.settings_sw_poi_osm_sub),
            ) {
                // Le cache n'a pas a etre vide : chaque cellule retient la source qui l'a servie
                // (cf. PoiCellEntity), et basculer le complement ne fait que demander les autres.
                vm.save(cur.copy(poiOsmComplement = it))
            }
            RowDivider()
            /*
             * Le couloir des traces : au-dela de cette distance d'une trace AFFICHEE, un lieu n'est plus
             * montre (cf. ui/poi/PoiCorridor).
             *
             * Ce qu'il repare : le zoom de chargement etait haut - une ville et ses abords - parce qu'a
             * l'echelle d'une region la vue porte des milliers de lieux dont le service ne rend que les
             * premiers. On zoomait donc beaucoup pour voir quoi que ce soit. Le couloir retire ce qui ne
             * borde aucun trajet, et c'est lui qui rend le zoom plus bas supportable (cf.
             * PoiLoading.MIN_ZOOM).
             *
             * Une poignee de distances plutot qu'un curseur : le choix se fait entre "le long du chemin" et
             * "dans le coin", pas au decametre pres.
             *
             * Eteint par defaut, ici comme en base : c'est un filtre qui RETIRE de la carte, et une couche
             * qui montre moins qu'on ne lui a demande sans l'avoir dit se lit comme une panne.
             */
            // Le curseur parcourt les crans, et non les metres : "Sans limite" vaut zero en base, mais se
            // tient tout a DROITE, au-dela de la plus grande distance - c'est la plus large de toutes.
            val crans = PoiCorridorSteps
            val cran = crans.indexOf(cur.poiTrackCorridorM).takeIf { it >= 0 }
                ?: crans.indices.minBy { kotlin.math.abs(crans[it] - cur.poiTrackCorridorM) }
            SliderRow(
                label = stringResource(R.string.settings_label_poi_corridor),
                value = poiCorridorLabel(cur.poiTrackCorridorM),
                fraction = fractionOf(cran, 0, crans.lastIndex),
                steps = crans.size - 2,
                onFraction = {
                    vm.save(cur.copy(poiTrackCorridorM = crans[valueOf(it, 0, crans.lastIndex)]))
                },
                info = stringResource(R.string.settings_poi_corridor_hint),
                // L'attribution suit immediatement : elle appartient a ce qui est au-dessus, et l'ecart
                // normal l'en aurait detachee.
                bottomPadding = 6.dp,
            )
        }
        // Sans filet au-dessus : un filet separe deux reglages, et l'attribution n'en est pas un - c'est
        // la mention que les donnees imposent, au bas de ce qui les affiche.
        Hint(stringResource(R.string.settings_poi_attribution))
    }

    // Le lissage et l'echelle verticale ont quitte l'onglet "Carte" pour celui-ci : ils ne decrivent pas
    // ce qui s'affiche mais comment le profil se CALCULE, la ligne de partage que les deux onglets
    // revendiquent depuis toujours.
    // Lissage et echelle verticale : mode expert seulement. Ce sont des reglages de CALCUL, dont la bonne
    // valeur ne se devine pas a l'oeil, et dont les defauts conviennent a qui ne les cherche pas.
    if (cur.expertMode) {
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
                info = stringResource(R.string.settings_profile_smoothing_hint),
            )
            RowDivider()
            // Echelle verticale : Auto (0 = remplit la hauteur) ou "1 cm = N m" (metres d'altitude par cm
            // physique). Bornes choisies d'apres la hauteur du graphe (~1,6 cm). Valeurs non equidistantes,
            // donc curseur indexe, comme le lissage.
            /*
             * L'echelle verticale, en deux lignes : le REGIME, puis sa valeur.
             *
             * Les trois ne repondent pas a la meme question (cf. ProfileScale) : remplir la hauteur sans
             * dresser une pente douce en muraille, comparer des AMPLITUDES a la regle, ou comparer des PENTES.
             * L'exageration ne se propose qu'en mode expert : elle suppose qu'on sache ce qu'on compare.
             */
            val vertical = remember(cur.profileVerticalScale) { ProfileScale.parse(cur.profileVerticalScale) }
            val modes = buildList {
                add(ProfileScale.Mode.CAP)
                add(ProfileScale.Mode.M_PER_CM)
                if (cur.expertMode) add(ProfileScale.Mode.EXAGGERATION)
            }
            PickRow(
                stringResource(R.string.settings_label_vertical_scale), vertical.mode, modes,
                optionLabel = { verticalModeLabel(it) },
                info = stringResource(R.string.settings_vertical_scale_hint),
            ) { mode ->
                val v = when (mode) {
                    ProfileScale.Mode.CAP -> ProfileScale.Vertical(mode, ProfileScale.DEFAULT_CAP)
                    ProfileScale.Mode.M_PER_CM -> ProfileScale.Vertical(mode, 100.0)
                    ProfileScale.Mode.EXAGGERATION -> ProfileScale.Vertical(mode, 10.0)
                }
                vm.save(cur.copy(profileVerticalScale = ProfileScale.store(v)))
            }
            if (vertical.mode != ProfileScale.Mode.CAP) {
                RowDivider()
                val metres = vertical.mode == ProfileScale.Mode.M_PER_CM
                val crans = if (metres) VerticalScaleSteps else ExaggerationSteps
                val cran = crans.indexOf(vertical.value.toInt())
                    .let { if (it >= 0) it else crans.indices.minBy { i -> kotlin.math.abs(crans[i] - vertical.value) } }
                SliderRow(
                    if (metres) stringResource(R.string.settings_label_vertical_scale_value)
                    else stringResource(R.string.settings_label_exaggeration),
                    if (metres) stringResource(R.string.settings_vertical_scale_value, vertical.value.toInt())
                    else stringResource(R.string.settings_exaggeration_value, vertical.value.toInt()),
                    fractionOf(cran, 0, crans.lastIndex), steps = crans.size - 2,
                    onFraction = {
                        val v = crans[valueOf(it, 0, crans.lastIndex)].toDouble()
                        vm.save(cur.copy(profileVerticalScale = ProfileScale.store(vertical.copy(value = v))))
                    },
                )
            }
            // Ne remet a zero que les DEUX reglages ci-dessus, les seuls dont la bonne valeur ne se devine pas
            // a l'oeil : un lissage ou une echelle mal regles se remarquent longtemps apres, sur une autre
            // trace. Les autres reglages du profil se jugent immediatement et se defont seuls.
            CardAction(stringResource(R.string.action_reset_defaults)) {
                vm.save(cur.copy(
                    profileSmoothingM = 5,
                    profileVerticalScale = ProfileScale.store(
                        ProfileScale.Vertical(ProfileScale.Mode.CAP, ProfileScale.DEFAULT_CAP),
                    ),
                ))
            }
        }
    }

    // Dans cet onglet et non dans "Carte" : c'est de la matiere du profil qu'il s'agit, comme le lissage
    // au-dessus, et non de ce qui s'affiche. Les deux services ne s'ouvrent qu'une fois le completement
    // demande - trois champs d'URL sous un interrupteur eteint n'auraient rien a regler.
    SectionTitle(stringResource(R.string.settings_section_elevation_fill))
    SettingsCard {
        SwitchLine(
            stringResource(R.string.settings_label_fill_elevation), cur.fillMissingElevation,
            info = stringResource(R.string.settings_fill_elevation_hint),
        ) { vm.save(cur.copy(fillMissingElevation = it)) }
        // Les adresses des services : mode expert seulement. Le completement se demande d'un interrupteur,
        // et ses serveurs par defaut conviennent a qui ne les a jamais regardes.
        if (cur.fillMissingElevation && cur.expertMode) {
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

    /*
     * Moteur d'itineraire et services techniques, en fin de liste : ce sont des reglages qu'on pose une
     * fois puis qu'on oublie, a la difference de la discipline et des preferences de trace, ajustees a
     * chaque sortie. Le moteur juste avant les services, puisque c'est lui qui decide QUELLE des deux
     * adresses en dessous est la sienne.
     */
    if (cur.expertMode) {
        SectionTitle(
            stringResource(R.string.settings_section_route_engine),
            info = stringResource(R.string.settings_route_engine_hint),
        )
        SettingsCard {
            SegChips(RouteEngine.entries.map { it.key to routeEngineLabel(it) }, cur.routeEngine) {
                vm.save(cur.copy(routeEngine = it))
            }
        }
    }

    // Juste apres le moteur : ce sont les donnees de l'un des deux, et c'est la qu'on vient voir s'il
    // saura calculer sans reseau.
    OfflineRoutingZones(vm)

    if (cur.expertMode) {
        SectionTitle(
            stringResource(R.string.settings_section_services),
            info = stringResource(R.string.settings_services_hint),
        )
        SettingsCard {
            FieldRow(stringResource(R.string.settings_section_geocoding_service)) {
                SettingsTextField(cur.geocodingUrl, Photon.DEFAULT_URL) { vm.save(cur.copy(geocodingUrl = it.trim())) }
            }
            RowDivider()
            /*
             * L'instance Overpass, aupres du geocodeur et du moteur d'itineraire : c'est le troisieme service
             * tiers que l'application interroge, et le seul qui n'etait pas reglable.
             *
             * Ce n'est pas un raffinement : releve sur cinq tentatives identiques, l'instance publique a rendu
             * deux 504 et trois reponses, entre 1,6 et 9,3 s. Une instance de repli est la seule parade a la
             * disposition de l'utilisateur quand celle-ci sature.
             */
            if (cur.poiEnabled) {
                FieldRow(stringResource(R.string.settings_section_poi_osm_service)) {
                    SettingsTextField(cur.poiOsmUrl, Overpass.DEFAULT_URL) {
                        vm.save(cur.copy(poiOsmUrl = it.trim()))
                    }
                }
                RowDivider()
            }
            FieldRow(stringResource(R.string.settings_section_routing_service)) {
                // Le champ ENTIER suit le moteur retenu, valeur et gabarit : chaque moteur garde son adresse,
                // si bien que basculer pour comparer ne fait pas perdre celle de l'autre - et qu'on n'envoie
                // jamais la requete d'un moteur au serveur du voisin, faute qui echouerait en silence.
                val moteur = RouteEngine.of(cur.routeEngine)
                SettingsTextField(cur.routeUrl(moteur), Router.defaultUrlOf(moteur)) {
                    vm.save(cur.withRouteUrl(moteur, it.trim()))
                }
            }
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
        // Le profil des TRACES seulement : celui de l'itineraire calcule a son reglage, onglet Trajets.
        SwitchLine(
            stringResource(R.string.settings_profile_color_by_slope), cur.profileSlope,
            info = stringResource(R.string.settings_profile_color_by_slope_info),
        ) { vm.save(cur.copy(profileSlope = it)) }
        RowDivider()
        // La largeur des classes vaut partout ou la pente colore : profils, traces et itineraire. Le "i"
        // montre la trame elle-meme, dans la largeur choisie.
        PickRow(
            stringResource(R.string.settings_slope_classes), cur.slopeClassTenths, SlopeRamp.ClassSteps,
            optionLabel = { slopeClassLabel(it) },
            infoContent = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SlopeLegend(cur.slopeClassTenths, fontSp = 14, vertical = true, length = 360.dp)
                }
            },
        ) { vm.save(cur.copy(slopeClassTenths = it)) }
    }

    SectionTitle(stringResource(R.string.settings_section_title_line_info))
    SettingsCard {
        // Un interrupteur par info, comme les champs du tableau de bord : on en allume autant qu'on veut,
        // et les puces le disaient mal (cf. InfoSwitchRows).
        // Les noms ENTIERS, ceux des bulles du profil, et non les abreviations des colonnes : "D+" tient
        // dans une colonne de graphe, pas dans une liste de reglages ou rien ne presse.
        InfoSwitchRows(
            listOf(
                "dist" to stringResource(R.string.info_name_distance),
                "asc" to stringResource(R.string.info_name_ascent),
                "desc" to stringResource(R.string.info_name_descent),
                "dur" to stringResource(R.string.info_name_duration),
                "min" to stringResource(R.string.info_name_alt_min),
                "max" to stringResource(R.string.info_name_alt_max),
            ),
            cur.titleInfos,
        ) { vm.save(cur.copy(titleInfos = it)) }
    }

    SectionTitle(stringResource(R.string.settings_label_current_point))
    SettingsCard {
        InfoSwitchRows(
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
        CardAction(stringResource(R.string.action_reset_defaults)) {
            vm.save(cur.copy(
                profAxisFont = 9, profAxisBold = false,
                profTitleFont = 16, profTitleBold = true,
                profBarFont = 11, profBarBold = false,
                profLegendFont = 9, profLegendBold = false,
                profCursorFont = 11, profCursorBold = false,
            ))
        }
    }
}

/**
 * Reglage "Mises a jour" : mode Auto/Manuel, et en Manuel seulement le bouton de verification immediate.
 *
 * **Le bouton est SOUS la ligne, aligne a droite**, et non au bout de celle-ci. Il y etait, et la ligne
 * n'en voulait pas : son contenu de fin partage sa largeur avec le libelle, qui a le poids. Une troisieme
 * commande comprimait donc "Mises a jour" jusqu'a le faire disparaitre, et la ligne enflait en hauteur
 * pour loger le texte replie - un grand vide au-dessus de trois commandes sans intitule.
 *
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

    SetRow(stringResource(R.string.settings_label_updates), info = stringResource(R.string.settings_updates_info)) {
        SettingsChip(stringResource(R.string.update_mode_auto), cur.updateCheckMode != "manual") {
            vm.save(cur.copy(updateCheckMode = "auto"))
        }
        SettingsChip(stringResource(R.string.update_mode_manual), cur.updateCheckMode == "manual") {
            vm.save(cur.copy(updateCheckMode = "manual"))
        }
    }
    if (UpdateManager.isSupported && cur.updateCheckMode == "manual") {
        RowTrailingBelow {
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

/**
 * Bornes du couloir des traces, en metres : de zero - sans limite - a vingt kilometres.
 *
 * **Un curseur et non une liste de valeurs**, et le pas de cinq cents metres en est la raison : entre "le
 * long du chemin" et "dans le coin" il y a un continuum, et l'endroit ou l'on veut s'arreter depend du
 * pays qu'on traverse - deux kilometres en ville, dix dans une vallee vide. Une poignee de valeurs
 * imposees obligeait a choisir la moins fausse.
 *
 * **Zero retire la limite**, et se tient tout a DROITE du curseur, au-dela de vingt kilometres : c'est la
 * plus large des distances, et on la cherchait a l'oppose, apres le plus grand chiffre. On explore une
 * region ou l'on n'a aucune trace ouverte, et l'on veut voir ce qu'elle porte ; sans cette position, il
 * fallait laisser une distance qui ne voulait rien dire - et le couloir empeche desormais la requete
 * elle-meme (cf. PoiCorridor.crosses), ce qui viderait la carte pour de bon.
 */
private const val MaxPoiCorridorM = 20_000

/** Le pas du curseur : cinq cents metres. Au-dessous, on reglerait la precision d'un trace, pas la
 *  distance a laquelle on accepte de faire un detour. */
private const val PoiCorridorStepM = 500

/** Les crans du curseur, de gauche a droite : 500 m a 20 km, puis zero - sans limite. */
private val PoiCorridorSteps: List<Int> =
    (1..MaxPoiCorridorM / PoiCorridorStepM).map { it * PoiCorridorStepM } + 0

/** "Sans limite", "500 m", "5 km" : le metre sous le kilometre, le kilometre au-dela, avec sa decimale
 *  quand elle compte - "2,5 km" est une distance qu'on se represente, "2500 m" beaucoup moins. */
@Composable
private fun poiCorridorLabel(m: Int): String = when {
    m <= 0 -> stringResource(R.string.settings_poi_corridor_off)
    m < 1_000 -> "$m m"
    m % 1_000 == 0 -> "${m / 1_000} km"
    else -> "${m / 1_000},${(m % 1_000) / 100} km"
}

/** Metres d'altitude par centimetre proposes : de la trace de vallee au massif entier. */
private val VerticalScaleSteps = listOf(50, 100, 150, 200, 250, 300, 500, 800, 1200)

/** Exagerations proposees : au-dela d'une vingtaine, le relief ne se lit plus, il se devine. */
private val ExaggerationSteps = listOf(2, 5, 10, 15, 25, 50)

/** Libelle traduit d'un regime d'echelle verticale (cf. ProfileScale.Mode). */
@Composable private fun verticalModeLabel(m: ProfileScale.Mode): String = stringResource(
    when (m) {
        ProfileScale.Mode.CAP -> R.string.settings_vertical_scale_auto
        ProfileScale.Mode.M_PER_CM -> R.string.settings_vertical_scale_absolute
        ProfileScale.Mode.EXAGGERATION -> R.string.settings_label_exaggeration
    }
)

/** "0,5 %", "1 %", "2,5 %", "5 %" : la largeur d'une classe de pente, ecrite comme on la lit. */
@Composable internal fun slopeClassLabel(tenths: Int): String {
    // Le separateur decimal de la langue de l'application, et non celle du telephone.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val valeur = if (tenths % 10 == 0) "${tenths / 10}" else String.format(locale, "%.1f", tenths / 10.0)
    return stringResource(R.string.settings_slope_class_format, valeur)
}
