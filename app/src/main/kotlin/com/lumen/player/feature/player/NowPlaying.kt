package com.lumen.player.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.trackCover
import com.lumen.player.core.util.formatClock

// basic Now Playing (P4); the full design arrives in P8
@Composable
fun NowPlaying(track: TrackRow, viewModel: PlayerViewModel, onCollapse: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatMode.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val context by viewModel.context.collectAsStateWithLifecycle()
    val position by remember { viewModel.position() }.collectAsStateWithLifecycle(0L)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val accent = LocalAccent.current.accent
    val haptics = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .background(LumenColors.ink0)
            .clickable(enabled = false) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, modifier = Modifier.testTag("player_collapse")) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = LumenColors.paper)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYING FROM", style = LumenType.overline, color = LumenColors.paper3)
                Text(context.name, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.weight(1f))
        CoverImage(trackCover(track.id, track.hasCover), track.title, Modifier.fillMaxWidth().aspectRatio(1f), radius = Radii.card)
        Spacer(Modifier.height(28.dp))
        Text(track.title, style = LumenType.display, color = LumenColors.paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artistLine.orEmpty(), style = LumenType.label, color = LumenColors.paper2)
        Spacer(Modifier.height(16.dp))
        val shown = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { viewModel.connection.seekTo((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = LumenColors.ink3),
            modifier = Modifier.testTag("player_seek"),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock((shown * duration).toLong()), style = LumenType.numeric, color = LumenColors.paper3)
            Spacer(Modifier.weight(1f))
            Text("-" + formatClock(duration - (shown * duration).toLong()), style = LumenType.numeric, color = LumenColors.paper3)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.connection.toggleShuffle() }, modifier = Modifier.testTag("player_shuffle")) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) accent else LumenColors.paper2)
            }
            IconButton(onClick = { viewModel.connection.previous() }, modifier = Modifier.size(56.dp).testTag("player_previous")) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = LumenColors.paper, modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LumenColors.paper)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        viewModel.connection.playPause()
                    }
                    .testTag("player_play_pause"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = LumenColors.ink, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { viewModel.connection.next() }, modifier = Modifier.size(56.dp).testTag("player_next")) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = LumenColors.paper, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { viewModel.connection.cycleRepeat() }, modifier = Modifier.testTag("player_repeat")) {
                Icon(
                    if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat",
                    tint = if (repeat == Player.REPEAT_MODE_OFF) LumenColors.paper2 else accent,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
