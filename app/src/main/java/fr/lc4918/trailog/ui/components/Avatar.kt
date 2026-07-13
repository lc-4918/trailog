package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/** Avatar circulaire (image locale ou URL, cropée en cercle) ; logo Trailog par défaut si `source` est vide
 *  (clair/sombre selon le thème réellement actif, affiché tel quel - sans recadrage circulaire). */
@Composable
fun Avatar(source: String, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (source.isBlank()) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        AsyncImage(
            model = "file:///android_asset/avatar/${if (isDark) "sombre" else "clair"}.png",
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        AsyncImage(
            model = imageModel(source),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    }
}
