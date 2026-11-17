package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

@Immutable
data class GlassTokens(
    val isDark: Boolean,
    val canvasTop: Color,
    val canvasMid: Color,
    val canvasBottom: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val orbTertiary: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassHighlightBorder: Color,
    val glassShadowBorder: Color,
    val dockSurface: Color,
    val dockIndicator: Color,
    val tamazightAccent: Color,
    val arabicAccent: Color,
    val frenchAccent: Color,
    val englishAccent: Color
)

private val DarkGlassTokens = GlassTokens(
    isDark = true,
    canvasTop = MidnightAbyss,
    canvasMid = DeepSapphire,
    canvasBottom = Color(0xFF081626),
    orbPrimary = Color(0x3D38BDF8),
    orbSecondary = Color(0x33F5B041),
    orbTertiary = Color(0x2E2DD4BF),
    glassSurface = Color(0xA3122742),
    glassSurfaceElevated = Color(0xCC183254),
    glassHighlightBorder = GlassBorderDarkTop,
    glassShadowBorder = GlassBorderDarkBottom,
    dockSurface = Color(0xD90E2038),
    dockIndicator = Color(0x3838BDF8),
    tamazightAccent = LangTamazightDark,
    arabicAccent = LangArabicDark,
    frenchAccent = LangFrenchDark,
    englishAccent = LangEnglishDark
)

private val LightGlassTokens = GlassTokens(
    isDark = false,
    canvasTop = AlabasterMist,
    canvasMid = FrostSurface,
    canvasBottom = Color(0xFFE1EEF9),
    orbPrimary = Color(0x330E4D8A),
    orbSecondary = Color(0x2EF5B041),
    orbTertiary = Color(0x290D9488),
    glassSurface = GlassCardLight,
    glassSurfaceElevated = Color(0xE0FFFFFF),
    glassHighlightBorder = GlassBorderLightTop,
    glassShadowBorder = GlassBorderLightBottom,
    dockSurface = Color(0xE6F8FBFF),
    dockIndicator = Color(0x240E4D8A),
    tamazightAccent = LangTamazightLight,
    arabicAccent = LangArabicLight,
    frenchAccent = LangFrenchLight,
    englishAccent = LangEnglishLight
)

val LocalGlassTokens = staticCompositionLocalOf { DarkGlassTokens }

private val DarkColorScheme = darkColorScheme(
    primary = SkyFrost,
    onPrimary = MidnightAbyss,
    primaryContainer = SkyFrostContainer,
    onPrimaryContainer = TextIce,
    secondary = AmazighGold,
    onSecondary = MidnightAbyss,
    secondaryContainer = AmazighGoldContainer,
    onSecondaryContainer = TextIce,
    tertiary = OasisTeal,
    onTertiary = MidnightAbyss,
    tertiaryContainer = OasisTealContainer,
    onTertiaryContainer = TextIce,
    background = MidnightAbyss,
    onBackground = TextIce,
    surface = DeepSapphire,
    onSurface = TextIce,
    surfaceVariant = GlassSlateDark,
    onSurfaceVariant = TextMist,
    error = TerracottaRose,
    onError = MidnightAbyss,
    outline = Color(0xFF2D4A6B)
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltOcean,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = InkDeep,
    secondary = WarmAmber,
    onSecondary = Color.White,
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = InkDeep,
    tertiary = DeepEmerald,
    onTertiary = Color.White,
    tertiaryContainer = DeepEmeraldContainer,
    onTertiaryContainer = InkDeep,
    background = AlabasterMist,
    onBackground = InkDeep,
    surface = FrostSurface,
    onSurface = InkDeep,
    surfaceVariant = Color(0xFFE3ECF6),
    onSurfaceVariant = SlateMuted,
    error = CrimsonRose,
    onError = Color.White,
    outline = Color(0xFFB5C7DA)
)

@Composable
fun TasniremtTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val glassTokens = if (darkTheme) DarkGlassTokens else LightGlassTokens

    CompositionLocalProvider(LocalGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TasniremtTypography,
            content = content
        )
    }
}
