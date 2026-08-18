package fr.lc4918.trailog.ui.poi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import fr.lc4918.trailog.R
import fr.lc4918.trailog.poi.Poi
import fr.lc4918.trailog.ui.geocode.CloseCorner
import fr.lc4918.trailog.ui.points.InfoBubbleWidth
import fr.lc4918.trailog.ui.points.OverlayIconButton
import fr.lc4918.trailog.ui.points.OverlayInset

/**
 * Infobulle d'un point d'intérêt : ce qu'il est, et ce qu'on peut en faire.
 *
 * Reprend la carte des autres infobulles - même largeur, même croix d'angle, même élévation - parce qu'un
 * point d'intérêt n'est pas d'une autre nature qu'un marqueur de trace ou une épingle posée : c'est un
 * endroit sur la carte, et il s'ouvre pareil.
 *
 * **Pas d'image de repli** quand le lieu n'en publie pas, et c'est un choix : deux tiers des lieux de
 * DATAtourisme n'ont pas de photo, un visuel générique serait donc la règle plutôt que l'exception, et il
 * n'apprendrait rien - le pictogramme du marqueur dit déjà la catégorie.
 *
 * Le nom n'est cliquable que si le lieu publie un site ([Poi.webUrl]) : un titre souligné qui ne mène
 * nulle part est pire qu'un titre ordinaire.
 */
@Composable
fun PoiBubble(
    poi: Poi,
    onOpenWeb: (String) -> Unit,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onAddStep: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    fontSp: Int = 14,
    backgroundAlpha: Float = 1f,
) {
    Card(
        modifier = modifier.width(InfoBubbleWidth),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Box {
            Column {
                /*
                 * Avec photo : elle occupe toute la largeur en image de garde, et le nom comme la
                 * categorie se posent PAR-DESSUS, en bas a gauche - exactement comme l'infobulle d'un
                 * waypoint qui en porte une. Le nom garde son fond blanc a 80 % : sur une photo, un texte
                 * sans fond devient illisible des que le cliche est clair.
                 *
                 * Sans photo : le nom et la categorie prennent simplement la tete de la bulle. Pas de
                 * visuel de repli - deux tiers des lieux n'en publient pas, et un dessin generique
                 * n'apprendrait rien que le pictogramme du marqueur ne dise deja.
                 */
                if (poi.imageUrl != null) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    ) {
                        // Le peintre plutot que AsyncImage : c'est son etat qui dit s'il faut encore
                        // attendre. Une photo de point d'interet vient d'un serveur touristique quelconque,
                        // parfois lent, parfois muet - sans attente visible, la bulle s'ouvre sur un
                        // rectangle vide dont rien ne dit s'il va se remplir.
                        val peintre = rememberAsyncImagePainter(poi.imageUrl)
                        val etat by peintre.state.collectAsState()
                        Image(
                            painter = peintre,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                        )
                        // Pendant le chargement SEULEMENT : une erreur arrete l'attente comme une reussite,
                        // sans quoi le rond tournerait indefiniment sur une image qui ne viendra jamais.
                        if (etat is AsyncImagePainter.State.Loading) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp,
                            )
                        }
                        Box(
                            Modifier.align(Alignment.BottomStart)
                                .padding(start = 8.dp, bottom = 8.dp, end = 8.dp),
                        ) {
                            Nom(poi, fontSp, onOpenWeb)
                        }
                    }
                } else {
                    Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp)) {
                        Text(
                            stringResource(R.string.poi_bubble_title),
                            fontSize = (fontSp - 3).sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                        Nom(poi, fontSp, onOpenWeb)
                    }
                }
                Column(Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp)) {
                    // La categorie sous l'en-tete, et non par-dessus la photo : posee sur l'image, elle
                    // en masquait le bas - justement la ou le cliche montre le lieu.
                    CategoryChip(
                        poi, fontSp,
                        Modifier.padding(top = if (poi.imageUrl != null) 8.dp else 6.dp, bottom = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 2.dp))
                    RouteAction(Icons.Outlined.PlayArrow, R.string.poi_set_start, fontSp, onSetStart)
                    RouteAction(Icons.Outlined.Flag, R.string.poi_set_end, fontSp, onSetEnd)
                    RouteAction(Icons.Filled.Add, R.string.poi_add_step, fontSp, onAddStep)
                }
            }
            /*
             * La croix sur une photo prend son halo blanc, comme celle d'un waypoint pose sur son image
             * de garde : sans lui, un contour noir se perd sur un cliche sombre et disparait sur un
             * cliche clair. Sans photo, elle garde la croix ordinaire des autres infobulles - le halo
             * n'aurait rien a detacher.
             */
            if (poi.imageUrl != null) {
                OverlayIconButton(
                    Icons.Filled.Close, R.string.action_close, onClose, filled = false,
                    modifier = Modifier.align(Alignment.TopEnd).padding(OverlayInset),
                )
            } else {
                CloseCorner(onClose, Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 4.dp))
            }
        }
    }
}

/**
 * Le nom du lieu, sur son fond blanc a 80 % - le meme que le titre d'une infobulle de waypoint posee sur
 * son image de garde, et pour la meme raison : sur une photo claire, un texte sans fond disparait.
 *
 * Il le garde AUSSI sans photo, sur le fond de la bulle, ou il ne se voit pas : mieux vaut un seul nom,
 * toujours pareil, que deux presentations selon qu'un lieu a publie une image.
 *
 * Souligne et cliquable seulement si le lieu publie un site : un titre souligne qui ne mene nulle part
 * est pire qu'un titre ordinaire.
 */
@Composable
private fun Nom(poi: Poi, fontSp: Int, onOpenWeb: (String) -> Unit) {
    val url = poi.webUrl
    Text(
        poi.label,
        fontSize = fontSp.sp, fontWeight = FontWeight.Bold, color = Color.Black,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        textDecoration = if (url != null) TextDecoration.Underline else null,
        modifier = (if (url != null) Modifier.clickable { onOpenWeb(url) } else Modifier)
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Le libellé de la catégorie, sur l'aplat de son groupe : la même couleur que le marqueur qu'on vient de
 *  toucher, pour que l'infobulle se rattache d'un coup d'oeil au point qui l'a ouverte. */
@Composable
private fun CategoryChip(poi: Poi, fontSp: Int, modifier: Modifier = Modifier) {
    val teinte = Color(android.graphics.Color.parseColor(poiGroupColor(poi.category.group)))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(teinte.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(painterResource(poiIcon(poi.category)), null, Modifier.size((fontSp - 2).dp), tint = teinte)
        Spacer(Modifier.width(4.dp))
        Text(
            poiCategoryLabel(poi.category),
            fontSize = (fontSp - 3).sp, color = teinte, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Une des trois actions d'itinéraire : toute la ligne est cliquable, comme les rangées de mesure de
 *  l'infobulle d'un point de carte. */
@Composable
private fun RouteAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    fontSp: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(label), fontSize = fontSp.sp)
    }
}
