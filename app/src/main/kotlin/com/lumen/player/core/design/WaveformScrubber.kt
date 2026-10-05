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

// 96 bars from the analysed waveform; played bars in the accent, drag or tap to seek
@Composable
fun WaveformScrubber(
    waveform: ByteArray?,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragging: (Float?) -> Unit = {},
) {
    val accent = LocalAccent.current.accent
    val haptics = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val bars = remember(waveform) {
        if (waveform == null || waveform.isEmpty()) FloatArray(96) { 0.18f }
        else FloatArray(waveform.size) { ((waveform[it].toInt() and 0xFF) / 255f).coerceAtLeast(0.06f) }
    }
    val shown = (drag ?: progress).coerceIn(0f, 1f)
    val unplayed = LumenColors.paper3.copy(alpha = 0.5f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("waveform")
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
        val count = bars.size
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val maxHeight = size.height * 0.82f
        for (i in 0 until count) {
            val height = (bars[i] * maxHeight).coerceAtLeast(3.dp.toPx())
            val x = i * (barWidth + gap)
            val played = (i + 0.5f) / count <= shown
            drawRoundRect(
                color = if (played) accent else unplayed,
                topLeft = Offset(x, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
        drawCircle(accent, radius = 5.dp.toPx(), center = Offset(size.width * shown, size.height / 2))
    }
}
