package fr.lc4918.trailog.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.MaxGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MaxMapButtonSizeDp
import fr.lc4918.trailog.data.db.MaxOffTrackAlertM
import fr.lc4918.trailog.data.db.MinGpsMarkerSizeDp
import fr.lc4918.trailog.data.db.MinMapButtonSizeDp
import fr.lc4918.trailog.data.db.MinOffTrackAlertM
import fr.lc4918.trailog.data.db.OffTrackAlertStepM
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.model.BubblePosition
import fr.lc4918.trailog.domain.model.GpsMarkerStyle
import fr.lc4918.trailog.domain.model.PoiGroup
import fr.lc4918.trailog.location.alertSoundTitle
import fr.lc4918.trailog.ui.components.ColorPickerDialog
import kotlinx.coroutines.launch

/**
 * L'onglet Carte : ce qui se voit sur la carte et ce qui s'y allume.
 *
 * Les fonctions qui ne servent qu'a lui vivent avec lui - l'alerte d'eloignement, le repere de position,
 * la position des infobulles.
 */

/**
 * Onglet "Carte" : ce qui s'affiche sur la carte et par-dessus elle.
 *
 * Trois parties, dans l'ordre ou l'on decouvre la carte : ce qui s'y commande, ce qu'on y pose, puis ce
 * qui decrit le relief parcouru. Les deux dernieres portent un titre de groupe ; la premiere n'en a pas,
 * elle commence en tete de l'onglet ou il n'y a encore rien dont la distinguer.
 *
 * Les libelles des interrupteurs ont perdu leur "Afficher" : la rubrique s'appelle "Boutons et gestes",
 * et le repeter sept fois de suite ne disait rien de plus.
 */
@Composable internal fun MapTab(cur: SettingsEntity, vm: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_section_map_controls), tight = true)
    SettingsCard {
        // Éteindre la localisation éteint l'alerte d'éloignement avec elle : celle-ci n'a que la position
        // pour matière, et sa cloche resterait sur la carte sans rien pour allumer ni couper le capteur.
        // Le lien inverse est tenu par le réglage de l'alerte, plus bas.
        SwitchLine(stringResource(R.string.settings_sw_gps_button), cur.showGpsButton) {
            vm.save(cur.copy(showGpsButton = it, offTrackAlertEnabled = it && cur.offTrackAlertEnabled))
        }
        RowDivider()
        // Juste sous la localisation, dont il ne dit que le comportement : il ne déclenche rien de son côté
        // et ne coûte rien capteur éteint, d'où l'absence du lien croisé que l'alerte, elle, impose.
        SwitchLine(
            stringResource(R.string.settings_sw_follow_position), cur.mapFollowPosition,
            sub = stringResource(R.string.settings_sw_follow_position_sub),
        ) { vm.save(cur.copy(mapFollowPosition = it)) }
        RowDivider()
        // Au meme endroit que le suivi, dont il ne dit lui aussi que le comportement. Pas de lien croise
        // avec la localisation : le drapeau n'est pose que pendant le suivi (cf. MainScreen), et ne coute
        // donc rien capteur eteint.
        SwitchLine(
            stringResource(R.string.settings_sw_keep_screen_on), cur.keepScreenOn,
            sub = stringResource(R.string.settings_sw_keep_screen_on_sub),
        ) { vm.save(cur.copy(keepScreenOn = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_geocoding), cur.geocodingEnabled) { vm.save(cur.copy(geocodingEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_planner), cur.routePlannerEnabled) { vm.save(cur.copy(routePlannerEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_measure), cur.trackMeasureEnabled) { vm.save(cur.copy(trackMeasureEnabled = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_sw_poi), cur.poiEnabled,
            sub = stringResource(R.string.settings_sw_poi_sub),
        ) { vm.save(cur.copy(poiEnabled = it)) }
        // Sous la couche qu'il nuance, et seulement quand elle est allumee : un reglage qui ne peut rien
        // faire n'a rien a montrer.
        if (cur.poiEnabled) {
            RowDivider()
            SwitchLine(
                stringResource(R.string.settings_sw_poi_osm), cur.poiOsmComplement,
                sub = stringResource(R.string.settings_sw_poi_osm_sub),
            ) { vm.save(cur.copy(poiOsmComplement = it)) }
        }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_sw_track_edit), cur.trackEditEnabled,
            sub = stringResource(R.string.settings_sw_track_edit_sub),
        ) { vm.save(cur.copy(trackEditEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_scale), cur.showScale) { vm.save(cur.copy(showScale = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_rotation), cur.rotateGesturesEnabled) { vm.save(cur.copy(rotateGesturesEnabled = it)) }
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_buttons_bg), cur.controlButtonsBackground) { vm.save(cur.copy(controlButtonsBackground = it)) }
        RowDivider()
        // Le curseur ne va pas au-dela du bouton Material plein : plus grand, il ne depasserait pas sa
        // zone tactile, il deborderait dessus.
        SliderRow(
            label = stringResource(R.string.settings_label_map_button_size),
            value = "${cur.mapButtonSizeDp} dp",
            fraction = fractionOf(cur.mapButtonSizeDp, MinMapButtonSizeDp, MaxMapButtonSizeDp),
            steps = MaxMapButtonSizeDp - MinMapButtonSizeDp - 1,
            onFraction = { vm.save(cur.copy(mapButtonSizeDp = valueOf(it, MinMapButtonSizeDp, MaxMapButtonSizeDp))) },
        )
    }

    OffTrackAlertSettings(cur, vm)

    GpsMarkerSettings(cur, vm)

    SectionTitle(stringResource(R.string.settings_section_basemap_control))
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_label_show_button), cur.showBasemapControlButton) {
            vm.save(cur.copy(showBasemapControlButton = it))
        }
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_panel_width), "${cur.basemapControlWidthPct} %",
            fractionOf(cur.basemapControlWidthPct, 20, 90),
            { vm.save(cur.copy(basemapControlWidthPct = valueOf(it, 20, 90))) },
        )
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_panel_opacity), "${cur.basemapControlOpacityPct} %",
            fractionOf(cur.basemapControlOpacityPct, 30, 100),
            { vm.save(cur.copy(basemapControlOpacityPct = valueOf(it, 30, 100))) },
        )
        CardAction(stringResource(R.string.action_reset_defaults)) {
            vm.save(cur.copy(basemapControlWidthPct = 70, basemapControlOpacityPct = 90))
        }
    }

    GroupTitle(stringResource(R.string.settings_group_pois))
    SectionTitle(stringResource(R.string.settings_section_markers), tight = true)
    SettingsCard {
        StepperLine(stringResource(R.string.settings_label_marker_size), cur.markerSize, 16, 80) {
            vm.save(cur.copy(markerSize = it))
        }
    }
    SectionTitle(stringResource(R.string.settings_section_bubbles))
    SettingsCard {
        StepperLine(stringResource(R.string.settings_font_size), cur.bubbleFont, 7, 28,
            bold = cur.bubbleBold, onBold = { vm.save(cur.copy(bubbleBold = it)) }) { vm.save(cur.copy(bubbleFont = it)) }
        RowDivider()
        StepperLine(stringResource(R.string.font_title), cur.bubbleTitleFont, 7, 28,
            bold = cur.bubbleTitleBold, onBold = { vm.save(cur.copy(bubbleTitleBold = it)) }) { vm.save(cur.copy(bubbleTitleFont = it)) }
        RowDivider()
        PickRow(
            stringResource(R.string.settings_label_bubble_position),
            BubblePosition.of(cur.bubblePosition), BubblePosition.entries,
            optionLabel = { bubblePositionLabel(it) },
        ) { vm.save(cur.copy(bubblePosition = it.key)) }
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_opacity), "${cur.bubbleOpacityPct} %",
            fractionOf(cur.bubbleOpacityPct, 30, 100),
            { vm.save(cur.copy(bubbleOpacityPct = valueOf(it, 30, 100))) },
        )
    }

    GroupTitle(stringResource(R.string.settings_group_elevation_profile))
    ProfileSettings(cur, vm)
}

/** Nom traduit d'un groupe de points d'interet. */
@Composable internal fun poiGroupLabel(g: PoiGroup): String = stringResource(
    when (g) {
        PoiGroup.LODGING -> R.string.poi_group_lodging
        PoiGroup.FOOD -> R.string.poi_group_food
        PoiGroup.LEISURE -> R.string.poi_group_leisure
        PoiGroup.PRACTICAL -> R.string.poi_group_practical
    }
)

/**
 * Alerte d'eloignement : la cloche sur la carte, l'ecart qui la declenche, et le son qui l'accompagne.
 *
 * L'interrupteur allume AUSSI le bouton de localisation, sans le demander : une alerte se nourrit de la
 * position, et la cloche sans le bouton GPS serait un capteur qu'on ne peut ni voir ni couper. Le lien
 * inverse est tenu la-haut, dans la ligne du bouton GPS - eteindre l'un eteint l'autre.
 *
 * Le son ne montre son choix que s'il est actif : une ligne de reglage qui ne sert a rien vaut mieux
 * absente que grisee.
 */
@Composable private fun OffTrackAlertSettings(cur: SettingsEntity, vm: SettingsViewModel) {
    val ctx = LocalContext.current
    // Le selecteur de sonnerie du systeme : c'est lui qui liste les notifications du telephone et les fait
    // ecouter. En livrer un dans l'application reviendrait a redessiner un ecran que l'utilisateur connait.
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        vm.save(cur.copy(offTrackAlertSoundUri = picked?.toString().orEmpty()))
    }
    val soundLabel = remember(cur.offTrackAlertSoundUri) { alertSoundTitle(ctx, cur.offTrackAlertSoundUri) }
    val defaultSoundLabel = stringResource(R.string.settings_off_track_sound_default)

    SectionTitle(stringResource(R.string.settings_section_off_track))
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_label_show_button), cur.offTrackAlertEnabled) {
            vm.save(cur.copy(offTrackAlertEnabled = it, showGpsButton = it || cur.showGpsButton))
        }
        RowDivider()
        // Le curseur va par pas de dix metres : au metre pres, on reglerait la precision du capteur, pas
        // la distance a laquelle on veut etre prevenu.
        SliderRow(
            label = stringResource(R.string.settings_label_off_track_distance),
            value = "${cur.offTrackAlertDistanceM} m",
            fraction = fractionOf(cur.offTrackAlertDistanceM / OffTrackAlertStepM,
                MinOffTrackAlertM / OffTrackAlertStepM, MaxOffTrackAlertM / OffTrackAlertStepM),
            steps = (MaxOffTrackAlertM - MinOffTrackAlertM) / OffTrackAlertStepM - 1,
            onFraction = {
                val steps = valueOf(it, MinOffTrackAlertM / OffTrackAlertStepM, MaxOffTrackAlertM / OffTrackAlertStepM)
                vm.save(cur.copy(offTrackAlertDistanceM = steps * OffTrackAlertStepM))
            },
        )
        RowDivider()
        SwitchLine(stringResource(R.string.settings_sw_off_track_sound), cur.offTrackAlertSound) {
            vm.save(cur.copy(offTrackAlertSound = it))
        }
        if (cur.offTrackAlertSound) {
            RowDivider()
            SetRow(
                stringResource(R.string.settings_label_off_track_sound),
                onClick = { soundPicker.launch(ringtonePickerIntent(ctx, cur.offTrackAlertSoundUri)) },
            ) {
                ValueText(soundLabel ?: defaultSoundLabel)
            }
        }
        Hint(stringResource(R.string.settings_off_track_hint))
    }
}

/** Intention du selecteur de sonnerie, limite aux notifications, ouvert sur le son deja retenu. */
private fun ringtonePickerIntent(ctx: android.content.Context, current: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.settings_label_off_track_sound))
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            current.takeIf { it.isNotBlank() }?.toUri())
    }

/**
 * Repere de position GPS : son symbole, sa couleur, sa taille.
 *
 * Juste apres les boutons de la carte, et non avec les marqueurs des points d'interet : ce repere n'est
 * pas un point pose sur la carte mais l'etat du capteur qu'allume le bouton juste au-dessus.
 *
 * La couleur affichee est la couleur EFFECTIVE - celle reglee, ou celle propre au symbole tant qu'on n'a
 * rien choisi -, si bien que la pastille dit toujours ce qu'on verra sur la carte.
 */
@Composable private fun GpsMarkerSettings(cur: SettingsEntity, vm: SettingsViewModel) {
    val marker = GpsMarkerStyle.of(cur.gpsMarkerStyle)
    val color = cur.gpsMarkerColor.takeIf { it.isNotBlank() } ?: marker.defaultColor
    var pickColor by remember { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.settings_section_gps_marker))
    SettingsCard {
        // Changer de symbole rend sa couleur ET sa taille au nouveau : chacun a les siennes (bleu et 20 dp
        // pour la puce, rouge et 30 dp pour les fleches, qui doivent montrer une direction), et les heriter
        // du precedent donnerait une fleche bleue et minuscule a qui vient de quitter la puce sans avoir
        // rien choisi.
        PickRow(
            stringResource(R.string.settings_label_gps_marker_style),
            marker, GpsMarkerStyle.entries, optionLabel = { gpsMarkerLabel(it) },
        ) {
            vm.save(cur.copy(
                gpsMarkerStyle = it.key, gpsMarkerColor = "", gpsMarkerSizeDp = it.defaultSizeDp,
            ))
        }
        RowDivider()
        SetRow(stringResource(R.string.settings_label_gps_marker_color), onClick = { pickColor = true }) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color.toColorInt())))
        }
        RowDivider()
        SliderRow(
            label = stringResource(R.string.settings_label_gps_marker_size),
            value = "${cur.gpsMarkerSizeDp} dp",
            fraction = fractionOf(cur.gpsMarkerSizeDp, MinGpsMarkerSizeDp, MaxGpsMarkerSizeDp),
            steps = MaxGpsMarkerSizeDp - MinGpsMarkerSizeDp - 1,
            onFraction = { vm.save(cur.copy(gpsMarkerSizeDp = valueOf(it, MinGpsMarkerSizeDp, MaxGpsMarkerSizeDp))) },
        )
        if (marker.oriented) Hint(stringResource(R.string.settings_gps_marker_heading_hint))
    }
    if (pickColor) {
        ColorPickerDialog(
            current = color,
            onPick = { vm.save(cur.copy(gpsMarkerColor = it)); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
}

/** Libelle traduit d'un symbole de position. */
@Composable private fun gpsMarkerLabel(m: GpsMarkerStyle): String = stringResource(
    when (m) {
        GpsMarkerStyle.DOT -> R.string.gps_marker_dot
        GpsMarkerStyle.ARROW_OUTLINE -> R.string.gps_marker_arrow_outline
        GpsMarkerStyle.ARROW_FILLED -> R.string.gps_marker_arrow_filled
        GpsMarkerStyle.CROSSHAIR -> R.string.gps_marker_crosshair
    }
)

/** Libellé traduit d'un placement d'infobulle. */
@Composable private fun bubblePositionLabel(p: BubblePosition): String = stringResource(
    when (p) {
        BubblePosition.AUTO -> R.string.bubble_pos_auto
        BubblePosition.TOP_LEFT -> R.string.bubble_pos_top_left
        BubblePosition.TOP -> R.string.bubble_pos_top
        BubblePosition.TOP_RIGHT -> R.string.bubble_pos_top_right
        BubblePosition.MIDDLE_LEFT -> R.string.bubble_pos_middle_left
        BubblePosition.CENTER -> R.string.bubble_pos_center
        BubblePosition.MIDDLE_RIGHT -> R.string.bubble_pos_middle_right
        BubblePosition.BOTTOM_LEFT -> R.string.bubble_pos_bottom_left
        BubblePosition.BOTTOM -> R.string.bubble_pos_bottom
        BubblePosition.BOTTOM_RIGHT -> R.string.bubble_pos_bottom_right
    }
)

/** Select du placement de l'infobulle (même fond/bord compacts que [LanguagePicker]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BubblePositionPicker(current: BubblePosition, onSelect: (BubblePosition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        Box(Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().clip(OutlinedTextFieldDefaults.shape)) {
            OutlinedTextFieldDefaults.Container(
                enabled = true, isError = false, interactionSource = interactionSource,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(bubblePositionLabel(current), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(open, modifier = Modifier.requiredSize(CompactIconSize))
            }
        }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BubblePosition.entries.forEach { p ->
                DropdownMenuItem(text = { Text(bubblePositionLabel(p)) }, onClick = { open = false; onSelect(p) })
            }
        }
    }
}
