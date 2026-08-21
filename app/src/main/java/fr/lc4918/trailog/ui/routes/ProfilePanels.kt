package fr.lc4918.trailog.ui.routes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.domain.geo.Format
import fr.lc4918.trailog.domain.geo.TrackMath
import fr.lc4918.trailog.domain.geo.TrackMeasure
import fr.lc4918.trailog.domain.model.ComputedTrack
import fr.lc4918.trailog.ui.geocode.GeocodeBubble
import fr.lc4918.trailog.ui.profile.ElevationProfile
import fr.lc4918.trailog.ui.profile.SlopeLegend
import fr.lc4918.trailog.ui.profile.TrackInfoColumns
import fr.lc4918.trailog.ui.profile.titleInfos

/**
 * Ce qui entoure le profil altimetrique dans l'ecran principal : son bouton de zoom, la ligne du restant,
 * le bandeau d'altitude manquante, et le panneau du parcours calcule.
 *
 * Le dessin du profil lui-meme vit ailleurs (`ui/profile`) ; ici ne restent que les elements qui
 * l'accompagnent a l'ecran.
 */

/** Petit bouton carré (24 dp) des contrôles de zoom du profil (début/fin/expand). Contrairement à
 *  IconButton, sa couche d'état (ripple + fond bleu de sélection) est bornée à sa propre taille via clip :
 *  IconButton force une couche d'état de 40 dp qui déborderait de ce bouton réduit. */
@Composable
internal fun ProfileZoomButton(
    @androidx.annotation.DrawableRes iconRes: Int, contentDesc: String, active: Boolean, onClick: () -> Unit,
) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDesc, modifier = Modifier.size(16.dp),
            tint = if (active) Color.White else LocalContentColor.current)
    }
}

/* ----------------------- Légende ----------------------- */

/**
 * Ou l'on en est sur la trace affichee : ce qui reste a parcourir, et a monter.
 *
 * L'ecart a la trace n'est dit qu'au-dela de [OffTrackThresholdM] : sur la trace, il vaut la precision du
 * capteur et ne veut rien dire ; loin d'elle, il est la seule information qui compte - le "restant" ne
 * decrit alors plus le chemin qu'on suit.
 */
@Composable
internal fun RemainingOnTrackRow(
    projection: TrackMeasure.Projection, remaining: TrackMath.Remaining, imperial: Boolean, fontSp: Int,
) {
    val off = projection.awayM >= OffTrackThresholdM
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, null, Modifier.size((fontSp + 3).dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.track_remaining,
                Format.distance(remaining.distance, imperial), Format.elevation(remaining.ascent, imperial)),
            fontSize = fontSp.sp, fontWeight = FontWeight.Medium, color = Color.Black,
        )
        if (off) {
            Text(
                stringResource(R.string.track_off_track, Format.distance(projection.awayM, imperial)),
                fontSize = (fontSp - 1).sp, color = MaterialTheme.colorScheme.error,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** Au-dela de cet ecart, on ne suit plus la trace : c'est le moment de le dire. Cinquante metres passent
 *  la precision d'un GPS de telephone sous couvert forestier, sans attendre qu'on soit vraiment perdu. */
internal const val OffTrackThresholdM = 50.0

/** Air autour du titre du bandeau de profil : au-dessus comme au-dessous, dans tous les cas. */
internal val ProfileTitleGap = 4.dp

/** Air entre les infos de la trace et le graphique quand la legende des pentes n'est pas affichee :
 *  c'est elle qui tenait cette place, et elle en prenait plus que six points. */
internal val ProfileGraphGap = 12.dp

/** Ecart du bloc d'infos du point courant : le meme a droite de l'ecran qu'au-dessus du profil. */
internal val CursorInfoGap = 4.dp

/** Teintes de l'avertissement "sans altimétrie" : figées, pour rester lisibles sur le fond blanc du
 *  panneau de profil quel que soit le thème. */
internal val NoElevationBorder = Color(0xFFE8850C)

internal val NoElevationFill = Color(0xFFFFF3E0)

internal val NoElevationText = Color(0xFFB35309)

/** Avertissement affiché à la place du tracé quand la trace n'a aucune altitude : 80 % de la largeur et
 *  50 % de la hauteur de la zone de dessin, centré dedans. */
@Composable
internal fun NoElevationBanner(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.5f)
                .background(NoElevationFill, RoundedCornerShape(8.dp))
                .border(1.dp, NoElevationBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.profile_no_elevation), color = NoElevationText,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

/**
 * Panneau de profil d'un itinéraire mesuré depuis l'infobulle d'un lieu.
 *
 * Reprend la forme du profil d'une trace - mêmes réglages d'apparence, même légende, même graphique - pour
 * qu'une pente s'y lise de la même façon. Trois différences, toutes tenant à la nature de l'objet : pas de
 * spinner (l'itinéraire est calculé avant que le bouton n'apparaisse), pas de zoom A/B (un parcours court,
 * d'un seul tenant), et une croix de fermeture, car rien sur la carte ne permettrait de le rouvrir.
 *
 * Aucun garde-fou sur l'altimétrie : le bouton qui ouvre ce panneau n'existe que si le moteur a rendu des
 * altitudes (cf. GeocodeBubble).
 */
@Composable
internal fun RouteProfilePanel(
    track: ComputedTrack,
    title: String,
    settings: SettingsEntity?,
    imperial: Boolean,
    lineColor: Color,
    cursorX: Double?,
    lastLabelInsetPx: Float,
    onScrub: (Double) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp).navigationBarsPadding(),
    ) {
        // Meme mise en page que le bandeau d'une trace : le titre sur sa ligne (la croix a son bout),
        // les infos en colonnes sur toute la largeur en dessous.
        Row(
            Modifier.padding(vertical = ProfileTitleGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = (settings?.profTitleFont ?: 16).sp,
                fontWeight = if (settings?.profTitleBold != false) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(18.dp))
                }
            }
        }
        TrackInfoColumns(
            titleInfos(
                track.stats, settings?.titleInfos ?: "dist,asc,desc", imperial,
                remember(track.samples) { TrackMath.toblerSeconds(track.samples) },
            ),
            fontSp = settings?.profBarFont ?: 11,
            bold = settings?.profBarBold == true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings?.profileSlope != false && settings?.profileSlopeLegend == true) {
            SlopeLegend(track.stats.maxAbsSlope, settings?.profLegendFont ?: 9,
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                bold = settings?.profLegendBold == true)
        } else {
            Spacer(Modifier.height(ProfileGraphGap))
        }
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            ElevationProfile(
                samples = track.samples, stats = track.stats,
                grid = settings?.profileGrid ?: true,
                slope = settings?.profileSlope ?: true,
                lineColor = lineColor,
                axisFontSp = settings?.profAxisFont ?: 9,
                axisBold = settings?.profAxisBold == true,
                cursorX = cursorX, onScrub = onScrub,
                lastLabelInsetPx = lastLabelInsetPx,
                verticalScaleMPerCm = settings?.profileVerticalScaleMPerCm ?: 0,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        }
    }
}
