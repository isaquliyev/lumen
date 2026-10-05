package com.lumen.player.core.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.util.formatClock

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: TrackRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    playing: Boolean = false,
    selected: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val accent = LocalAccent.current.accent
    val removed = track.deletedAt != null
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (selected) LumenColors.ink3 else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("track_${track.id}")
            .padding(start = Space.side, end = if (onMore != null) 4.dp else Space.side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        CoverImage(trackThumb(track.id, track.hasCover), track.title, Modifier.size(CoverSmall))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = LumenType.body,
                color = when {
                    removed -> LumenColors.paper3
                    playing -> accent
                    else -> LumenColors.paper
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (removed) "Removed" else track.artistLine.orEmpty(),
                style = LumenType.label,
                color = LumenColors.paper2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing() else {
            Text(formatClock(track.durationMs), style = LumenType.numeric, color = LumenColors.paper3)
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.testTag("more_${track.id}")) {
                Icon(Icons.Rounded.MoreHoriz, "More", tint = LumenColors.paper2)
            }
        }
    }
}
