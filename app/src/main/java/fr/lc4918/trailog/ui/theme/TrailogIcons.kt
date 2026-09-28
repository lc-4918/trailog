package fr.lc4918.trailog.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Les icones dessinees pour l'application, la ou celles de Material ne conviennent pas.
 *
 * Au TRAIT, et non pleines : un trait fin se lit plus leger a cote du texte, et c'est l'allure des
 * maquettes. La couleur du trait est indifferente - Icon la remplace par sa teinte.
 */
object TrailogIcons {

    /**
     * La poubelle : un couvercle, sa poignee, une cuve evasee et deux rainures. Remplace Delete et
     * DeleteOutline de Material, pleines et massives, partout ou l'on supprime ou efface.
     */
    val Trash: ImageVector by lazy {
        stroked("Trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    }

    /** Une icone de 24 dp faite d'un seul trace au trait, arrondi aux bouts et aux angles. */
    internal fun stroked(name: String, pathData: String, strokeWidth: Float = 1.8f) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
