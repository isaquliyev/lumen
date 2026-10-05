package com.lumen.player.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.SectionHeader
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.playback.QueueEntry
import com.lumen.player.playback.SleepState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface QueueItem {
    val key: String
    data class Header(val title: String, val action: String? = null) : QueueItem { override val key = "h_$title" }
    data class Song(val entry: QueueEntry, val movable: Boolean) : QueueItem { override val key = "s_${entry.index}_${entry.mediaId}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val queue by viewModel.queueUi.collectAsStateWithLifecycle()
    val context by viewModel.context.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val built = remember(queue, context) {
        buildList {
            queue.current?.let {
                add(QueueItem.Header("Now playing"))
                add(QueueItem.Song(it, false))
            }
            if (queue.upNextUser.isNotEmpty()) {
                add(QueueItem.Header("Next in queue", "Clear"))
                queue.upNextUser.forEach { add(QueueItem.Song(it, true)) }
            }
            if (queue.upNextContext.isNotEmpty()) {
                add(QueueItem.Header("Next from: ${context.name}"))
                queue.upNextContext.take(300).forEach { add(QueueItem.Song(it, true)) }
            }
        }
    }
    var items by remember(built) { mutableStateOf(built) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { it.key == from.key }
        val toIndex = items.indexOfFirst { it.key == to.key }
        val target = items.getOrNull(toIndex)
        if (fromIndex >= 0 && target is QueueItem.Song && target.movable) {
            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LumenColors.ink2,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is QueueItem.Header -> SectionHeader(item.title, action = item.action, onAction = { viewModel.clearUserQueue() })
                    is QueueItem.Song -> {
                        val row = item.entry.row ?: return@items
                        if (!item.movable) {
                            TrackRowItem(row, onClick = {}, playing = true)
                            return@items
                        }
                        ReorderableItem(reorder, key = item.key) { dragging ->
                            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.connection.removeItem(item.entry.index)
                                    true
                                } else false
                            })
                            SwipeToDismissBox(
                                state = dismiss,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(Modifier.fillMaxSize().background(LumenColors.danger.copy(alpha = 0.25f)).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                        Icon(Icons.Rounded.Delete, "Remove", tint = LumenColors.danger)
                                    }
                                },
                            ) {
                                Row(Modifier.background(if (dragging) LumenColors.ink3 else LumenColors.ink2), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        TrackRowItem(row, onClick = { viewModel.connection.seekToIndex(item.entry.index) })
                                    }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(
                                            onDragStarted = {
                                                dragFrom = item.entry.index
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                // the new absolute index is the position among songs after the current one
                                                val songs = items.filterIsInstance<QueueItem.Song>().filter { it.movable }
                                                val newPosition = songs.indexOfFirst { it.key == item.key }
                                                val to = queue.currentIndex + 1 + newPosition
                                                if (dragFrom >= 0 && newPosition >= 0 && to != dragFrom) viewModel.connection.moveItem(dragFrom, to)
                                                dragFrom = -1
                                            },
                                        ).testTag("queue_drag_${item.entry.index}"),
                                    ) { Icon(Icons.Rounded.DragHandle, "Reorder", tint = LumenColors.paper2) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(viewModel: PlayerViewModel, sleep: SleepState, onDismiss: () -> Unit) {
    val accent = LocalAccent.current.accent
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.ink2,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).heightIn(max = 520.dp)) {
            Text("Sleep timer", style = LumenType.title, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side, vertical = 8.dp))
            (if (com.lumen.player.BuildConfig.DEBUG) listOf(1, 15, 30, 45, 60, 90) else listOf(15, 30, 45, 60, 90)).forEach { minutes ->
                SleepOption(if (minutes == 1) "1 minute (debug)" else "$minutes minutes", false) { viewModel.sleepIn(minutes); onDismiss() }
            }
            SleepOption("End of this song", sleep == SleepState.EndOfSong) { viewModel.sleepAtEndOfSong(); onDismiss() }
            if (sleep != SleepState.Off) {
                Text(
                    "Turn off timer",
                    style = LumenType.body,
                    color = accent,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.cancelSleep(); onDismiss() }.padding(horizontal = Space.side, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SleepOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.side, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = LumenType.body, color = LumenColors.paper, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = LocalAccent.current.accent)
    }
}
