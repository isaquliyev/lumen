package com.lumen.player.core.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils

data class CoverTone(val hue: Float, val saturation: Float)

fun coverTone(bitmap: Bitmap): CoverTone? {
    val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
    val pixels = IntArray(48 * 48).also { small.getPixels(it, 0, 48, 0, 0, 48, 48) }
    val counts = IntArray(24)
    val weights = FloatArray(24)
    val sums = FloatArray(24)
    val hsl = FloatArray(3)
    for (pixel in pixels) {
        if (Color.alpha(pixel) < 128) continue
        ColorUtils.colorToHSL(pixel, hsl)
        val hue = hsl[0]
        val sat = hsl[1]
        val light = hsl[2]
        if (light < 0.10f || light > 0.95f || sat < 0.14f) continue
        val bucket = (hue / 15f).toInt() % 24
        counts[bucket]++
        weights[bucket] += sat * sat
        sums[bucket] += sat
    }
    val top = (0 until 24).filter { counts[it] > 0 }.maxByOrNull { weights[it] } ?: return null
    return CoverTone(top * 15f + 7.5f, (sums[top] / counts[top]).coerceIn(0.2f, 0.85f))
}

fun seededTone(text: String): CoverTone {
    var hash = 0
    for (c in text) hash = (hash * 31 + c.code) % 100_000
    return CoverTone((hash % 360).toFloat(), 0.55f)
}

fun hsl(hue: Float, sat: Float, light: Float): Int = ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))
