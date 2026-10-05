package com.lumen.player.feature.player

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.LocalReduceMotion
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.util.activeLineIndex
import com.lumen.player.data.TrackLyrics
import kotlin.math.abs

// full-screen lyrics: synced lines follow the song, tap a line to seek there
@Composable
fun LyricsScreen(track: TrackRow, lyrics: TrackLyrics?, viewModel: PlayerViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LumenColors.ink0).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("lyrics_close")) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = LumenColors.paper)
            }
            Column(Modifier.weight(1f)) {
                Text(track.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1)
                Text(track.artistLine.orEmpty(), style = LumenType.label, color = LumenColors.paper2, maxLines = 1)
            }
        }
        val parsed = lyrics?.parsed
        when {
            lyrics?.kind == LyricsKind.SYNCED && parsed?.synced == true -> SyncedLyrics(parsed.lines, track.lyricsOffsetMs, viewModel)
            parsed != null && parsed.lines.isNotEmpty() -> Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("NOT SYNCED", style = LumenType.overline, color = LumenColors.paper3)
                Spacer(Modifier.height(12.dp))
                parsed.lines.forEach { Text(it.text, style = LumenType.plainLyric, color = LumenColors.paper) }
                Spacer(Modifier.height(48.dp))
            }
            else -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (lyrics?.kind == LyricsKind.INSTRUMENTAL) "An instrumental. Just listen." else "No lyrics for this one. Just listen.",
                    style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<com.lumen.player.core.util.LyricLine>, offsetMs: Long, viewModel: PlayerViewModel) {
    val position by remember { viewModel.position(100) }.collectAsStateWithLifecycle(0L)
    val active = activeLineIndex(lines, position + offsetMs)
    val listState = rememberLazyListState()
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    var autoScrolling by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !autoScrolling) lastUserScroll = System.currentTimeMillis()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = with(LocalDensity.current) { maxHeight.toPx() }
        // the active line sits about 35 % from the top; manual scrolling pauses this for 3 s
        LaunchedEffect(active) {
            if (active >= 0 && System.currentTimeMillis() - lastUserScroll > 3_000) {
                autoScrolling = true
                if (reduceMotion) listState.scrollToItem(active, -(viewport * 0.35f).toInt())
                else listState.animateScrollToItem(active, -(viewport * 0.35f).toInt())
                autoScrolling = false
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(top = 24.dp, bottom = maxHeight * 0.6f), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val distance = abs(index - active)
                val alpha = when {
                    index == active -> 1f
                    distance == 1 -> 0.45f
                    else -> 0.2f
                }
                val scale by animateFloatAsState(if (index == active) 1f else 0.96f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "line")
                val blurred = distance > 1 && Build.VERSION.SDK_INT >= 31
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.connection.seekTo((line.timeMs - offsetMs).coerceAtLeast(0)) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("lyric_$index"),
                ) {
                    if (line.text.isBlank()) {
                        InstrumentalDots(index == active)
                    } else {
                        Text(
                            line.text,
                            style = LumenType.lyric,
                            color = if (index == active) LumenColors.paper else LumenColors.paper.copy(alpha = alpha),
                            modifier = Modifier.scale(scale).then(if (blurred) Modifier.blur(2.dp) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstrumentalDots(active: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (active && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "dots")
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha").value
    } else if (active) 1f else 0.3f
    Text("♪ ♪ ♪", style = LumenType.lyric, color = LumenColors.paper, modifier = Modifier.alpha(pulse))
}
