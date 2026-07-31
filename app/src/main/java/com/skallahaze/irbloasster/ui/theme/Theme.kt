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

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DE7DC),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF164B47),
    onPrimaryContainer = Color(0xFFB9FFF4),
    secondary = Color(0xFFAFC8FF),
    onSecondary = Color(0xFF122F60),
    secondaryContainer = Color(0xFF283F66),
    onSecondaryContainer = Color(0xFFD7E2FF),
    tertiary = Color(0xFFF1B3CF),
    onTertiary = Color(0xFF4A1931),
    background = Color(0xFF070B12),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF0E151F),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2431),
    onSurfaceVariant = Color(0xFFB8C4D4),
    outline = Color(0xFF8290A3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF006B61),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DE7DC),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF3E5F91),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E2FF),
    onSecondaryContainer = Color(0xFF0A1B36),
    tertiary = Color(0xFF8D4665),
    onTertiary = Color.White,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF161B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161B22),
    surfaceVariant = Color(0xFFE4EAF2),
    onSurfaceVariant = Color(0xFF424A55),
    outline = Color(0xFF727B88)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun LivingRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
