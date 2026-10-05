package com.lumen.player.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LumenEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

object Durations {
    const val SHORT = 200
    const val MEDIUM = 300
    const val LONG = 450
}

fun <T> lumenTween(duration: Int = Durations.MEDIUM) = tween<T>(duration, easing = LumenEasing)

fun <T> sheetSpring(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

val LocalReduceMotion = staticCompositionLocalOf { false }

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
    val side = 20.dp
}

object Radii {
    val listCover = 6.dp
    val card = 14.dp
    val sheet = 28.dp
    val miniPlayer = 20.dp
}
