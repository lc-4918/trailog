package fr.lc4918.trailog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import fr.lc4918.trailog.R

/**
 * Inter, embarquee en quatre graisses statiques plutot qu'en police variable : celle-ci ne se regle
 * qu'a partir d'Android 8, et l'application descend a Android 7.
 *
 * Les fichiers sont reduits aux alphabets latin, grec et cyrillique : les noms de lieux rendus par le
 * geocodeur peuvent en venir, le reste retombe sur la police du systeme. Licence : assets/licenses.
 */
internal val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(
    size: Float, lineHeight: Float, weight: FontWeight,
    letterSpacing: TextUnit = 0.sp, features: String? = null,
) = TextStyle(
    fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing, fontFeatureSettings = features,
)

/**
 * Echelle de texte de l'application, plus serree que celle de Material : elle sert surtout des bandes et
 * des bulles posees sur la carte, ou chaque ligne prise l'est a la carte.
 *
 * Les styles d'affichage et de gros titre ne servent pas aujourd'hui ; ils sont poses pour que rien ne
 * retombe sur ceux de Material, dans une autre police.
 *
 * [Typography.titleSmall] porte des chiffres a chasse fixe : c'est celui des totaux, qui ne doivent pas
 * danser quand une valeur change sous le doigt.
 */
internal val TrailogTypography = Typography(
    displayLarge = style(40f, 48f, FontWeight.SemiBold, (-0.5).sp),
    displayMedium = style(34f, 42f, FontWeight.SemiBold, (-0.4).sp),
    displaySmall = style(28f, 36f, FontWeight.SemiBold, (-0.3).sp),
    headlineLarge = style(26f, 32f, FontWeight.SemiBold, (-0.3).sp),
    headlineMedium = style(24f, 30f, FontWeight.SemiBold, (-0.2).sp),
    headlineSmall = style(22f, 28f, FontWeight.SemiBold, (-0.2).sp),
    titleLarge = style(20f, 26f, FontWeight.SemiBold, (-0.2).sp),
    titleMedium = style(16f, 24f, FontWeight.SemiBold, (-0.1).sp),
    titleSmall = style(15f, 20f, FontWeight.SemiBold, features = "tnum"),
    bodyLarge = style(15f, 22f, FontWeight.Normal),
    bodyMedium = style(14f, 20f, FontWeight.Normal),
    bodySmall = style(12f, 16f, FontWeight.Normal),
    labelLarge = style(13f, 18f, FontWeight.Medium),
    labelMedium = style(12f, 16f, FontWeight.Medium),
    labelSmall = style(10.5f, 14f, FontWeight.Medium, 0.4.sp),
)
