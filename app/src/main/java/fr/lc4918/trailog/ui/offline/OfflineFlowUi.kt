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
import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.map.offline.Bbox
import fr.lc4918.trailog.map.offline.OfflineDownloadState
import fr.lc4918.trailog.ui.routes.MainViewModel
import fr.lc4918.trailog.ui.routes.MapChrome
import fr.lc4918.trailog.ui.routes.OfflineExtentDialog
import fr.lc4918.trailog.ui.routes.OfflineTrackPickDialog

/**
 * Le chemin complet du telechargement hors-ligne, de la question a la barre de progression.
 *
 * Quatre ecrans qui se suivent et ne s'affichent jamais ensemble : ce qu'on telecharge (un rectangle ou le
 * couloir d'une trace), la trace a border le cas echeant, la configuration - zooms, nom, points d'interet -
 * puis l'avancement. Le trace du rectangle, lui, se fait SUR la carte et reste dans l'ecran : c'est le seul
 * moment ou l'utilisateur regarde le fond de carte plutot qu'un formulaire.
 *
 * Poses par-dessus tout le reste, en plein ecran : ces trois premiers ne sont pas des ornements de carte
 * mais des etapes, et rien de la carte n'a a rester touchable pendant qu'on y repond.
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
    layers: List<LayerEntity>,
    currentProvider: ProviderEntity?,
    styleJson: String?,
    styleUrl: String?,
    poiAvailable: Boolean,
) {
    // Configuration du téléchargement hors-ligne (SPEC section 3), plein écran par-dessus tout le reste.
    if (offline.extentChoice) {
        OfflineExtentDialog(
            dark = chrome.dark,
            onDismiss = { offline.extentChoice = false },
            onArea = {
                offline.extentChoice = false
                offline.corridor = null
                offline.drawingActive = true
            },
            onTrack = { offline.extentChoice = false; offline.pickTrack = true },
        )
    }
    if (offline.pickTrack) {
        OfflineTrackPickDialog(
            candidates = layers.filter { it.hasLine },
            onDismiss = { offline.pickTrack = false },
            onPick = { l ->
                offline.pickTrack = false
                // La geometrie est relue ICI et non a l'affichage de l'ecran suivant : le couloir se
                // calcule sur les points reels, et l'estimation doit etre juste des la premiere image.
                vm.trackPointsOf(l) { pts ->
                    if (pts.isNotEmpty()) {
                        offline.corridor = l to pts
                        offline.configBbox = Bbox.of(
                            pts.minOf { it.first }, pts.minOf { it.second },
                            pts.maxOf { it.first }, pts.maxOf { it.second },
                        )
                    }
                }
            },
        )
    }
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
