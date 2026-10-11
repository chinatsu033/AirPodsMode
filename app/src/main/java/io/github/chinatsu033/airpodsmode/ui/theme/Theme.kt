package io.github.chinatsu033.airpodsmode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B7C6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E6DE),
    onPrimaryContainer = Color(0xFF1B2E24),
    secondary = Color(0xFF7A6B64),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F3),
    onBackground = Color(0xFF1C1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1A),
    surfaceVariant = Color(0xFFE8E8E4),
    onSurfaceVariant = Color(0xFF4A4A46),
    outline = Color(0xFFC4C4BE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB8A8),
    onPrimary = Color(0xFF1B2E24),
    primaryContainer = Color(0xFF3E5548),
    onPrimaryContainer = Color(0xFFD8E6DE),
    secondary = Color(0xFFC4B5AE),
    onSecondary = Color(0xFF2C2420),
    background = Color(0xFF2A2A28),
    onBackground = Color(0xFFF0F0EC),
    surface = Color(0xFF343430),
    onSurface = Color(0xFFF0F0EC),
    surfaceVariant = Color(0xFF40403C),
    onSurfaceVariant = Color(0xFFC8C8C2),
    outline = Color(0xFF5A5A56),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirPodsModeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
