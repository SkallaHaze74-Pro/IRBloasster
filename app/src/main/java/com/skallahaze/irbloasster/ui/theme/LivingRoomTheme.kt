package com.skallahaze.irbloasster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LivingPink = Color(0xFFFF6E9C)
val LivingCyan = Color(0xFF76E4F7)
val LivingViolet = Color(0xFFB5A4FF)
val LivingGreen = Color(0xFF70E1A1)
val LivingAmber = Color(0xFFFFC56E)
val NightBackground = Color(0xFF090B10)
val NightSurface = Color(0xFF121620)
val NightSurfaceHigh = Color(0xFF1B2130)

private val DarkColors = darkColorScheme(
    primary = LivingCyan,
    onPrimary = Color(0xFF002F36),
    primaryContainer = Color(0xFF164A54),
    onPrimaryContainer = Color(0xFFB7F4FF),
    secondary = LivingPink,
    onSecondary = Color(0xFF4B001F),
    secondaryContainer = Color(0xFF68233F),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = LivingViolet,
    onTertiary = Color(0xFF29205E),
    background = NightBackground,
    onBackground = Color(0xFFF2F4FA),
    surface = NightSurface,
    onSurface = Color(0xFFF2F4FA),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFC3C8D5),
    outline = Color(0xFF858B99),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006875),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CEFFD),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF9D315D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3E001D),
    tertiary = Color(0xFF5A50A0),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E4EB),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F)
)

@Composable
fun LivingRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
