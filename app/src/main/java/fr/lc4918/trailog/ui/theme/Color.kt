package fr.lc4918.trailog.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Les deux jeux de couleurs de l'application, COMPLETS : chaque role de Material 3 y est pose.
 *
 * Ils ne l'etaient pas - seuls `primary` et `secondary` l'etaient, et tout le reste (surfaces, conteneurs,
 * `onPrimary`...) restait celui du jeu par defaut de Material, violet. Une pastille retenue, un fond de
 * menu ou de carte tiraient donc au lavande sur des ecrans par ailleurs bleus, et les reglages avaient du
 * se donner une palette a eux pour y echapper (cf. SettingsPalette, qui lit desormais celle-ci).
 *
 * Maquette de reference : "Trailog - theme et maquettes", planche des tokens.
 *
 * **Clair** : le bleu des reglages, sur des neutres a peine bleutes.
 * **Sombre** : des gris NEUTRES presque noirs, sans teinte - un sombre bleu nuit se lisait comme une
 * autre application. Le bleu n'y reste que comme accent, et sur le fond d'une option retenue.
 */

internal val TrailogLight = lightColorScheme(
    primary = Color(0xFF16588F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E7F5),
    onPrimaryContainer = Color(0xFF0C3F6B),
    inversePrimary = Color(0xFF6FB6E8),
    secondary = Color(0xFF2D6F68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3ECE8),
    onSecondaryContainer = Color(0xFF0F3F3A),
    tertiary = Color(0xFFA04A12),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE3D3),
    onTertiaryContainer = Color(0xFF5A2600),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF17222C),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF17222C),
    surfaceVariant = Color(0xFFDCE7F0),
    onSurfaceVariant = Color(0xFF56697B),
    surfaceTint = Color(0xFF16588F),
    inverseSurface = Color(0xFF2A3642),
    inverseOnSurface = Color(0xFFEEF3F8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7C8FA1),
    outlineVariant = Color(0xFFC9D6E1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBFCFE),
    surfaceDim = Color(0xFFD6DEE6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F6FA),
    surfaceContainer = Color(0xFFECF1F6),
    surfaceContainerHigh = Color(0xFFE6EDF3),
    surfaceContainerHighest = Color(0xFFDCE7F0),
)

internal val TrailogDark = darkColorScheme(
    primary = Color(0xFF6FB6E8),
    onPrimary = Color(0xFF00324F),
    primaryContainer = Color(0xFF0E3A5E),
    onPrimaryContainer = Color(0xFFC7E3F8),
    inversePrimary = Color(0xFF16588F),
    secondary = Color(0xFF7FC8BD),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF1F4F48),
    onSecondaryContainer = Color(0xFFBDEBE3),
    tertiary = Color(0xFFF2A36B),
    onTertiary = Color(0xFF4A2100),
    tertiaryContainer = Color(0xFF6A3410),
    onTertiaryContainer = Color(0xFFFFDBC6),
    background = Color(0xFF161616),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF333333),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceTint = Color(0xFF6FB6E8),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF1C1C1C),
    error = Color(0xFFF28B82),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFF383838),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3A3A),
    surfaceDim = Color(0xFF161616),
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF333333),
)
