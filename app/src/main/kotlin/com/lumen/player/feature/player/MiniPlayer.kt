package com.lumen.player.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.trackThumb

// floating pill: tap or swipe up to expand, swipe left/right to skip
@Composable
fun MiniPlayer(track: TrackRow, viewModel: PlayerViewModel, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()
    val position by remember { viewModel.position(500) }.collectAsStateWithLifecycle(0L)
    val accent = LocalAccent.current.accent
    val haptics = LocalHapticFeedback.current
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(Radii.miniPlayer))
            .background(LumenColors.ink2)
            .drawBehind {
                drawRect(accent, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = Size(size.width * progress, 2.dp.toPx()))
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -80) viewModel.connection.next() else if (total > 80) viewModel.connection.previous()
                    },
                ) { _, drag -> total += drag }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -40) onExpand() }) { _, drag -> total += drag }
            }
            .clickable(onClick = onExpand)
            .testTag("mini_player")
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(trackThumb(track.id, track.hasCover), track.title, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistLine.orEmpty(), style = LumenType.label, color = LumenColors.paper2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                viewModel.connection.playPause()
            },
            modifier = Modifier.testTag("mini_play_pause"),
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = LumenColors.paper)
        }
    }
}
