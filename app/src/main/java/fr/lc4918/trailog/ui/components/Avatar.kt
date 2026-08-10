package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Bleu de la pastille par defaut : l'accent de l'ecran des reglages, ou l'avatar se change. */
private val AvatarBlue = Color(0xFF16588F)

/** Avatar circulaire (image locale ou URL, cropée en cercle) ; à défaut, une pastille bleue aux initiales
 *  de l'application - un monogramme se lit à 26 dp dans une barre de titre, là où un logo détaillé n'est
 *  plus qu'une tache. */
@Composable
fun Avatar(source: String, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (source.isBlank()) {
        Box(
            modifier.size(size).clip(CircleShape).background(AvatarBlue)
                .then(if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            // Corps proportionnel au diametre : le meme monogramme sert de 26 dp dans une barre a 96 dp
            // dans les reglages, et une taille fixe le noierait ou le ferait deborder.
            Text(
                "TL", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (0.42f * size.value).sp, letterSpacing = 0.sp,
            )
        }
    } else {
        AsyncImage(
            model = imageModel(source),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    }
}
