package com.skallahaze.irbloasster.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2EE6D6),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0D4E49),
    onPrimaryContainer = Color(0xFF9AF4EB),
    secondary = Color(0xFFFF4FA3),
    onSecondary = Color(0xFF3A0020),
    secondaryContainer = Color(0xFF5D123B),
    onSecondaryContainer = Color(0xFFFFD8E9),
    tertiary = Color(0xFF95B8FF),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE4E8F0),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFE4E8F0),
    surfaceVariant = Color(0xFF1A202A),
    onSurfaceVariant = Color(0xFFC2C8D2),
    outline = Color(0xFF737B88),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9AF4EB),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFFA40055),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E9),
    onSecondaryContainer = Color(0xFF3A0020),
    tertiary = Color(0xFF315DA8),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E7EF),
    onSurfaceVariant = Color(0xFF414850)
)

@Composable
fun LivingRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
