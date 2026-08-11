package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.lc4918.trailog.R

/** Fond des barres posées en bas de la carte : gris très foncé, 10 % de transparence. Partagé par la
 *  saisie de la bounding box hors-ligne et par le choix d'un point de mesure, qui doivent se ressembler
 *  exactement - l'utilisateur y lit le même genre de consigne. */
internal val MapBarBackground = Color(0xFF1B1B1B).copy(alpha = 0.9f)

/**
 * Barre du bas réduite à une consigne : le mode de saisie n'attend qu'un tap sur la carte.
 *
 * [onClose] non nul y ajoute une croix, plaquée dans le coin haut-droit : elle est volontairement plus
 * petite qu'un bouton d'icône ordinaire, la barre devant garder la hauteur de sa seule ligne de texte.
 * Sans elle, la barre ne se referme que par le retour système (cf. MainScreen).
 */
/**
 * La même barre, augmentée de boutons : une consigne qui appelle une réponse, et non un simple tap sur la
 * carte.
 *
 * Les actions sont sur leur propre ligne, sous le texte : mises à sa suite, une consigne un peu longue les
 * repoussait hors de l'écran sur un téléphone étroit - or ce sont elles qu'on vient chercher.
 */
@Composable
fun MapActionBar(
    text: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Box(
        modifier.fillMaxWidth().padding(8.dp)
            .background(MapBarBackground, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(end = if (onClose != null) 22.dp else 0.dp))
            androidx.compose.foundation.layout.Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        if (onClose != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close),
                    Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun MapPromptBar(text: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    Box(
        modifier.fillMaxWidth().padding(8.dp)
            .background(MapBarBackground, RoundedCornerShape(8.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White,
            textAlign = TextAlign.Center,
            // La croix mord sur la largeur du texte : sans cette marge, une consigne longue passerait dessous.
            modifier = Modifier.fillMaxWidth().padding(end = if (onClose != null) 22.dp else 0.dp))
        if (onClose != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close),
                    Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}
