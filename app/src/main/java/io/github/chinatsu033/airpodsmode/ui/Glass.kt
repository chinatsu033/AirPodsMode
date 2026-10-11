package io.github.chinatsu033.airpodsmode.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GlassCorner = 28.dp
val GlassShape = RoundedCornerShape(GlassCorner)

@Composable
fun glassFill(selected: Boolean = false): Color {
    val dark = isSystemInDarkTheme()
    return when {
        selected && dark -> Color.White.copy(alpha = 0.28f)
        selected && !dark -> Color.White.copy(alpha = 0.55f)
        dark -> Color.White.copy(alpha = 0.14f)
        else -> Color.White.copy(alpha = 0.32f)
    }
}

@Composable
fun glassBorder(selected: Boolean = false): BorderStroke {
    val dark = isSystemInDarkTheme()
    val c = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        dark -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    return BorderStroke(if (selected) 1.5.dp else 1.dp, c)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: Dp = GlassCorner,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        glassFill(selected).copy(alpha = glassFill(selected).alpha + 0.06f),
                        glassFill(selected),
                    ),
                ),
                shape = shape,
            )
            .border(glassBorder(selected), shape)
            .then(
                if (!dark) {
                    Modifier.background(Color.White.copy(alpha = 0.08f), shape)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}
