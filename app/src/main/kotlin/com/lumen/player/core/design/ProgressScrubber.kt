package com.lumen.player.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// plain progress bar; played part in the accent, drag or tap to seek
@Composable
fun ProgressScrubber(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragging: (Float?) -> Unit = {},
) {
    val accent = LocalAccent.current.accent
    val haptics = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: progress).coerceIn(0f, 1f)
    val unplayed = LumenColors.paper3.copy(alpha = 0.5f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .testTag("scrubber")
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                var lastTick = -1
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        drag = (offset.x / size.width).coerceIn(0f, 1f)
                        lastTick = (drag!! * 10).toInt()
                        onDragging(drag)
                    },
                    onDragEnd = {
                        drag?.let(onSeek)
                        drag = null
                        onDragging(null)
                    },
                    onDragCancel = {
                        drag = null
                        onDragging(null)
                    },
                ) { change, _ ->
                    val value = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag = value
                    onDragging(value)
                    val tick = (value * 10).toInt()
                    if (tick != lastTick) {
                        lastTick = tick
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }
            },
    ) {
        val thickness = 4.dp.toPx()
        val top = (size.height - thickness) / 2
        val corner = CornerRadius(thickness / 2)
        drawRoundRect(unplayed, Offset(0f, top), Size(size.width, thickness), corner)
        drawRoundRect(accent, Offset(0f, top), Size(size.width * shown, thickness), corner)
        drawCircle(accent, radius = 7.dp.toPx(), center = Offset(size.width * shown, size.height / 2))
    }
}
