package fr.lc4918.trailog.ui.offline

import fr.lc4918.trailog.map.offline.CorridorShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
import fr.lc4918.trailog.map.offline.OfflineCorridor
import fr.lc4918.trailog.map.offline.OfflineDownloadRequest
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
 * Largeurs proposees de chaque cote du parcours, en kilometres : de 1 a 20, par kilometre. En deca, la
 * carte s'arretait au bord du chemin, et le moindre detour sortait de ce qu'on avait emporte.
 */
private val CorridorWidthKm = (1..20).map { it.toDouble() }

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
    // Decochee d'office : c'est une requete de plus a des services tiers, et la carte se telecharge tres
    // souvent sans qu'on ait besoin de ses lieux. Qui les veut coche la case.
    var withPois by remember { mutableStateOf(false) }
    // Largeur telechargee de chaque cote du parcours. En kilometres parce que c'est l'unite dans laquelle
    // on se represente un ecart de route : "500 m autour" ne dit pas grand-chose, "un kilometre de chaque
    // cote" se voit.
    var halfWidthKm by remember { mutableStateOf<Double>(CorridorWidthKm.first()) }

    val minZ = zoomRange.start.toInt()
    val maxZ = zoomRange.endInclusive.toInt()
    // Le compte suit le mode : le couloir ne prend que ce qui borde la trace, et une meme tuile n'y compte
    // qu'une fois meme quand le parcours repasse dessus.
    //
    // Hors du fil de l'interface, et un instant apres le dernier cran du curseur : sur une longue trace aux
    // grands zooms, le compte se chiffre en millions de tuiles, et le faire a chaque cran figeait l'ecran
    // jusqu'a ce qu'Android propose de fermer l'application. Null seulement avant le premier calcul : pendant
    // un recalcul, la valeur d'avant reste affichee, et le bouton garde son etat - les faire passer a "..." et
    // au gris a chaque cran les faisait clignoter tant que le curseur bougeait.
    var tileCount by remember { mutableStateOf<Long?>(null) }
    // Le calcul en cours se voit a la place de la taille, par un rond qui tourne : la valeur d'avant reste
    // gardee - le bouton en depend -, mais l'afficher laisserait croire qu'elle vaut pour le nouveau reglage.
    var calcul by remember { mutableStateOf(true) }
    LaunchedEffect(bbox, minZ, maxZ, corridorPoints, halfWidthKm) {
        calcul = true
        delay(300)
        tileCount = withContext(Dispatchers.Default) {
            if (corridorPoints != null) TileMath.totalTileCountAlong(corridorPoints, minZ, maxZ, halfWidthKm * 1000.0)
            else TileMath.totalTileCount(bbox, minZ, maxZ)
        }
        calcul = false
    }
    val sizeLabel = tileCount?.let { TileMath.formatSize(TileMath.estimateSizeBytes(it)) } ?: "..."

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
                        // L'estimation suit le curseur dans la même carte : c'est sa conséquence, pas
                        // une rubrique de plus. Le nombre de tuiles n'est plus affiché : le poids seul dit
                        // ce que ça coûte.
                        SetRow(stringResource(R.string.offline_config_label_size)) {
                            if (calcul) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = p.accent)
                            else ValueText(sizeLabel)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingsCard {
                        FieldRow(stringResource(R.string.offline_config_name_label)) {
                            SettingsTextField(name, stringResource(R.string.offline_config_name_placeholder)) {
                                name = it
                            }
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
                    BboxOverview(bbox, styleJson, styleUrl, corridorPoints, halfWidthKm * 1000.0)
                }
                // Action principale, hors du défilement : un aplat d'accent plein, là où les boutons de
                // carte des réglages se contentent du container - c'est la seule action de l'écran.
                val enabled = name.isNotBlank() && (tileCount ?: 0L) > 0
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .height(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) p.accent else p.track)
                        // 'enabled' garantit déjà name.isNotBlank() : pas de repli nécessaire ici.
                        .clickable(enabled = enabled) {
                            onDownload(OfflineDownloadRequest(
                                // Toujours : une tuile manquante laisse un trou dans la carte, quand
                                // l'arret jetait tout ce qui avait ete telecharge. Ce n'est plus un choix.
                                bbox, minZ, maxZ, name, continueOnError = true,
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
 * Vue d'ensemble de ce qu'on va telecharger : le rectangle, ou la trace et sa zone tampon.
 *
 * Une vraie carte, et non une image figee : le style du fond retenu est deja charge, et le cadrage revient
 * au [MapController.fitTo] qui sert partout ailleurs. Les gestes y sont coupes - c'est un repere, pas une
 * carte a explorer.
 *
 * **Le long d'une trace, on voit ce qu'on emporte** : la trace, et autour d'elle la zone tampon de la
 * largeur reglee, qui suit le curseur. Pour l'un comme pour l'autre, un bouton au bas de la miniature ouvre
 * la meme carte en grand, ou l'on peut zoomer pour verifier qu'un col ou un village a l'ecart est bien dedans.
 */
@Composable
private fun BboxOverview(
    bbox: Bbox, styleJson: String?, styleUrl: String?,
    corridor: List<Pair<Double, Double>>? = null, radiusM: Double = 0.0,
) {
    var enGrand by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))
            .background(settingsPalette.card),
    ) {
        EmpriseMap(bbox, styleJson, styleUrl, corridor, radiusM, interactive = false)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.85f))
                .clickable { enGrand = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Fullscreen, stringResource(R.string.offline_thumb_expand),
                Modifier.size(20.dp), tint = Color.Black)
        }
    }
    if (enGrand) {
        Dialog(onDismissRequest = { enGrand = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f).clip(RoundedCornerShape(16.dp))
                    .background(settingsPalette.card),
            ) {
                EmpriseMap(bbox, styleJson, styleUrl, corridor, radiusM, interactive = true)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.9f))
                        .clickable { enGrand = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(20.dp),
                        tint = Color.Black)
                }
            }
        }
    }
}

/**
 * La carte de l'emprise, cadree sur ce qu'on telecharge - la zone tampon comprise, le long d'une trace.
 *
 * La trace est allegee a deux mille points au plus : elle ne sert qu'a se voir, et une EuroVelo en porte des
 * dizaines de milliers.
 */
@Composable
private fun EmpriseMap(
    bbox: Bbox, styleJson: String?, styleUrl: String?,
    corridor: List<Pair<Double, Double>>?, radiusM: Double, interactive: Boolean,
) {
    val mini = remember { MapController() }
    var ready by remember { mutableIntStateOf(0) }
    val allegee = remember(corridor) {
        corridor?.let { c -> val pas = c.size / 2000 + 1; c.filterIndexed { i, _ -> i % pas == 0 || i == c.lastIndex } }
    }
    // Le cadrage ne suit que l'emprise, pas la largeur : deplacer le curseur ne doit pas faire sauter la
    // carte, seulement epaissir la zone tampon.
    LaunchedEffect(ready, bbox) {
        if (mini.style == null) return@LaunchedEffect
        if (allegee == null) {
            mini.setBboxDraw(listOf(bbox.west to bbox.south, bbox.east to bbox.north), showPoints = false)
            mini.fitTo(bbox.west, bbox.south, bbox.east, bbox.north)
        } else {
            // L'emprise elargie de la zone tampon : sans cela, ses bords sortiraient du cadre.
            val dLat = radiusM / 111_320.0
            val dLon = dLat / kotlin.math.cos(Math.toRadians((bbox.south + bbox.north) / 2)).coerceAtLeast(0.1)
            mini.fitTo(bbox.west - dLon, bbox.south - dLat, bbox.east + dLon, bbox.north + dLat)
        }
    }
    // La zone tampon se calcule hors du fil de l'interface, comme le poids : sur une longue trace, elle se
    // chiffre en milliers de bandes.
    LaunchedEffect(ready, allegee, radiusM) {
        if (mini.style == null || allegee == null) return@LaunchedEffect
        val bandes = withContext(Dispatchers.Default) { CorridorShape.bands(allegee, radiusM) }
        mini.setCorridorPreview(allegee, bandes)
    }
    MapLibreView(
        modifier = Modifier.fillMaxSize(), controller = mini,
        styleJson = styleJson, styleUrl = styleUrl,
        gesturesEnabled = interactive, destroyOnDispose = true,
        onReady = { ready++ },
    )
}
