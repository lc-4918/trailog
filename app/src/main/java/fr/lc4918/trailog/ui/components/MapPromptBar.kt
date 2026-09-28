package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.ui.theme.Spacing
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor

/** Fond des barres posées en bas de la carte : gris très foncé, 10 % de transparence. Partagé par la
 *  saisie de la bounding box hors-ligne et par le choix d'un point de mesure, qui doivent se ressembler
 *  exactement - l'utilisateur y lit le même genre de consigne. */
/**
 * Le ton d'un bandeau pose sur la carte, et ses couleurs, prises au theme.
 *
 * - [PROMPT] : une consigne (mesure, point a choisir), sur le fond inverse du theme - sombre en clair,
 *   clair en sombre ;
 * - [WARNING] : un avertissement (la localisation s'est tue), dans l'orange d'avertissement ;
 * - [ALERT] : l'alerte d'eloignement, dans le rouge d'erreur.
 *
 * En theme sombre, l'orange et le rouge s'assombrissent - leurs conteneurs, texte clair - au lieu de
 * rester vifs : un aplat vif sur une carte sombre eblouit, de nuit, quand on s'en sert le plus.
 */
internal enum class MapBannerTone { PROMPT, WARNING, ALERT }

/** Le fond et le texte d'un bandeau de ce ton, dans le theme en cours. */
@Composable
internal fun MapBannerTone.colors(): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val dark = c.surface.luminance() < 0.5f
    return when (this) {
        MapBannerTone.PROMPT -> c.inverseSurface to c.inverseOnSurface
        MapBannerTone.WARNING -> if (dark) c.tertiaryContainer to c.onTertiaryContainer else c.tertiary to c.onTertiary
        MapBannerTone.ALERT -> if (dark) c.errorContainer to c.onErrorContainer else c.error to c.onError
    }
}

/**
 * Le cadre commun des bandeaux de la carte : un aplat arrondi, une ombre, et - s'il se ferme - le bandeau
 * TOUT ENTIER comme cible. Pas de croix : on ferme un bandeau en le touchant, et une croix de 20 dp dans
 * son coin etait la seule cible de la carte qu'il fallait viser.
 */
@Composable
internal fun MapBanner(
    tone: MapBannerTone,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable (fg: Color) -> Unit,
) {
    val (bg, fg) = tone.colors()
    Box(
        modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)
            .shadow(6.dp, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(bg.copy(alpha = BannerAlpha))
            .then(if (onClose != null) Modifier.clickable(onClick = onClose) else Modifier)
            .padding(horizontal = 14.dp, vertical = Spacing.m),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content(fg) }
    }
}

/** Presque opaque : la carte transparait a peine, le texte se lit sur tout fond. */
private const val BannerAlpha = 0.96f

@Composable
fun MapActionBar(
    text: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    MapBanner(MapBannerTone.PROMPT, modifier, onClose) { fg ->
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            androidx.compose.foundation.layout.Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun MapPromptBar(text: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    MapBanner(MapBannerTone.PROMPT, modifier, onClose) { fg ->
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
