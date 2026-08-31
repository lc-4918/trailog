package fr.lc4918.trailog.ui.poi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.ui.components.MapController

/**
 * Pourquoi la couche des points d'interet ne montre rien.
 *
 * Elle peut etre allumee et vide pour quatre raisons, et sans un mot on croit qu'elle est cassee : la vue
 * est trop dezoomee pour que le service accepte de la peupler, le reseau manque, on ne voit que ce que le
 * cache avait garde, ou la reponse a ete tronquee.
 *
 * @param topControlsPx la hauteur de la colonne de boutons du haut : le bandeau se glisse juste dessous.
 */
@Composable
internal fun BoxScope.PoiStatusBanner(
    poi: PoiState,
    controller: MapController,
    topControlsPx: Int,
) {
    val density = LocalDensity.current
    /*
     * Deux mots discrets sous les commandes du haut, quand la couche est allumee mais qu'elle ne peut
     * rien montrer : trop dezoome, ou pas de reseau et rien que le cache. Sans eux, la carte reste
     * simplement vide, et l'on croit la couche cassee.
     *
     * La bulle des categories ferme le bandeau : elle s'ouvre depuis le bord droit et descend sur la
     * carte, et l'avertissement de zoom naitrait derriere elle - au moment meme ou l'on coche la premiere
     * categorie. Il attend donc qu'on referme, c'est-a-dire qu'on regarde a nouveau la carte.
     */
    if (poi.bubbleOpen) return
    // Couche mise de cote : ces quatre messages disent pourquoi la carte ne montre rien, et la reponse est
    // alors qu'on vient de la ranger soi-meme (cf. PoiState.masked).
    if (poi.showingMarkers && (poi.tooFar || poi.needsNetwork || poi.fromCache || poi.partial)) {
        /*
         * Le message du zoom se TAPE, et zoome. Les deux autres ne sont que des constats -
         * pas de reseau, points du cache - que rien ni personne ne leve d'un doigt, et les
         * rendre tapables promettrait une action qui n'existe pas.
         *
         * Le message le DIT - "Appuyez ici pour zoomer" - au lieu de donner la consigne que le tap
         * execute a notre place. Il disait "Zoomez pour voir les points d'interet", ce qu'on lisait
         * comme un ordre a suivre soi-meme : on pinçait la carte, et l'on ne savait pas qu'il suffisait
         * de toucher ces mots-la.
         */
        val zoomable = poi.tooFar
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            // Couleur de contenu imposee : un fond translucide n'est plus l'une des couleurs du
            // theme, et contentColorFor n'y reconnait donc rien. Sans elle le texte hérite du
            // LocalContentColor ambiant, dont le defaut est le noir - illisible sur le fond
            // sombre de ce meme bandeau en theme sombre (cf. GeocodeSearchBar, meme remede).
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = with(density) { (topControlsPx + 16).toDp() })
                .then(
                    if (!zoomable) Modifier
                    else Modifier.clickable(role = Role.Button) {
                        // Le zoom minimum qui charge, pas un cran de plus : c'est le plus grand
                        // territoire que le service accepte de peupler, donc celui qui montre le
                        // plus de lieux d'un coup (cf. PoiLoading.MIN_ZOOM).
                        //
                        // Autour du centre courant, qu'on ne deplace pas : c'est la zone qu'on
                        // regarde qu'on veut voir peuplee, et une carte qui saute ailleurs au
                        // moment ou elle se remplit ferait perdre l'endroit qu'on tenait.
                        //
                        // Rien de plus a declencher : la camera qui s'immobilise relance le
                        // chargement, comme apres un geste de la main.
                        controller.cameraState()?.let { (la, lo, _) ->
                            controller.centerOnAtLeast(la, lo, PoiLoading.MIN_ZOOM)
                        }
                    }
                ),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // La main qui appuie, devant le seul des quatre messages qui SE TAPE. La couleur des
                // commandes ne suffisait pas : elle distingue bien ce message des trois constats, mais
                // seulement pour qui les a vus cote a cote - un utilisateur n'en voit jamais qu'un, et
                // rien ne lui disait que celui-la repond au doigt. Le texte le dit desormais, et ce
                // dessin le montre.
                if (zoomable) {
                    Icon(
                        Icons.Filled.TouchApp, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    stringResource(
                        when {
                            poi.tooFar -> R.string.poi_zoom_in
                            poi.needsNetwork -> R.string.poi_needs_network
                            poi.fromCache -> R.string.poi_from_cache
                            // En dernier : les trois autres disent pourquoi la carte est vide ou
                            // vieille, celui-ci pourquoi elle est incomplete - c'est le moins grave.
                            else -> R.string.poi_partial
                        }
                    ),
                    fontSize = 12.sp,
                    // La couleur des commandes quand le message en est une, celle du texte ordinaire
                    // sinon : sans cela, rien ne distinguerait la consigne qu'on peut suivre d'un
                    // doigt des deux constats qu'on ne peut que lire.
                    color = if (zoomable) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
