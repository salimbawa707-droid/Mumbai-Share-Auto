package com.mumbaishareauto.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Theme mode preference persisted locally.
 */
enum class ThemePreference(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

// Brand & Semantic Tokens
object MumbaiColors {
    val DeepNavy = Color(0xFF12304A)
    val DeepNavyLight = Color(0xFF1E4566)
    val DeepNavyDarkContainer = Color(0xFF1A365D)
    val NavyContainerLight = Color(0xFFDCE7F7)

    val AutoAmber = Color(0xFFB45309)
    val AutoYellow = Color(0xFFF59E0B)
    val AutoAmberBright = Color(0xFFFBBF24)
    val AmberContainerLight = Color(0xFFFEF3C7)
    val AmberContainerDark = Color(0xFF452804)
    val OnAmberContainerLight = Color(0xFF451A03)

    val EmeraldSuccess = Color(0xFF15803D)
    val EmeraldBright = Color(0xFF34D399)
    val EmeraldContainerLight = Color(0xFFD1FAE5)
    val EmeraldContainerDark = Color(0xFF064E3B)
    val OnEmeraldContainerLight = Color(0xFF064E3B)

    val ClearRed = Color(0xFFDC2626)
    val ClearRedLight = Color(0xFFF87171)
    val RedContainerLight = Color(0xFFFEE2E2)
    val RedContainerDark = Color(0xFF5C1515)
    val OnRedContainerLight = Color(0xFF7F1D1D)

    val LightBackground = Color(0xFFDCE5F0)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE6EEF7)
    val LightOutline = Color(0xFFCBD5E1)
    val LightTextPrimary = Color(0xFF0F172A)
    val LightTextSecondary = Color(0xFF1E293B)

    val DarkBackground = Color(0xFF0B1220)
    val DarkSurface = Color(0xFF131C2E)
    val DarkSurfaceVariant = Color(0xFF1E293B)
    val DarkOutline = Color(0xFF334155)
    val DarkTextPrimary = Color(0xFFF1F5F9)
    val DarkTextSecondary = Color(0xFF94A3B8)
}

private val LightColorScheme = lightColorScheme(
    primary = MumbaiColors.DeepNavy,
    onPrimary = Color.White,
    primaryContainer = MumbaiColors.NavyContainerLight,
    onPrimaryContainer = MumbaiColors.DeepNavy,
    secondary = MumbaiColors.AutoAmber,
    onSecondary = Color.White,
    secondaryContainer = MumbaiColors.AmberContainerLight,
    onSecondaryContainer = MumbaiColors.OnAmberContainerLight,
    tertiary = MumbaiColors.EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = MumbaiColors.EmeraldContainerLight,
    onTertiaryContainer = MumbaiColors.OnEmeraldContainerLight,
    background = MumbaiColors.LightBackground,
    onBackground = MumbaiColors.LightTextPrimary,
    surface = MumbaiColors.LightSurface,
    onSurface = MumbaiColors.LightTextPrimary,
    surfaceVariant = MumbaiColors.LightSurfaceVariant,
    onSurfaceVariant = MumbaiColors.LightTextSecondary,
    outline = MumbaiColors.LightOutline,
    error = MumbaiColors.ClearRed,
    onError = Color.White,
    errorContainer = MumbaiColors.RedContainerLight,
    onErrorContainer = MumbaiColors.OnRedContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = MumbaiColors.DeepNavy,
    primaryContainer = MumbaiColors.DeepNavyDarkContainer,
    onPrimaryContainer = Color(0xFFE0EDFF),
    secondary = MumbaiColors.AutoAmberBright,
    onSecondary = Color(0xFF2E1600),
    secondaryContainer = MumbaiColors.AmberContainerDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = MumbaiColors.EmeraldBright,
    onTertiary = Color(0xFF003824),
    tertiaryContainer = MumbaiColors.EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = MumbaiColors.DarkBackground,
    onBackground = MumbaiColors.DarkTextPrimary,
    surface = MumbaiColors.DarkSurface,
    onSurface = MumbaiColors.DarkTextPrimary,
    surfaceVariant = MumbaiColors.DarkSurfaceVariant,
    onSurfaceVariant = MumbaiColors.DarkTextSecondary,
    outline = MumbaiColors.DarkOutline,
    error = MumbaiColors.ClearRedLight,
    onError = Color(0xFF450A0A),
    errorContainer = MumbaiColors.RedContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val MumbaiTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

private val MumbaiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp)
)

@Composable
fun MumbaiShareAutoTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
        typography = MumbaiTypography,
        shapes = MumbaiShapes,
        content = content
    )
}
