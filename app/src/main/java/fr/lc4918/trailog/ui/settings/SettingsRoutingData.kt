package fr.lc4918.trailog.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.TrailogApp
import fr.lc4918.trailog.map.StyleBuilder
import fr.lc4918.trailog.map.flagAssetModel
import fr.lc4918.trailog.map.offline.TileMath
import fr.lc4918.trailog.routing.offline.BrouterDownloads
import fr.lc4918.trailog.routing.offline.BrouterZone
import fr.lc4918.trailog.routing.offline.BrouterZones
import fr.lc4918.trailog.ui.components.MapController
import fr.lc4918.trailog.ui.components.MapLibreView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Ecart entre deux zones de la liste, et entre une zone et la carte de son detail. */
private val ZoneGap = 8.dp

/**
 * Les donnees du calcul d'itineraire hors ligne : les zones telechargees, et de quoi en ajouter.
 *
 * **Un telechargement rare, et donc ici.** On emporte une zone d'itineraire une fois par voyage, quand on
 * emporte un fond de plan a chaque sortie : les deux n'ont pas leur place au meme endroit, et la case qui
 * les melait alourdissait le geste frequent pour servir le geste rare.
 *
 * Les zones sont des pays ou de grandes regions (cf. [BrouterZones]) - jamais les carres de cinq degres du
 * serveur, dont le nom ne dit rien a personne. Chaque zone est une ligne qu'on deplie pour voir son detail et
 * son emprise ; la liste des zones disponibles, dans une popup, a la meme presentation.
 */
@Composable
internal fun OfflineRoutingZones(vm: SettingsViewModel) {
    val app = LocalContext.current.applicationContext as? TrailogApp ?: return
    val data = app.brouterData
    val s by data.state.collectAsState()
    // A l'ouverture : relire le dossier, et l'index du serveur - pour le poids des zones et leurs mises a
    // jour. L'index ne se relit qu'une fois par lancement : il change une fois par semaine.
    LaunchedEffect(Unit) { data.refreshInstalled(); data.loadRemote() }
    var style by remember { mutableStateOf<StyleBuilder.Result?>(null) }
    LaunchedEffect(Unit) { style = vm.zoneMapStyle() }
    var catalogueOuvert by remember { mutableStateOf(false) }

    // Le "i" leve la confusion la plus probable : ce ne sont ni des fonds de plan ni des traces, seulement
    // ce qu'il faut au moteur pour calculer sans reseau.
    SectionTitle(
        stringResource(R.string.settings_section_offline_routing),
        info = stringResource(R.string.settings_offline_routing_info),
    )
    val telechargees = BrouterZones.all.filter { it.id in s.zones }
    if (telechargees.isEmpty()) {
        SettingsCard {
            SetRow(stringResource(R.string.routing_zones_none))
            Hint(stringResource(R.string.routing_zones_hint))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(ZoneGap)) {
            telechargees.forEach { z -> ZoneItem(z, s, data, style, downloaded = true) }
        }
    }
    Spacer(Modifier.height(ZoneGap))
    // Meme grammaire que "Gerer les fournisseurs" : une ligne qui ouvre une popup.
    SettingsCard {
        SetRow(
            stringResource(R.string.routing_zones_add),
            sub = stringResource(R.string.routing_zones_add_sub),
            onClick = { catalogueOuvert = true },
        ) {
            RowIcon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.routing_zones_add))
        }
    }

    if (catalogueOuvert) {
        Dialog(
            onDismissRequest = { catalogueOuvert = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp), color = settingsPalette.screen,
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routing_zones_available), fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold, color = settingsPalette.label,
                            modifier = Modifier.weight(1f))
                        RowIcon(Icons.Filled.Close, stringResource(R.string.action_close)) { catalogueOuvert = false }
                    }
                    Spacer(Modifier.height(8.dp))
                    val disponibles = BrouterZones.all.filter { it.id !in s.zones }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ZoneGap),
                    ) {
                        if (disponibles.isEmpty()) {
                            SettingsCard { SetRow(stringResource(R.string.routing_zones_all_downloaded)) }
                        }
                        disponibles.forEach { z -> ZoneItem(z, s, data, style, downloaded = false) }
                    }
                }
            }
        }
    }
}

/**
 * Une zone : sa ligne - icone, nom, poids, et pour une zone a telecharger le bouton qui le fait -, et, une
 * fois depliee, la carte de son detail juste dessous.
 */
@Composable
private fun ZoneItem(
    zone: BrouterZone,
    s: BrouterDownloads.State,
    data: BrouterDownloads,
    style: StyleBuilder.Result?,
    downloaded: Boolean,
) {
    val p = settingsPalette
    var deplie by remember(zone.id) { mutableStateOf(false) }
    var aSupprimer by remember(zone.id) { mutableStateOf(false) }
    val nom = zoneName(zone)
    val poids = s.weight(zone)
    // Pour une zone a telecharger, la ligne dit ce qui reste a telecharger - ce que la barre comptera. Les
    // carres de cinq degres se partagent : apres la France, les Alpes n'en demandent plus que deux sur quatre.
    val reste = if (downloaded) null else s.remaining(zone)
    val progression = s.zoneProgress(zone)
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card)
                .clickable { deplie = !deplie }
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoneIcon(zone)
            Text(nom, fontSize = 13.sp, color = p.label, modifier = Modifier.weight(1f))
            Text(
                if (downloaded) poids?.let { TileMath.formatSize(it) } ?: stringResource(R.string.routing_zone_unknown_size)
                else tailleAFaire(reste),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.accent,
            )
            if (!downloaded) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    if (progression != null) {
                        val (recu, attendu) = progression
                        if (attendu != null && attendu > 0) {
                            CircularProgressIndicator(
                                progress = { (recu.toFloat() / attendu).coerceIn(0f, 1f) },
                                modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp,
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        }
                    } else {
                        RowIcon(Icons.Filled.FileDownload, stringResource(R.string.routing_zone_download)) {
                            data.downloadZone(zone)
                        }
                    }
                }
            }
        }
        if (deplie) {
            Spacer(Modifier.height(4.dp))
            SettingsCard {
                SetRow(stringResource(R.string.routing_zone_tiles)) { ValueText("${zone.tiles.size}") }
                RowDivider()
                SetRow(stringResource(R.string.routing_zone_size)) {
                    ValueText(poids?.let { TileMath.formatSize(it) } ?: stringResource(R.string.routing_zone_unknown_size))
                }
                if (!downloaded) {
                    RowDivider()
                    SetRow(stringResource(R.string.routing_zone_to_download)) { ValueText(tailleAFaire(reste)) }
                }
                if (downloaded) {
                    s.dataDate(zone)?.let { d ->
                        RowDivider()
                        SetRow(stringResource(R.string.routing_zone_date)) { ValueText(date(d, LocalConfiguration.current.locales[0])) }
                    }
                    if (s.zoneOutdated(zone) && progression == null) {
                        Hint(stringResource(R.string.routing_zone_outdated))
                        CardAction(stringResource(R.string.routing_zone_update)) { data.downloadZone(zone) }
                    }
                }
                progression?.let { (recu, attendu) ->
                    RowDivider()
                    SetRow(
                        stringResource(R.string.routing_zone_downloading, TileMath.formatSize(recu),
                            attendu?.let { TileMath.formatSize(it) } ?: "?"),
                    ) {
                        RowIcon(Icons.Filled.Close, stringResource(R.string.action_cancel)) { data.cancelZone(zone) }
                    }
                    LinearProgressIndicator(
                        progress = { if (attendu != null && attendu > 0) (recu.toFloat() / attendu).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp).height(4.dp),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                if (zone.id in s.zoneFailed && progression == null) {
                    Hint(stringResource(R.string.routing_zone_failed))
                }
                Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { ZoneMap(zone, style) }
                if (!downloaded && progression == null) {
                    CardAction(stringResource(R.string.routing_zone_download)) { data.downloadZone(zone) }
                }
                if (downloaded && progression == null) {
                    CardAction(stringResource(R.string.action_delete)) { aSupprimer = true }
                }
                RowDivider()
                // Replier, en bas de ce qu'on vient de lire : le pouce y est deja.
                Row(
                    Modifier.fillMaxWidth().clickable { deplie = false }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ExpandLess, null, Modifier.size(18.dp), tint = p.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.routing_zone_collapse), fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = p.accent)
                }
            }
        }
    }
    if (aSupprimer) {
        AlertDialog(
            onDismissRequest = { aSupprimer = false },
            title = { Text(stringResource(R.string.routing_zone_delete_title, nom)) },
            text = { Text(stringResource(R.string.routing_zone_delete_text)) },
            confirmButton = {
                TextButton(onClick = { aSupprimer = false; data.deleteZone(zone) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { aSupprimer = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Ce qu'il reste a telecharger : une taille, rien si tout est deja la, ou l'aveu qu'on ne sait pas. */
@Composable
private fun tailleAFaire(octets: Long?): String = when {
    octets == null -> stringResource(R.string.routing_zone_unknown_size)
    octets == 0L -> stringResource(R.string.routing_zone_nothing_left)
    else -> TileMath.formatSize(octets)
}

/** Le drapeau d'un pays, ou une montagne pour un massif. */
@Composable
private fun ZoneIcon(zone: BrouterZone) {
    val flag = zone.flag
    if (flag != null) {
        AsyncImage(
            model = flagAssetModel(flag), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(22.dp, 16.dp).clip(RoundedCornerShape(3.dp)),
        )
    } else {
        Box(Modifier.size(22.dp, 16.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Terrain, null, Modifier.size(20.dp), tint = settingsPalette.subtle)
        }
    }
}

/**
 * L'emprise de la zone sur une carte, bordee du meme rouge que le trace d'une zone hors ligne : une vraie
 * carte, gestes coupes, comme l'apercu du telechargement d'un fond de plan.
 */
@Composable
private fun ZoneMap(zone: BrouterZone, style: StyleBuilder.Result?) {
    if (style == null) return
    val mini = remember(zone.id) { MapController() }
    var pret by remember(zone.id) { mutableIntStateOf(0) }
    LaunchedEffect(pret) {
        if (mini.style == null) return@LaunchedEffect
        val a = zone.area
        mini.setBboxDraw(listOf(a.west to a.south, a.east to a.north), showPoints = false)
        mini.fitTo(a.west, a.south, a.east, a.north)
    }
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)).background(settingsPalette.screen),
    ) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(), controller = mini,
            styleJson = style.styleJson, styleUrl = style.styleUrl,
            gesturesEnabled = false, destroyOnDispose = true,
            onReady = { pret++ },
        )
    }
}

@Composable
internal fun zoneName(zone: BrouterZone): String = stringResource(
    when (zone.id) {
        "fr" -> R.string.routing_zone_fr
        "es" -> R.string.routing_zone_es
        "pt" -> R.string.routing_zone_pt
        "it" -> R.string.routing_zone_it
        "ch" -> R.string.routing_zone_ch
        "at" -> R.string.routing_zone_at
        "de" -> R.string.routing_zone_de
        "be" -> R.string.routing_zone_be
        "gb" -> R.string.routing_zone_gb
        "alps" -> R.string.routing_zone_alps
        else -> R.string.routing_zone_pyrenees
    }
)

private fun date(ms: Long, locale: Locale): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
