package fr.lc4918.trailog.ui.poi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R
import fr.lc4918.trailog.domain.model.GroupCheck
import fr.lc4918.trailog.domain.model.PoiCategory
import fr.lc4918.trailog.domain.model.PoiFilters
import fr.lc4918.trailog.domain.model.PoiGroup
import androidx.core.graphics.toColorInt

/**
 * Largeur MAXIMALE de la bulle.
 *
 * 264 dp d'abord, et c'etait trop juste : les onglets faisaient 60 dp, ou "Hebergement" et "Restauration"
 * se coupaient en plein mot - Compose casse au milieu quand le mot ne tient pas, faute d'un autre endroit
 * ou couper. A 320 dp ils font 74 dp, et les quatre libelles tiennent sur une ligne, l'allemand compris.
 *
 * Un MAXIMUM et non une largeur fixe : sur un ecran etroit, la bulle prend ce qui reste a gauche du
 * bouton plutot que de deborder. C'est l'appelant qui lui donne cette place (cf. [PoiFilterBubbleAnchored]).
 */
private val BubbleMaxWidth = 320.dp

/** Hauteur maximale de la liste des categories. Neuf lignes tiennent dessous ; au-dela elle defile. */
private val PanelMaxHeight = 260.dp

/** Cote de la pointe, avant rotation : un carre tourne d'un quart de tour, dont la moitie depasse. */
private val TailSize = 14.dp

/**
 * La bulle des points d'interet : ce qu'on affiche sur la carte, choisi depuis la carte.
 *
 * **Elle remplace une rubrique des reglages.** Les vingt-sept categories vivaient dans Reglages > Trajets,
 * quatre sections depliables de cases a cocher - c'est-a-dire a quatre gestes de la carte, dans un ecran
 * qui recouvre justement ce qu'on essaie de regarder. Or choisir ses points d'interet est un geste de
 * TERRAIN : on cherche un camping en fin d'apres-midi, un point d'eau a la montee, une boulangerie le
 * matin, et l'on veut alors ces lieux-la et pas les vingt-six autres.
 *
 * **Le filtre EST l'interrupteur.** Il n'y a plus de "montrer les points d'interet" a cote : la carte
 * montre exactement ce qui est en surbrillance ici, et tout masquer eteint la couche (cf. [PoiFilters]).
 * Le bouton de la carte n'allume donc plus rien - il ouvre et ferme cette bulle, et c'est tout.
 *
 * **Chaque ligne porte le marqueur qu'elle commande** : le meme pictogramme et la meme couleur de groupe
 * que l'epingle posee sur la carte (cf. [poiIcon], [poiGroupColor]). C'est ce qui fait le lien entre une
 * ligne cochee ici et un marqueur vu la-bas, sans avoir a l'ouvrir pour comprendre lequel on vient
 * d'allumer.
 *
 * @param onFilters le filtre a enregistrer. Il part en base a chaque tap : c'est un reglage, et il doit
 *   survivre a la fermeture de la bulle comme au redemarrage.
 */
@Composable
fun PoiFilterBubble(
    filters: PoiFilters,
    onFilters: (PoiFilters) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    /** La couche est mise de cote : ses marqueurs ont quitte la carte, son filtre est intact. */
    masked: Boolean = false,
    onToggleMask: () -> Unit = {},
) {
    /*
     * L'onglet ouvert survit a la fermeture de la bulle et a une rotation : on revient a ses hebergements
     * apres avoir regarde la carte, et repartir de "Hebergement" a chaque ouverture ferait perdre le fil.
     *
     * Sa CLE et non l'enumeration : `rememberSaveable` ecrit dans un `Bundle`, qui ne sait pas porter une
     * valeur d'enumeration. La cle est deja ce que le domaine expose comme identifiant stable.
     */
    var groupeKey by rememberSaveable { mutableStateOf(PoiGroup.LODGING.key) }
    val groupe = PoiGroup.entries.firstOrNull { it.key == groupeKey } ?: PoiGroup.LODGING
    Card(
        modifier = modifier.widthIn(max = BubbleMaxWidth).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        EnTete(filters, onFilters, onClose, masked, onToggleMask)
        Onglets(groupe, filters) { groupeKey = it.key }
        // La cle du defilement suit l'onglet : chacun garde sa position, et passer de "Pratique" - neuf
        // lignes - a "Restauration" - deux - ne doit pas ouvrir sur une liste defilee hors de sa fin.
        val defilement = rememberScrollState()
        Column(
            Modifier.height(PanelMaxHeight).verticalScroll(defilement).padding(vertical = 4.dp),
        ) {
            // "Tout le groupe" en tete, avec son etat a trois valeurs : coche, vide, ou entre les deux
            // quand une partie seulement du groupe est retenue.
            LigneGroupe(
                filters.groupState(groupe),
                Color(poiGroupColor(groupe).toColorInt()),
            ) { onFilters(filters.toggleGroup(groupe)) }
            PoiCategory.of(groupe).forEach { cat ->
                LigneCategorie(cat, filters.isShown(cat)) { onFilters(filters.toggle(cat)) }
            }
        }
    }
}

/**
 * L'en-tete : ce que la bulle regle, de quoi ranger la couche un instant, et de quoi tout eteindre.
 *
 * **L'oeil et la poubelle ne font pas la meme chose, et c'est tout l'objet de l'oeil.** La poubelle
 * DECOCHE tout : elle vide le filtre, et revoir ce qu'on avait choisi demande de le recocher categorie par
 * categorie. L'oeil ne touche a rien - il retire les marqueurs de la carte et les remet, le filtre entier
 * derriere lui. Le testeur n'avait que la poubelle, et l'a dit ainsi : "on ne peut plus masquer les points
 * d'interet".
 *
 * L'oeil vient A GAUCHE de la poubelle : on range avant de jeter, et le geste le plus anodin des deux ne
 * doit pas etre celui qu'on atteint en dernier.
 *
 * Ni l'un ni l'autre n'a de sens s'il ne reste rien a montrer : les deux s'effacent alors, plutot que de
 * proposer un geste sans effet - c'est la meme regle que le bouton de recentrage de la carte.
 */
@Composable
private fun EnTete(
    filters: PoiFilters,
    onFilters: (PoiFilters) -> Unit,
    onClose: () -> Unit,
    masked: Boolean,
    onToggleMask: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.poi_layer_title),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (!filters.nothingShown) {
            IconButton(
                onClick = onToggleMask,
                modifier = Modifier.size(28.dp),
            ) {
                // L'oeil BARRE quand la couche est de cote : le dessin montre l'etat courant, non le geste
                // qu'il declenche. C'est la convention de tous les interrupteurs de l'application - le
                // repere de position, les couches du menu lateral - et l'inverse se lit a contresens.
                Icon(
                    if (masked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    stringResource(
                        if (masked) R.string.poi_filter_show_layer else R.string.poi_filter_hide_layer,
                    ),
                    tint = if (masked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { onFilters(filters.hideAll()) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.poi_filter_hide_all),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                stringResource(R.string.action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Les quatre groupes en onglets, chacun avec le compte de ce qu'il retient.
 *
 * Le compte plutot qu'une pastille : "3/9" dit d'un coup d'oeil qu'un groupe est partiellement filtre,
 * ce qu'aucune couleur d'onglet ne saurait dire sans qu'on l'ouvre.
 */
@Composable
private fun Onglets(courant: PoiGroup, filters: PoiFilters, onGroup: (PoiGroup) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PoiGroup.entries.forEach { g ->
            val actif = g == courant
            val teinte = Color(poiGroupColor(g).toColorInt())
            val cats = PoiCategory.of(g)
            Column(
                // `clickable` AVANT `clip`, et c'est un test d'interface qui l'a impose : l'ordre
                // inverse - celui qu'on ecrit d'instinct pour que l'ondulation epouse les angles -
                // laissait l'onglet sourd au doigt, la zone tactile suivant alors le contour decoupe.
                // La zone d'abord, la decoration ensuite : un onglet qui ne repond pas est bien pire
                // qu'une ondulation qui deborde de deux points sur un angle arrondi.
                Modifier.weight(1f)
                    .clickable { onGroup(g) }
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(if (actif) teinte.copy(alpha = 0.14f) else Color.Transparent)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    poiGroupLabel(g),
                    fontSize = 10.sp, lineHeight = 12.sp,
                    fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (actif) teinte else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${cats.count { filters.isShown(it) }}/${cats.size}",
                    fontSize = 9.sp, lineHeight = 11.sp,
                    color = if (actif) teinte else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Tout selectionner" : coche tout le groupe, ou le decoche si tout etait deja coche.
 *
 * La meme case a trois etats que dans les reglages d'ou ces categories viennent - cochee, vide, ou barree
 * quand une partie seulement du groupe est retenue. Le dessin plutot qu'un mot : c'est celui que
 * l'utilisateur connait deja de l'ecran qu'on lui retire.
 */
@Composable
private fun LigneGroupe(etat: GroupCheck, teinte: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (etat) {
                GroupCheck.ALL -> Icons.Filled.CheckBox
                GroupCheck.SOME -> Icons.Filled.IndeterminateCheckBox
                GroupCheck.NONE -> Icons.Filled.CheckBoxOutlineBlank
            },
            null,
            modifier = Modifier.size(20.dp),
            tint = if (etat == GroupCheck.NONE) MaterialTheme.colorScheme.onSurfaceVariant else teinte,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            stringResource(R.string.settings_poi_select_all),
            fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Une categorie : son pictogramme dans la couleur de son groupe, son nom, et sa surbrillance quand elle
 * est retenue.
 *
 * **Le pictogramme et la couleur sont ceux du MARQUEUR**, et non un jeu propre a cette liste : c'est ce
 * qui permet de reconnaitre sur la carte ce qu'on vient d'allumer ici.
 *
 * Retenue, la ligne porte le fond teinte du groupe et son pictogramme est plein ; masquee, elle passe en
 * gris. Une case a cocher ferait la meme chose en moins de place, mais elle dirait "coche pour choisir"
 * la ou l'on veut dire "voila ce que montre la carte".
 */
@Composable
private fun LigneCategorie(cat: PoiCategory, retenue: Boolean, onToggle: () -> Unit) {
    val teinte = Color(poiGroupColor(cat.group).toColorInt())
    val eteint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(if (retenue) teinte.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(50))
                .background(if (retenue) teinte.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(poiIcon(cat)), null,
                modifier = Modifier.size(17.dp),
                tint = if (retenue) teinte else eteint,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            poiCategoryLabel(cat),
            fontSize = 13.sp,
            color = if (retenue) MaterialTheme.colorScheme.onSurface else eteint,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La bulle, sa pointe et son animation, posee a gauche du bouton qui l'ouvre.
 *
 * **L'animation dit d'ou elle vient.** Elle grandit depuis le bord droit, la ou se trouve le bouton, avec
 * un leger depassement avant de se poser - le geste d'un objet qui sort de sous le doigt. Elle se retire
 * sans rebond et deux fois plus vite : l'ouverture est une reponse, la fermeture est un rangement, et un
 * rangement qui rebondit retient l'attention pour rien.
 *
 * @param tailFromBottom hauteur de la pointe au-dessus du bas de la bulle : celle du centre du bouton, de
 *   sorte que la pointe le vise vraiment.
 */
@Composable
fun PoiFilterBubbleAnchored(
    open: Boolean,
    filters: PoiFilters,
    onFilters: (PoiFilters) -> Unit,
    tailFromBottom: Dp,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    masked: Boolean = false,
    onToggleMask: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = open,
        modifier = modifier,
        // Le point fixe de la mise a l'echelle : le bord droit, a la hauteur de la pointe. C'est ce qui
        // fait sortir la bulle DU BOUTON plutot que de la faire gonfler sur place.
        enter = scaleIn(
            animationSpec = keyframes {
                durationMillis = 480
                0.25f at 0
                1.06f at 264 using CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
                0.97f at 374
                1f at 480
            },
            initialScale = 0.25f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + fadeIn(tween(200)),
        exit = scaleOut(
            animationSpec = tween(260, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            targetScale = 0.2f,
            transformOrigin = TransformOrigin(1f, 1f),
        ) + fadeOut(tween(260)),
    ) {
        /*
         * La bulle, et sa pointe posee A CHEVAL sur son bord droit.
         *
         * La boite fait la largeur de la bulle plus une demi-pointe : la carte occupe la gauche, la pointe
         * s'aligne a droite, et son centre tombe donc exactement sur le bord de la carte. La rotation ne
         * change pas les limites de mise en page - le losange deborde de lui-meme vers le bouton, ce qui
         * est precisement ce qu'on veut d'une pointe.
         *
         * Dessinee APRES la carte, donc par-dessus : posee avant, le fond de la carte l'aurait recouverte.
         */
        Box(Modifier.widthIn(max = BubbleMaxWidth + TailSize / 2).padding(start = 8.dp)) {
            PoiFilterBubble(
                filters,
                onFilters,
                Modifier.widthIn(max = BubbleMaxWidth).padding(end = TailSize / 2),
                onClose,
                masked,
                onToggleMask,
            )
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(bottom = (tailFromBottom - TailSize / 2).coerceAtLeast(0.dp))
                    .size(TailSize)
                    .rotate(45f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
