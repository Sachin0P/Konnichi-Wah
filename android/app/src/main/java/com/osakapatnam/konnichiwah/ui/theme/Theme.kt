package com.osakapatnam.konnichiwah.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The brand palette from the design: warm cream paper, maroon ink, a torii
// red for actions, sakura pink for chips, and matcha green for "safe"/offline.
object Palette {
    val Cream = Color(0xFFF7F0E4)
    val Card = Color(0xFFFFFBF4)
    val Maroon = Color(0xFF6B1E26)
    val Sun = Color(0xFFC8323A)
    val Petal = Color(0xFFF2CDD1)
    val HeroPink = Color(0xFFF6D5D9)
    val Ink = Color(0xFF2B1B1B)
    val Mute = Color(0xFF7A6868)
    val Line = Color(0xFFE6D9C6)
    val Green = Color(0xFF3E7A5A)
    val GreenBg = Color(0xFFDDEBDD)
    val GreenInk = Color(0xFF285540)
    val Orange = Color(0xFFF7DCC8)
    val OrangeInk = Color(0xFF7A3A12)
    val Tip = Color(0xFFFBEFE3)
    val Night = Color(0xFF241617)
}

// Headings use a serif, like the design's Fraunces. FontFamily.Serif keeps us
// free of a font dependency; a bundled Fraunces could be swapped in here.
val DisplayFont = FontFamily.Serif

// The design is a single warm light palette, so we don't use the wallpaper's
// dynamic colours or a dark theme: both would lose the brand. Material roles
// are mapped so stock components (dialogs, sliders, date picker) match.
private val Colors = lightColorScheme(
    primary = Palette.Sun,
    onPrimary = Color.White,
    primaryContainer = Palette.Petal,
    onPrimaryContainer = Palette.Maroon,
    secondary = Palette.Maroon,
    onSecondary = Palette.Tip,
    secondaryContainer = Palette.Petal,
    onSecondaryContainer = Palette.Maroon,
    tertiary = Palette.Green,
    onTertiary = Color.White,
    tertiaryContainer = Palette.GreenBg,
    onTertiaryContainer = Palette.GreenInk,
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Cream,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Tip,
    onSurfaceVariant = Palette.Mute,
    surfaceContainerLowest = Palette.Card,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.Tip,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    errorContainer = Palette.Petal,
    onErrorContainer = Palette.Maroon,
)

private val Base = Typography()

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, color = Palette.Maroon),
    headlineLarge = Base.headlineLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = Palette.Maroon),
    headlineSmall = Base.headlineSmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, color = Palette.Maroon),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun KonnichiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
