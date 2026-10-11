package io.github.chinatsu033.airpodsmode.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GlassCorner = 28.dp

@Composable
fun glassFill(selected: Boolean = false): Color {
    val dark = isSystemInDarkTheme()
    return when {
        selected && dark -> Color.White.copy(alpha = 0.34f)
        selected && !dark -> Color.White.copy(alpha = 0.62f)
        dark -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.38f)
    }
}

@Composable
fun glassBorderColor(selected: Boolean = false): Color {
    val dark = isSystemInDarkTheme()
    return when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        dark -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.72f)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: Dp = GlassCorner,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val dark = isSystemInDarkTheme()
    val fill = glassFill(selected)
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    fill.copy(alpha = (fill.alpha + 0.12f).coerceAtMost(0.85f)),
                    fill.copy(alpha = (fill.alpha - 0.04f).coerceAtLeast(0.10f)),
                ),
            ),
            shape = shape,
        )
        // Specular top edge
        .border(
            BorderStroke(
                width = if (selected) 1.6.dp else 1.1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.55f else 0.85f),
                        glassBorderColor(selected).copy(alpha = 0.35f),
                        glassBorderColor(selected),
                    ),
                ),
            ),
            shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = ripple(
                        bounded = true,
                        color = if (dark) Color.White.copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.18f),
                    ),
                    enabled = enabled,
                    onClick = onClick,
                )
            } else Modifier,
        )

    Box(modifier = base, content = content)
}
