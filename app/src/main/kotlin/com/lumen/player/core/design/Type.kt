package com.lumen.player.core.design

import android.content.res.AssetManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.graphics.Typeface as AndroidTypeface

// Compose's variationSettings were ignored on HyperOS, so the axes are set on the platform Typeface
object LumenFonts {
    lateinit var assets: AssetManager

    fun init(assets: AssetManager) {
        this.assets = assets
    }
}

private fun variable(file: String, weight: Int, axes: String) = FontFamily(
    Typeface(
        AndroidTypeface.Builder(LumenFonts.assets, "fonts/$file")
            .setWeight(weight)
            .setFontVariationSettings("'wght' $weight$axes")
            .build()
    )
)

private fun fraunces(weight: Int, opsz: Int, soft: Int = 0) = variable("fraunces.ttf", weight, ", 'opsz' $opsz, 'SOFT' $soft")

private fun manrope(weight: Int) = variable("manrope.ttf", weight, "")

private fun style(family: FontFamily, weight: Int, size: Int, line: Int) =
    TextStyle(fontFamily = family, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp)

object LumenType {
    val displayHero = style(fraunces(400, 144, 50), 400, 72, 72)
    val displayLarge = style(fraunces(400, 96), 400, 44, 48)
    val display = style(fraunces(450, 48), 450, 30, 36)
    val lyric = style(fraunces(500, 36), 500, 28, 36)
    val title = style(manrope(650), 650, 20, 26)
    val body = style(manrope(550), 550, 16, 22)
    val label = style(manrope(500), 500, 13, 18)
    val overline = style(manrope(700), 700, 11, 14).copy(letterSpacing = 0.08.em)
    val numeric = style(manrope(600), 600, 12, 16).copy(fontFeatureSettings = "tnum")
    val plainLyric = style(manrope(550), 550, 20, 30)
}
