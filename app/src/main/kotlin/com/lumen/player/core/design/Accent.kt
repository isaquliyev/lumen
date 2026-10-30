package com.lumen.player.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.hsl

// colours are tuned for the dark theme; in light mode the accent gets darker
// (readable on white) and the glow gets pale
@Immutable
data class Accent(val darkAccent: Color, val darkGlow: Color, val darkDeep: Color) {
    private val lightAccent = Color(darkenFor(darkAccent.toArgb()))
    private val lightGlow = Color(withLightness(darkGlow.toArgb(), 0.84f))
    private val lightDeep = Color(withLightness(darkDeep.toArgb(), 0.95f))

    val accent get() = if (LumenColors.light) lightAccent else darkAccent
    val glow get() = if (LumenColors.light) lightGlow else darkGlow
    val deep get() = if (LumenColors.light) lightDeep else darkDeep
}

private fun withLightness(color: Int, light: Float): Int {
    val values = FloatArray(3)
    ColorUtils.colorToHSL(color, values)
    values[2] = light
    return ColorUtils.HSLToColor(values)
}

private fun darkenFor(color: Int): Int {
    val values = FloatArray(3)
    ColorUtils.colorToHSL(color, values)
    var result = color
    while (ColorUtils.calculateContrast(result, android.graphics.Color.WHITE) < 3.5 && values[2] > 0.2f) {
        values[2] -= 0.04f
        result = ColorUtils.HSLToColor(values)
    }
    return result
}

private const val EMBER_HUE = 11f

val DefaultAccent = Accent(
    darkAccent = LumenColors.ember,
    darkGlow = Color(hsl(EMBER_HUE, 0.70f, 0.32f)),
    darkDeep = Color(hsl(EMBER_HUE, 0.60f, 0.07f)),
)

fun accentFor(tone: CoverTone?): Accent {
    if (tone == null) return DefaultAccent
    val sat = tone.saturation
    var light = 0.64f
    var accent = hsl(tone.hue, sat.coerceIn(0.50f, 0.85f), light)
    while (ColorUtils.calculateContrast(accent, android.graphics.Color.BLACK) < 4.5 && light < 0.96f) {
        light += 0.04f
        accent = hsl(tone.hue, sat.coerceIn(0.50f, 0.85f), light)
    }
    return Accent(
        darkAccent = Color(accent),
        darkGlow = Color(hsl(tone.hue, (sat * 0.9f).coerceIn(0.35f, 0.70f), 0.32f)),
        darkDeep = Color(hsl(tone.hue, minOf(0.60f, sat + 0.06f), 0.07f)),
    )
}
