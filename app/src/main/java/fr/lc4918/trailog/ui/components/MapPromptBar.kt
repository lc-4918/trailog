package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Fond des barres posées en bas de la carte : gris très foncé, 10 % de transparence. Partagé par la
 *  saisie de la bounding box hors-ligne et par le choix d'un point de mesure, qui doivent se ressembler
 *  exactement - l'utilisateur y lit le même genre de consigne. */
internal val MapBarBackground = Color(0xFF1B1B1B).copy(alpha = 0.9f)

/** Barre du bas réduite à une consigne : le mode de saisie n'attend qu'un tap sur la carte, il n'y a
 *  donc aucun bouton à offrir (le retour système en sort, cf. MainScreen). */
@Composable
fun MapPromptBar(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(8.dp)
            .background(MapBarBackground, RoundedCornerShape(8.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
