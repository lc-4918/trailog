package fr.lc4918.trailog.ui.geocode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.geocode.GeocodePlace
import fr.lc4918.trailog.ui.components.CompactOutlinedTextField

/** Nombre de propositions visibles sans faire défiler ; au-delà, la liste défile. */
private const val VisibleSuggestions = 4

/** Hauteur d'une proposition : deux lignes à 13sp et leur marge, pour que la liste s'arrête exactement
 *  après la quatrième, quelle que soit la longueur des adresses affichées. */
private val SuggestionHeight = 52.dp

/** Opacité du champ et des propositions : la carte reste lisible dessous pendant la frappe. */
private const val PanelAlpha = 0.8f

/** Arrondi des deux panneaux. Porté aussi par le contour du champ (celui qui vire au bleu au focus) :
 *  laissé à la forme M3 par defaut, il valait 4dp et ses angles ne suivaient pas ceux du fond. */
private val PanelShape = RoundedCornerShape(8.dp)

/**
 * Barre de recherche de lieu/adresse et ses propositions.
 *
 * Ne décide de rien : la frappe, l'appel au géocodeur et la sélection sont pilotés par l'appelant
 * (cf. GeocodeSearchState), qui garde ces états au-delà de la vie du composable.
 */
@Composable
fun GeocodeSearchBar(
    query: String,
    results: List<GeocodePlace>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (GeocodePlace) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.fillMaxWidth()) {
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = PanelAlpha),
            // Couleur de contenu imposée : un fond translucide n'est plus l'une des couleurs du thème, et
            // contentColorFor n'y reconnaît donc rien. Sans elle, le texte hérite du LocalContentColor
            // ambiant - noir sur noir en thème sombre.
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 4.dp,
        ) {
            CompactOutlinedTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus), shape = PanelShape,
                // Une seule ligne quoi qu'il arrive : le libellé est court, mais une police système agrandie
                // ou une traduction plus longue le ferait sinon passer à la ligne et grandir le champ.
                placeholder = {
                    Text(stringResource(R.string.geocode_search_placeholder),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
                trailingIcon = {
                    // Le spinner remplace la croix pendant l'interrogation : même emplacement, pas de
                    // saut de largeur du champ entre les deux états.
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
                            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
                            }
                        }
                    }
                },
            )
        }
        if (results.isNotEmpty()) {
            Surface(
                modifier = Modifier.padding(top = 4.dp),
                shape = PanelShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = PanelAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,   // cf. le champ ci-dessus
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.heightIn(max = SuggestionHeight * VisibleSuggestions).verticalScroll(rememberScrollState())) {
                    results.forEachIndexed { i, place ->
                        if (i > 0) HorizontalDivider()
                        Box(
                            Modifier.fillMaxWidth().height(SuggestionHeight)
                                .clickable { onPick(place) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(place.label, fontSize = 13.sp, lineHeight = 16.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
