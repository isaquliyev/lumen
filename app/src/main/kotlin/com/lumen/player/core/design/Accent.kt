package com.lumen.player.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.hsl

@Immutable
data class Accent(val accent: Color, val glow: Color, val deep: Color)

private const val EMBER_HUE = 11f

val DefaultAccent = Accent(
    accent = LumenColors.ember,
    glow = Color(hsl(EMBER_HUE, 0.70f, 0.32f)),
    deep = Color(hsl(EMBER_HUE, 0.60f, 0.07f)),
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
        accent = Color(accent),
        glow = Color(hsl(tone.hue, (sat * 0.9f).coerceIn(0.35f, 0.70f), 0.32f)),
        deep = Color(hsl(tone.hue, minOf(0.60f, sat + 0.06f), 0.07f)),
    )
}
