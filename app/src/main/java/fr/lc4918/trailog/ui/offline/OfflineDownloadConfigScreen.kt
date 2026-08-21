package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapLibreView
import fr.lc4918.trailog.ui.settings.FieldRow
import fr.lc4918.trailog.ui.settings.ProvideSettingsPalette
import fr.lc4918.trailog.ui.settings.RangeSliderRow
import fr.lc4918.trailog.ui.settings.RowDivider
import fr.lc4918.trailog.ui.settings.SetRow
import fr.lc4918.trailog.ui.settings.SliderRow
import fr.lc4918.trailog.ui.settings.Hint
import fr.lc4918.trailog.ui.settings.SettingsCard
import fr.lc4918.trailog.ui.settings.SettingsSwitch
import fr.lc4918.trailog.ui.settings.SettingsTextField
import fr.lc4918.trailog.ui.settings.ValueText
import fr.lc4918.trailog.ui.settings.settingsPalette

/**
 * Étape 2 (SPEC offline_map.md section 3) : plage de zoom, statistiques live (tuiles/taille), nom de la
 * couche, gestion des erreurs. Le calcul (domaine A) et le téléchargement réel (domaine B, pas
 * encore branché) sont volontairement séparés : [onDownload] ne fait que remonter la requête validée.
 *
 * L'écran emprunte la grammaire des réglages (cf. `SettingsPalette`) : c'est le même genre d'objet, un
 * formulaire plein écran posé par-dessus la carte et non un panneau qui flotte au-dessus d'elle. Les
 * mesures et les briques viennent donc de `SettingsStyle`, sans rien redessiner ici.
 *
 * [dark] suit le thème choisi dans les réglages, [styleJson]/[styleUrl] sont ceux du fond de carte
 * courant, pour la vue d'ensemble de l'emprise : l'écran n'a accès ni aux préférences ni au style, mais
 * l'appelant les a déjà sous la main.
 */
/**
 * Largeurs proposees de chaque cote du parcours, en kilometres.
 *
 * Non equidistantes, comme les autres curseurs de l'application qui parcourent une liste : les premiers
 * crans sont ceux qu'on emploie - de quoi couvrir une erreur d'itineraire ou un detour -, les derniers
 * n'ont de sens que pour se garder une marge large, et chaque cran y coute bien plus de tuiles que le
 * precedent.
 */
private val CorridorWidthKm = listOf(0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0)

/** "500 m" en deca du kilometre, "2 km" au-dela : on ne dit pas "0,5 km". */
private fun formatKm(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()} m"
    km == Math.floor(km) -> "${km.toInt()} km"
    else -> "%.1f km".format(km)
}

@Composable
fun OfflineDownloadConfigScreen(
    bbox: Bbox,
    providerMinZoom: Int,
    providerMaxZoom: Int,
    dark: Boolean,
    styleJson: String?,
    styleUrl: String?,
    onDismiss: () -> Unit,
    onDownload: (OfflineDownloadRequest) -> Unit,
    /** La couche des points d'interet est allumee dans les reglages : sans elle, rien a proposer
     *  d'emporter - la case ne s'affiche pas. */
    poiAvailable: Boolean = false,
    /** Parcours a border, quand le telechargement suit une trace ; null pour une emprise rectangulaire. */
    corridorPoints: List<Pair<Double, Double>>? = null,
    corridorName: String = "",
) {
    val zoomBounds = providerMinZoom.toFloat()..providerMaxZoom.toFloat().coerceAtLeast(providerMinZoom.toFloat())
    // plage par défaut raisonnable : [min, min+6] bornée à la plage du provider (cf. SPEC section 3, "Détermination des niveaux").
    var zoomRange by remember {
        mutableStateOf(providerMinZoom.toFloat()..(providerMinZoom + 6).coerceAtMost(providerMaxZoom).toFloat())
    }
    var name by remember { mutableStateOf(corridorName) }
    var continueOnError by remember { mutableStateOf(false) }
    // Coche d'office quand la couche est allumee : qui l'a allumee s'en sert, et une zone emportee sans
    // ses lieux se decouvre trop tard - sur le terrain, sans reseau pour la completer.
    var withPois by remember { mutableStateOf(poiAvailable) }
    // Largeur telechargee de chaque cote du parcours. En kilometres parce que c'est l'unite dans laquelle
    // on se represente un ecart de route : "500 m autour" ne dit pas grand-chose, "un kilometre de chaque
    // cote" se voit.
    var halfWidthKm by remember { mutableStateOf<Double>(CorridorWidthKm.first()) }

    val minZ = zoomRange.start.toInt()
    val maxZ = zoomRange.endInclusive.toInt()
    // Le compte suit le mode : le couloir ne prend que ce qui borde la trace, et une meme tuile n'y compte
    // qu'une fois meme quand le parcours repasse dessus.
    val tileCount = remember(bbox, minZ, maxZ, corridorPoints, halfWidthKm) {
        if (corridorPoints != null) TileMath.totalTileCountAlong(corridorPoints, minZ, maxZ, halfWidthKm * 1000.0)
        else TileMath.totalTileCount(bbox, minZ, maxZ)
    }
    val sizeLabel = remember(tileCount) { TileMath.formatSize(TileMath.estimateSizeBytes(tileCount)) }
    val tileCountLabel = remember(tileCount) {
        String.format(java.util.Locale.ROOT, "%,d", tileCount).replace(',', ' ')
    }

    ProvideSettingsPalette(dark = dark) {
        val p = settingsPalette
        Surface(Modifier.fillMaxSize(), color = p.screen) {
            Column(Modifier.fillMaxSize()) {
                // Barre de titre : la surface blanche des cartes, comme celle des réglages, dont le fond
                // bleuté de l'écran se détache.
                Row(
                    Modifier.fillMaxWidth().background(p.card).statusBarsPadding()
                        .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.offline_config_title), fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, color = p.label, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(19.dp),
                            tint = p.label)
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                ) {
                    // Pas de titre de rubrique : chaque ligne porte son libellé, et les deux cartes
                    // séparent déjà ce qu'on règle de ce qui s'ensuit.
                    SettingsCard {
                        RangeSliderRow(
                            label = stringResource(R.string.offline_config_zoom_label),
                            value = "$minZ - $maxZ",
                            range = zoomRange, bounds = zoomBounds,
                            onRange = { zoomRange = it },
                            steps = (providerMaxZoom - providerMinZoom - 1).coerceAtLeast(0),
                        )
                        if (corridorPoints != null) {
                            RowDivider()
                            // La largeur est dans la MEME carte que le zoom : les deux commandent le poids
                            // annonce juste dessous, et les separer ferait chercher laquelle agit.
                            SliderRow(
                                label = stringResource(R.string.offline_config_width_label),
                                value = stringResource(R.string.offline_config_width_value, formatKm(halfWidthKm)),
                                fraction = CorridorWidthKm.indexOf(halfWidthKm)
                                    .coerceAtLeast(0).toFloat() / (CorridorWidthKm.size - 1),
                                steps = CorridorWidthKm.size - 2,
                                onFraction = { f ->
                                    val i = Math.round(f * (CorridorWidthKm.size - 1)).coerceIn(0, CorridorWidthKm.lastIndex)
                                    halfWidthKm = CorridorWidthKm[i]
                                },
                            )
                            Hint(stringResource(R.string.offline_config_width_hint))
                        }
                        RowDivider()
                        // Les deux estimations suivent le curseur dans la même carte : ce sont ses
                        // conséquences, pas une rubrique de plus.
                        SetRow(stringResource(R.string.offline_config_label_tiles)) { ValueText(tileCountLabel) }
                        RowDivider()
                        SetRow(stringResource(R.string.offline_config_label_size)) { ValueText(sizeLabel) }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingsCard {
                        FieldRow(stringResource(R.string.offline_config_name_label)) {
                            SettingsTextField(name, stringResource(R.string.offline_config_name_placeholder)) {
                                name = it
                            }
                        }
                        RowDivider()
                        SetRow(
                            stringResource(R.string.offline_config_continue_on_error_label),
                            sub = stringResource(R.string.offline_config_continue_on_error_desc),
                        ) {
                            SettingsSwitch(continueOnError) { continueOnError = it }
                        }
                        // Les tuiles seules laissent la couche vide precisement la ou l'on va : le cache
                        // ne retient que ce qu'on a survole CONNECTE (cf. PoiRepository.pinArea).
                        if (poiAvailable) {
                            RowDivider()
                            SetRow(
                                stringResource(R.string.offline_config_pois_label),
                                sub = stringResource(R.string.offline_config_pois_desc),
                            ) {
                                SettingsSwitch(withPois) { withPois = it }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BboxOverview(bbox, styleJson, styleUrl)
                }
                // Action principale, hors du défilement : un aplat d'accent plein, là où les boutons de
                // carte des réglages se contentent du container - c'est la seule action de l'écran.
                val enabled = name.isNotBlank() && tileCount > 0
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .height(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) p.accent else p.track)
                        // 'enabled' garantit déjà name.isNotBlank() : pas de repli nécessaire ici.
                        .clickable(enabled = enabled) {
                            onDownload(OfflineDownloadRequest(
                                bbox, minZ, maxZ, name, continueOnError,
                                corridor = corridorPoints?.let { OfflineCorridor(it, halfWidthKm * 1000.0) },
                                withPois = poiAvailable && withPois,
                            ))
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fg = if (enabled) Color.White else p.subtle
                    // Meme icone que le bouton "Telecharger" du menu lateral, d'ou l'on vient : c'est la
                    // meme action, menee a son terme.
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp), tint = fg)
                    Text(stringResource(R.string.offline_action_download), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = fg)
                }
            }
        }
    }
}

/**
 * Vue d'ensemble de l'emprise : la carte courante, cadrée sur le rectangle à télécharger et bordée du
 * même rouge que pendant le tracé.
 *
 * Une vraie carte, et non une image figée : le style du fond retenu est déjà chargé, et le cadrage
 * revient au [MapController.fitTo] qui sert partout ailleurs. Les gestes y sont coupés - c'est un
 * repère, pas une carte à explorer - et la vue est détruite en sortant, l'écran ne durant que le temps
 * d'un réglage.
 */
@Composable
private fun BboxOverview(bbox: Bbox, styleJson: String?, styleUrl: String?) {
    val mini = remember { MapController() }
    var ready by remember { mutableIntStateOf(0) }
    LaunchedEffect(ready, bbox) {
        if (mini.style == null) return@LaunchedEffect
        mini.setBboxDraw(listOf(bbox.west to bbox.south, bbox.east to bbox.north), showPoints = false)
        mini.fitTo(bbox.west, bbox.south, bbox.east, bbox.north)
    }
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))
            .background(settingsPalette.card),
    ) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(), controller = mini,
            styleJson = styleJson, styleUrl = styleUrl,
            gesturesEnabled = false, destroyOnDispose = true,
            onReady = { ready++ },
        )
    }
}
