package sh.hnet.comfychair.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Adds a pulsing glow border effect when [active] is true.
 * Uses the primary color with animated alpha for a Material Design-consistent look.
 */
@Composable
fun Modifier.enhancingGlow(active: Boolean): Modifier {
    if (!active) return this

    val infiniteTransition = rememberInfiniteTransition(label = "enhancing_glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val glowColor = MaterialTheme.colorScheme.primary

    return this.drawBehind {
        drawRoundRect(
            color = glowColor.copy(alpha = alpha * 0.3f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
