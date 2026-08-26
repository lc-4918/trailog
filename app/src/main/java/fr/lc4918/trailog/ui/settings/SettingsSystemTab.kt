package fr.lc4918.trailog.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.LocalePrefs
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.data.repo.StoragePaths
import fr.lc4918.trailog.map.flagAssetModel
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField
import fr.lc4918.trailog.domain.model.PlannerHistory
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Icon
import fr.lc4918.trailog.data.db.MinMapButtonSizeDp
import fr.lc4918.trailog.data.db.MaxMapButtonSizeDp

/**
 * L'onglet Systeme : langue, theme, sauvegarde et restauration, a propos.
 *
 * Changer de langue relance l'application (cf. restartApp) : la locale se pose dans attachBaseContext,
 * bien avant qu'un ecran ne soit compose.
 */

@Composable internal fun SystemTab(
    cur: SettingsEntity, vm: SettingsViewModel,
    onPickImportDir: () -> Unit, onPickMbtilesFolder: () -> Unit, onPickAvatar: () -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit,
) {
    val ctx = LocalContext.current
    var avatarDialogOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // Treize rubriques a plat devenaient une liste ou l'on cherchait. Quatre groupes : ou ca se range, ce
    // qui repond au doigt, ce que ca donne a voir, et ce que l'application fait d'elle-meme.
    GroupTitle(stringResource(R.string.settings_group_storage), first = true)
    SettingsCard {
        SetRow(
            stringResource(R.string.settings_section_import_folder),
            sub = if (cur.importDir.isBlank()) stringResource(R.string.settings_default_system)
                else (cur.importDir.toUri().lastPathSegment ?: stringResource(R.string.settings_default_system)),
        ) {
            InlineButton(stringResource(R.string.action_browse), Icons.Filled.Folder, onPickImportDir)
        }
        RowDivider()
        SetRow(
            stringResource(R.string.settings_section_mbtiles_folder),
            sub = if (cur.mbtilesDir.isBlank()) stringResource(R.string.settings_app_folder_default)
                else StoragePaths.displayName(cur.mbtilesDir),
        ) {
            InlineButton(stringResource(R.string.action_browse), Icons.Filled.Folder, onPickMbtilesFolder)
        }
    }

    // La contrepartie du "aucun compte, aucune synchronisation" : sans elle, un telephone change emporte
    // tout. Rangee sous "Stockage" et non dans un groupe a elle : c'est de la meme matiere qu'il s'agit -
    // ou vivent les donnees, et comment elles en sortent.
    SectionTitle(stringResource(R.string.settings_section_backup))
    SettingsCard {
        SetRow(stringResource(R.string.settings_backup_create)) {
            InlineButton(stringResource(R.string.action_save), Icons.Filled.FileUpload, onBackup)
        }
        RowDivider()
        SetRow(stringResource(R.string.settings_backup_restore)) {
            InlineButton(stringResource(R.string.action_restore), Icons.Filled.FileDownload, onRestore)
        }
        Hint(stringResource(R.string.settings_backup_hint))
    }

    /*
     * CACHE : ce que l'application a retenu toute seule, et de quoi le retirer.
     *
     * Les deux lignes viennent de l'onglet Trajets, ou elles suivaient la rubrique des categories de
     * points d'interet - laquelle a quitte les reglages pour une bulle ouverte depuis la carte (cf.
     * PoiFilterBubble). Elles n'avaient plus rien a y faire : ce ne sont pas des reglages de trajet mais
     * des gestes d'entretien, et leur place est aupres du stockage et de la sauvegarde.
     *
     * Meme gabarit pour les deux - un compte en sous-titre, une corbeille, eteinte quand il n'y a rien -
     * et la meme raison : ce qui s'inscrit sans qu'on le demande doit pouvoir se retirer sans
     * reinitialiser tous les reglages. C'est la moindre des choses pour une application dont l'argument
     * est que tout reste sur l'appareil.
     *
     * Sans confirmation ni l'une ni l'autre : huit lieux se reconstituent en une promenade, et un cache
     * se remplit tout seul des qu'on rouvre la carte. La demander serait du ceremonial.
     */
    SectionTitle(stringResource(R.string.settings_section_cache))
    val poiEnCache by vm.poiCached.collectAsState()
    val poiEmportes by vm.poiPinned.collectAsState()
    val lieux = PlannerHistory.of(cur.plannerHistory).places
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
        RowDivider()
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

    GroupTitle(stringResource(R.string.settings_group_interaction))
    SectionTitle(stringResource(R.string.settings_section_side_menu_open), tight = true)
    SettingsCard {
        SegChips(
            listOf("burger" to stringResource(R.string.side_menu_burger),
                "swipe" to stringResource(R.string.side_menu_swipe),
                "both" to stringResource(R.string.side_menu_both)),
            cur.sideMenuMode,
        ) { vm.save(cur.copy(sideMenuMode = it)) }
    }
    // Deux tolerances : les marqueurs sont interroges avant les traces et l'emportent, une valeur large
    // sur eux rend une trace qui passe a cote difficile a atteindre.
    SectionTitle(stringResource(R.string.settings_section_tap_tolerance))
    SettingsCard {
        SliderRow(
            stringResource(R.string.settings_label_waypoints), "${cur.tapToleranceDp} dp",
            fractionOf(cur.tapToleranceDp, 4, 40), steps = 35,
            onFraction = { vm.save(cur.copy(tapToleranceDp = valueOf(it, 4, 40))) },
        )
        RowDivider()
        SliderRow(
            stringResource(R.string.settings_label_tracks), "${cur.lineTapToleranceDp} dp",
            fractionOf(cur.lineTapToleranceDp, 4, 40), steps = 35,
            onFraction = { vm.save(cur.copy(lineTapToleranceDp = valueOf(it, 4, 40))) },
        )
    }

    GroupTitle(stringResource(R.string.settings_group_appearance))
    var currentLang by remember { mutableStateOf(LocalePrefs.get(ctx)) }
    SettingsCard {
        PickRow(
            stringResource(R.string.settings_label_theme), cur.theme,
            listOf("system", "light", "dark"),
            optionLabel = {
                stringResource(when (it) {
                    "light" -> R.string.theme_light
                    "dark" -> R.string.theme_dark
                    else -> R.string.theme_system
                })
            },
        ) { vm.save(cur.copy(theme = it)) }
        RowDivider()
        PickRow(
            stringResource(R.string.settings_label_language), currentLang,
            LocalePrefs.SELECTABLE, optionLabel = { LocalePrefs.nativeName(it) },
        ) { code ->
            currentLang = code
            LocalePrefs.set(ctx, code)
            (ctx as? Activity)?.recreate()
        }
        RowDivider()
        PickRow(
            stringResource(R.string.settings_label_units), cur.units, listOf("meters", "imperial"),
            optionLabel = { stringResource(if (it == "imperial") R.string.unit_imperial else R.string.unit_metric) },
        ) { vm.save(cur.copy(units = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_status_bar_transparent), cur.statusBarTransparent,
            sub = stringResource(R.string.settings_sw_status_bar_sub),
        ) { vm.save(cur.copy(statusBarTransparent = it)) }
    }

    /*
     * L'aspect des boutons de la carte, venu de l'onglet Carte.
     *
     * Ils y voisinaient les interrupteurs qui decident lesquels s'affichent, et les deux questions n'ont
     * rien a voir : l'une dit CE QU'ON POSE sur la carte - et se regle donc la-bas, avec les gestes -,
     * celle-ci dit a quoi cela ressemble, comme le theme et la barre d'etat juste au-dessus.
     */
    SectionTitle(stringResource(R.string.settings_section_map_buttons))
    SettingsCard {
        SwitchLine(stringResource(R.string.settings_sw_buttons_bg), cur.controlButtonsBackground) {
            vm.save(cur.copy(controlButtonsBackground = it))
        }
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

    SectionTitle(stringResource(R.string.settings_section_personalisation))
    var titleText by remember(cur.customTitle) { mutableStateOf(cur.customTitle) }
    SettingsCard {
        FieldRow(stringResource(R.string.settings_label_side_title)) {
            SettingsTextField(titleText, stringResource(R.string.drawer_default_title)) { titleText = it }
        }
        RowDivider()
        SetRow(stringResource(R.string.settings_label_avatar)) {
            if (cur.avatarSource.isNotBlank()) {
                RowIcon(Icons.Filled.DeleteOutline, stringResource(R.string.action_reset_avatar)) {
                    vm.save(cur.copy(avatarSource = ""))
                }
            }
            RowIcon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.action_change_avatar)) {
                avatarDialogOpen = true
            }
        }
        CardAction(stringResource(R.string.action_save)) { vm.save(cur.copy(customTitle = titleText)) }
    }

    GroupTitle(stringResource(R.string.settings_group_application))
    SettingsCard {
        /*
         * Garder l'ecran allume, venu de l'onglet Carte.
         *
         * Il y suivait le suivi de position, dont il partage le declencheur - le drapeau n'est pose que
         * pendant le suivi (cf. KeepScreenOnEffect) - mais ce n'est pas un bouton ni un geste de carte :
         * c'est ce que l'application fait au telephone sans qu'on le lui redemande, comme les deux lignes
         * qui suivent.
         */
        SwitchLine(
            stringResource(R.string.settings_sw_keep_screen_on), cur.keepScreenOn,
            sub = stringResource(R.string.settings_sw_keep_screen_on_sub),
        ) { vm.save(cur.copy(keepScreenOn = it)) }
        RowDivider()
        SwitchLine(
            stringResource(R.string.settings_simplify_render), cur.simplifyRender,
            sub = stringResource(R.string.settings_sw_simplify_sub),
        ) { vm.save(cur.copy(simplifyRender = it)) }
        Hint(stringResource(R.string.settings_simplify_render_hint))
        RowDivider()
        UpdatesRow(cur, vm)
        CardAction(stringResource(R.string.action_reset_all_settings)) { confirmReset = true }
    }

    if (avatarDialogOpen) {
        var urlText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { avatarDialogOpen = false },
            title = { Text(stringResource(R.string.action_change_avatar)) },
            text = {
                Column {
                    Button(onClick = { onPickAvatar(); avatarDialogOpen = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_load_image_from_phone))
                    }
                    Spacer(Modifier.height(12.dp))
                    CompactOutlinedTextField(urlText, { urlText = it },
                        placeholder = { Text(stringResource(R.string.avatar_url_placeholder)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlText.isNotBlank()) vm.save(cur.copy(avatarSource = urlText))
                    avatarDialogOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { avatarDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.action_reset_all_settings)) },
            text = { Text(stringResource(R.string.dialog_reset_all_settings_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllSettings()
                    confirmReset = false
                    (ctx as? Activity)?.recreate()
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/* --------------- Helpers (taille compacte : cf. bug 1.5) --------------- */

@OptIn(ExperimentalMaterial3Api::class)
/** Déclencheur du select construit à la main (Box + Row), plutôt que via les slots leadingIcon/trailingIcon
 *  de [CompactOutlinedTextField] : ces slots imposent un plancher de hauteur (zone tactile M3, ~48dp) non
 *  contournable de l'intérieur, ce qui gonflait le champ par rapport aux autres inputs compacts (bug 1.6).
 *  Le fond/bord réutilise [OutlinedTextFieldDefaults.Container] pour rester visuellement identique. */
@Composable internal fun LanguagePicker(current: String, onSelect: (String) -> Unit) {
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
                LanguageFlag(current)
                Spacer(Modifier.width(8.dp))
                Text(LocalePrefs.nativeName(current), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(open, modifier = Modifier.requiredSize(CompactIconSize))
            }
        }
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LocalePrefs.SELECTABLE.forEach { code ->
                DropdownMenuItem(
                    leadingIcon = { LanguageFlag(code) },
                    text = { Text(LocalePrefs.nativeName(code)) },
                    // Fermer avant de déclencher le changement de langue (qui recrée l'Activity) : évite
                    // tout flash du menu encore ouvert pendant la recréation (cf. bug 1.6).
                    onClick = { open = false; onSelect(code) },
                )
            }
        }
    }
}

@Composable private fun LanguageFlag(code: String) {
    val flag = LocalePrefs.flagCode(code) ?: return
    AsyncImage(model = flagAssetModel(flag), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(2.dp)))
}

/**
 * Relance l'application depuis zero.
 *
 * Seul geste de l'application qui tue son propre processus, et il n'a qu'un seul appelant : apres une
 * restauration, la base sur le disque n'est plus celle que Room a ouverte. Rouvrir proprement supposerait
 * de reconstruire tout ce qui en depend - flux, caches, ecrans - alors qu'un redemarrage le fait sans
 * risque d'en oublier un. L'ecran suivant est celui du demarrage normal, avec les donnees restaurees.
 */
internal fun restartApp(ctx: android.content.Context) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) ctx.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
