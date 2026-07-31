package com.skallahaze.irbloasster.ui.theme

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skallahaze.irbloasster.data.ThemePreference

private val LightColors = lightColorScheme(
    primary = Color(0xFF5266F8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5DBFF),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF5D70FF),
    onSecondary = Color.White,
    background = Color(0xFFEFF1F4),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF606C80),
    outline = Color(0xFFCAD0DD),
    error = Color(0xFFC00011),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8593FF),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF4A57BF),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF8593FF),
    onSecondary = Color(0xFF111111),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF2E3133),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF454749),
    onSurfaceVariant = Color(0xFFA1ACBF),
    outline = Color(0xFF606C80),
    error = Color(0xFFFFB4AB),
)

private val SmartIrTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
)

private val SmartIrShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)

@Composable
fun IRTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SmartIrTypography,
        shapes = SmartIrShapes,
        content = content,
    )
}
