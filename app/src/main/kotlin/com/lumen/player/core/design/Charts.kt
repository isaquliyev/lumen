package com.lumen.player.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 0 -> 1 once when a chart first appears (instant with Reduce motion)
@Composable
fun appearProgress(key: Any?): Float {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(key) { if (!reduceMotion) progress.animateTo(1f, tween(Durations.LONG * 2, easing = LumenEasing)) }
    return progress.value
}

@Composable
fun CountUpText(value: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier, format: (Long) -> String = { "%,d".format(it) }) {
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableLongStateOf(if (reduceMotion) value else 0L) }
    LaunchedEffect(value) {
        if (reduceMotion) {
            shown = value
            return@LaunchedEffect
        }
        val start = shown
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(900, easing = LumenEasing)) { shown = start + ((value - start) * this.value).toLong() }
    }
    Text(format(shown), style = style, color = color, modifier = modifier)
}

// 24-hour radial chart, midnight at the top, a marker on the current hour
@Composable
fun RadialClock(values: LongArray, currentHour: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.accent
    val progress = appearProgress(values.sum())
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    Box(modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val center = Offset(size.width / 2, size.height / 2)
            val inner = size.minDimension * 0.18f
            val outer = size.minDimension * 0.46f
            drawCircle(LumenColors.line, inner, center, style = Stroke(1.dp.toPx()))
            for (hour in 0 until 24) {
                val angle = (hour / 24.0 * 2 * PI - PI / 2).toFloat()
                val length = (outer - inner) * (values[hour].toFloat() / max) * progress
                val start = Offset(center.x + cos(angle) * (inner + 4.dp.toPx()), center.y + sin(angle) * (inner + 4.dp.toPx()))
                val end = Offset(center.x + cos(angle) * (inner + 4.dp.toPx() + length.coerceAtLeast(2.dp.toPx())), center.y + sin(angle) * (inner + 4.dp.toPx() + length.coerceAtLeast(2.dp.toPx())))
                drawLine(if (values[hour] > 0) accent else LumenColors.ink3, start, end, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                if (hour == currentHour) {
                    val dot = Offset(center.x + cos(angle) * (outer + 10.dp.toPx()), center.y + sin(angle) * (outer + 10.dp.toPx()))
                    drawCircle(LumenColors.paper, 3.dp.toPx(), dot)
                }
            }
        }
    }
}

// vertical bars with labels underneath (Mon–Sun)
@Composable
fun BarRow(values: LongArray, labels: List<String>, modifier: Modifier = Modifier, highlight: Int = -1) {
    val accent = LocalAccent.current.accent
    val progress = appearProgress(values.sum())
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(22.dp).height(110.dp)) {
                    val h = (size.height * value / max * progress).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        if (i == highlight || highlight < 0) accent else accent.copy(alpha = 0.45f),
                        Offset(0f, size.height - h), Size(size.width, h), CornerRadius(6.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(labels.getOrElse(i) { "" }, style = LumenType.numeric, color = LumenColors.paper3)
            }
        }
    }
}

// horizontal bars with a name and percentage
@Composable
fun HBarList(items: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.accent
    val progress = appearProgress(items)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (name, fraction) ->
            Column {
                Row {
                    Text(name, style = LumenType.label, color = LumenColors.paper, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(fraction * 100).toInt()} %", style = LumenType.numeric, color = LumenColors.paper2)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawRoundRect(LumenColors.ink3, cornerRadius = CornerRadius(3.dp.toPx()))
                    drawRoundRect(accent, size = Size(size.width * fraction * progress, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                }
            }
        }
    }
}

// month grid (7 columns) or a GitHub-style year grid (weeks as columns), 5 intensity levels
@Composable
fun CalendarHeatmap(days: List<LocalDate>, values: Map<LocalDate, Long>, yearMode: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.accent
    val max = (values.values.maxOrNull() ?: 0L).coerceAtLeast(1)
    fun level(day: LocalDate): Color {
        val v = values[day] ?: 0L
        if (v <= 0) return LumenColors.ink3
        val step = ((v.toFloat() / max) * 4).toInt().coerceIn(0, 3)
        return accent.copy(alpha = listOf(0.3f, 0.5f, 0.75f, 1f)[step])
    }
    if (days.isEmpty()) return
    if (!yearMode) {
        val lead = (days.first().dayOfWeek.value - 1)
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = LumenType.numeric, color = LumenColors.paper3, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
                }
            }
            val cells: List<LocalDate?> = List(lead) { null } + days
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { i ->
                        val day = week.getOrNull(i)
                        Canvas(Modifier.weight(1f).aspectRatio(1f).padding(vertical = 2.dp)) {
                            if (day != null) drawRoundRect(level(day), cornerRadius = CornerRadius(4.dp.toPx()))
                        }
                    }
                }
            }
        }
    } else {
        val recent = days.takeLast(371)
        val lead = recent.first().dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells: List<LocalDate?> = List(lead) { null } + recent
        val weeks = cells.chunked(7)
        Canvas(modifier.fillMaxWidth().aspectRatio(weeks.size / 7f)) {
            val cell = size.width / weeks.size
            weeks.forEachIndexed { w, week ->
                week.forEachIndexed { d, day ->
                    if (day != null) drawRoundRect(
                        level(day), Offset(w * cell + cell * 0.1f, d * cell + cell * 0.1f), Size(cell * 0.8f, cell * 0.8f), CornerRadius(cell * 0.2f),
                    )
                }
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.accent
    val progress = appearProgress(values)
    if (values.size < 2) return
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1f
    Canvas(modifier.fillMaxWidth().height(80.dp)) {
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height * 0.9f * progress
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, accent.copy(alpha = 0.12f))
    }
}
