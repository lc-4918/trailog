package fr.lc4918.trailog.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/** Avatar circulaire (image locale ou URL, cropée en cercle) ; logo Trailog par défaut si `source` est vide
 *  (toujours clair.png, quel que soit le thème, affiché tel quel, sans recadrage circulaire). */
@Composable
fun Avatar(source: String, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (source.isBlank()) {
        AsyncImage(
            model = "file:///android_asset/avatar/clair.png",
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
