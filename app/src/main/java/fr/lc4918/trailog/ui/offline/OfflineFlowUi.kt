package fr.lc4918.trailog.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.ui.routes.MainViewModel
import fr.lc4918.trailog.ui.routes.MapChrome

/**
 * La fin du chemin du telechargement hors-ligne : la configuration - zooms, nom, points d'interet - puis
 * l'avancement.
 *
 * Le chemin commence ailleurs, a l'une de ses deux entrees : le cadrage d'une zone, ouvert depuis les
 * reglages, qui se fait SUR la carte (cf. BboxEditorOverlay) ; ou le menu d'une trace, dans le menu lateral.
 * Les deux aboutissent ici.
 *
 * @param currentProvider le fond affiche : ses zooms bornent ceux qu'on peut demander.
 * @param poiAvailable la couche des points d'interet est allumee - proposer d'emporter ce qu'on ne peut
 *   pas afficher n'aurait aucun sens.
 */
@Composable
internal fun BoxScope.OfflineFlowUi(
    offline: OfflineFlowState,
    download: OfflineDownloadState?,
    chrome: MapChrome,
    vm: MainViewModel,
    currentProvider: ProviderEntity?,
    styleJson: String?,
    styleUrl: String?,
    poiAvailable: Boolean,
) {
    offline.configBbox?.let { bbox ->
        OfflineDownloadConfigScreen(
            bbox = bbox,
            corridorPoints = offline.corridor?.second,
            corridorName = offline.corridor?.first?.name.orEmpty(),
            providerMinZoom = currentProvider?.minZoom ?: 0,
            providerMaxZoom = currentProvider?.maxZoom ?: 19,
            dark = chrome.dark,
            styleJson = styleJson, styleUrl = styleUrl,
            poiAvailable = poiAvailable,
            onDismiss = { offline.closeFlow() },
            onDownload = { request ->
                // Domaine B : lance le moteur, puis revient à la carte ou la popup de progression
                // (observée via vm.offlineDownload) prend le relais.
                vm.startOfflineDownload(request)
                offline.closeFlow()
            },
        )
    }
    // Popup de progression du téléchargement hors-ligne (SPEC section 4), par-dessus la carte. Le mode
    // réduit (bouton orange) est rendu dans la barre de boutons en haut à gauche, pas ici.
    download?.let { dl ->
        if (!dl.minimized) {
            // Scrim opaque : bloque les interactions avec la carte derrière la popup.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {})
            OfflineDownloadCard(
                state = dl,
                onMinimize = { vm.setOfflineDownloadMinimized(true) },
                onCancel = { vm.cancelOfflineDownload() },
                onClose = { vm.dismissOfflineDownload() },
                modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 420.dp),
            )
        }
    }
}
