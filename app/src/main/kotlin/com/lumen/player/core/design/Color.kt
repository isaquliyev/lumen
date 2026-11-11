package com.lumen.player.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// "ink" is the background side and "paper" the text side; both flip in light mode.
// light is snapshot state, so everything that reads a colour redraws when the theme changes
object LumenColors {
    var light by mutableStateOf(false)

    val ink get() = if (light) Color(0xFFF6F3EE) else Color(0xFF0B0B0C)
    val ink0 get() = if (light) Color(0xFFFFFFFF) else Color(0xFF000000)
    val ink2 get() = if (light) Color(0xFFECE8E1) else Color(0xFF141416)
    val ink3 get() = if (light) Color(0xFFE0DBD2) else Color(0xFF1C1C1F)
    val line get() = if (light) Color(0xFFD5CFC5) else Color(0xFF27272B)
    val paper get() = if (light) Color(0xFF17161A) else Color(0xFFF3EFE8)
    val paper2 get() = if (light) Color(0xFF5C5851) else Color(0xFFA8A39B)
    val paper3 get() = if (light) Color(0xFF8C877E) else Color(0xFF6B675F)
    val ember = Color(0xFFFF5A36)
    val emberSoft = ember.copy(alpha = 0.14f)
    val positive get() = if (light) Color(0xFF2E9E4F) else Color(0xFF7BD88F)
    val danger get() = if (light) Color(0xFFD63A3A) else Color(0xFFFF6B6B)
}
