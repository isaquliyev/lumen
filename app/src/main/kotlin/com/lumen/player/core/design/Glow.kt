package com.lumen.player.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale

// a soft radial glow in the accent's glow colour; breathes slowly when [breathing] is on
@Composable
fun GlowBackground(
    glow: Color,
    modifier: Modifier = Modifier,
    centerY: Float = 0.3f,
    radiusFactor: Float = 0.9f,
    breathing: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val scale = if (breathing && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "breathe")
        val value by transition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Reverse), label = "scale")
        value
    } else 1f
    Box(
        modifier.drawBehind {
            val center = Offset(size.width / 2, size.height * centerY)
            scale(scale, pivot = center) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.85f), glow.copy(alpha = 0.25f), Color.Transparent),
                        center = center,
                        radius = size.width * radiusFactor,
                    )
                )
            }
        },
        content = content,
    )
}
