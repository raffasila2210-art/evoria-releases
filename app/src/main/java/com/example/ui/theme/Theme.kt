package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.ThemeMode

@Immutable
data class EvoriaTokens(
  val isDark: Boolean,
  val background: Color,
  val secondarySurface: Color,
  val cardSurface: Color,
  val elevatedCard: Color,
  val accentPeach: Color,
  val primaryTerracotta: Color,
  val inkText: Color,
  val mutedText: Color,
  val outlineSoft: Color,
  val glassPillTint: Color,
  val glassCapsuleTint: Color,
  val glassSpecularTop: Color,
  val glassSpecularBottom: Color,
  val sunsetGlow: Color,
  val hillSilhouetteNear: Color,
  val hillSilhouetteFar: Color,
  val sageSuccess: Color,
  val roseError: Color,
  val cardRadius: Dp = 22.dp,
  val pillRadius: Dp = 28.dp,
  val sectionSpacing: Dp = 20.dp,
  val screenHorizontalPadding: Dp = 20.dp,
)

private val LightEvoriaTokens =
  EvoriaTokens(
    isDark = false,
    background = WarmBackground,
    secondarySurface = WarmSecondarySurface,
    cardSurface = WarmCard,
    elevatedCard = WarmCardElevated,
    accentPeach = WarmPeach,
    primaryTerracotta = WarmTerracotta,
    inkText = WarmInk,
    mutedText = WarmMuted,
    outlineSoft = WarmOutline,
    glassPillTint = Color(0xCCF7F2EA),
    glassCapsuleTint = Color(0xEBFFFDF9),
    glassSpecularTop = Color(0xE6FFFFFF),
    glassSpecularBottom = Color(0x40A46B55),
    sunsetGlow = Color(0x33E9B99E),
    hillSilhouetteNear = Color(0x1FA46B55),
    hillSilhouetteFar = Color(0x148D7769),
    sageSuccess = EvoriaSage,
    roseError = EvoriaRoseError,
  )

private val DarkEvoriaTokens =
  EvoriaTokens(
    isDark = true,
    background = MidnightBackground,
    secondarySurface = MidnightDeep,
    cardSurface = MidnightSurface,
    elevatedCard = MidnightCardElevated,
    accentPeach = MidnightPeach,
    primaryTerracotta = MidnightAccent,
    inkText = MidnightText,
    mutedText = MidnightMuted,
    outlineSoft = MidnightOutline,
    glassPillTint = Color(0xCC2D2523),
    glassCapsuleTint = Color(0xD9473935),
    glassSpecularTop = Color(0x66F5EBE1),
    glassSpecularBottom = Color(0x33C58D73),
    sunsetGlow = Color(0x2BC58D73),
    hillSilhouetteNear = Color(0x261B1716),
    hillSilhouetteFar = Color(0x1AC58D73),
    sageSuccess = Color(0xFF8AA88B),
    roseError = Color(0xFFD9786A),
  )

val LocalEvoriaTokens = staticCompositionLocalOf { LightEvoriaTokens }

private val LightColorScheme =
  lightColorScheme(
    primary = WarmTerracotta,
    onPrimary = Color.White,
    primaryContainer = WarmPeach,
    onPrimaryContainer = WarmInk,
    secondary = WarmMuted,
    onSecondary = Color.White,
    secondaryContainer = WarmCard,
    onSecondaryContainer = WarmInk,
    tertiary = EvoriaSage,
    background = WarmBackground,
    onBackground = WarmInk,
    surface = WarmSecondarySurface,
    onSurface = WarmInk,
    surfaceVariant = WarmCard,
    onSurfaceVariant = WarmMuted,
    outline = WarmOutline,
    error = EvoriaRoseError,
    onError = Color.White,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = MidnightAccent,
    onPrimary = MidnightDeep,
    primaryContainer = Color(0xFF4F372E),
    onPrimaryContainer = MidnightText,
    secondary = MidnightMuted,
    onSecondary = MidnightDeep,
    secondaryContainer = MidnightSurface,
    onSecondaryContainer = MidnightText,
    tertiary = Color(0xFF8AA88B),
    background = MidnightBackground,
    onBackground = MidnightText,
    surface = MidnightDeep,
    onSurface = MidnightText,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = MidnightMuted,
    outline = MidnightOutline,
    error = Color(0xFFD9786A),
    onError = MidnightDeep,
  )

val EvoriaShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun EvoriaTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  accentPreset: com.example.data.local.EvoriaAccentPreset = com.example.data.local.EvoriaAccentPreset.TERRACOTTA,
  customAccentHex: Long? = null,
  content: @Composable () -> Unit,
) {
  val useDark =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val customPrimary: Color
  val customPeach: Color
  if (customAccentHex != null && customAccentHex != 0L) {
    val argb = (customAccentHex or 0xFF000000L).toInt()
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
    val baseHsl = hsl.copyOf()
    if (useDark) {
      baseHsl[2] = baseHsl[2].coerceIn(0.55f, 0.78f)
    } else {
      baseHsl[2] = baseHsl[2].coerceIn(0.32f, 0.62f)
    }
    customPrimary = Color(androidx.core.graphics.ColorUtils.HSLToColor(baseHsl))
    val softHsl = hsl.copyOf()
    softHsl[1] = (softHsl[1] * 0.75f).coerceIn(0.20f, 0.85f)
    softHsl[2] = if (useDark) 0.76f else 0.84f
    customPeach = Color(androidx.core.graphics.ColorUtils.HSLToColor(softHsl))
  } else {
    customPrimary =
      Color(if (useDark) accentPreset.darkPrimaryHex else accentPreset.lightPrimaryHex)
    customPeach =
      Color(if (useDark) accentPreset.darkPeachHex else accentPreset.lightPeachHex)
  }

  val colorScheme =
    (if (useDark) DarkColorScheme else LightColorScheme).copy(
      primary = customPrimary,
      primaryContainer = customPeach,
    )
  val baseTokens = if (useDark) DarkEvoriaTokens else LightEvoriaTokens
  val tokens =
    baseTokens.copy(
      primaryTerracotta = customPrimary,
      accentPeach = customPeach,
      sunsetGlow = customPeach.copy(alpha = if (useDark) 0.17f else 0.20f),
      glassSpecularBottom = customPrimary.copy(alpha = if (useDark) 0.22f else 0.25f),
    )

  CompositionLocalProvider(LocalEvoriaTokens provides tokens) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = EvoriaTypography,
      shapes = EvoriaShapes,
      content = content,
    )
  }
}
