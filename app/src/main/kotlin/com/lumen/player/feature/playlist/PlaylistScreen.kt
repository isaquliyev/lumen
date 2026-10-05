package com.lumen.player.feature.playlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.GlowBackground
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.SortChip
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.design.accentFor
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.formatCount
import com.lumen.player.core.util.formatLength
import com.lumen.player.core.util.seededTone
import com.lumen.player.feature.library.ConfirmDialog
import com.lumen.player.feature.library.LocalTrackMenu
import com.lumen.player.feature.library.lumenFieldColors
import com.lumen.player.feature.shell.AddSongsRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.bottomPadding
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen(viewModel: PlaylistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val playlist = state.playlist
    val nav = LocalNav.current
    val player = LocalPlayer.current
    val menu = LocalTrackMenu.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setCover) }

    if (playlist == null) {
        if (state.loaded) nav.back()
        return
    }
    val first = state.all.firstOrNull { it.toneHue != null }
    val tone = remember(first?.id) { first?.let { CoverTone(it.toneHue!!, it.toneSat ?: 0.5f) } ?: seededTone(playlist.name) }
    val accent = remember(tone) { accentFor(tone) }
    val songs = state.shown

    // local copy while dragging; written back in one transaction when the drag ends
    var order by remember(songs, editing) { mutableStateOf(songs) }
    val listState = rememberLazyListState()
    val headerCount = 1
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index - headerCount
        val toIndex = to.index - headerCount
        if (fromIndex in order.indices && toIndex in order.indices) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    CompositionLocalProvider(LocalAccent provides accent) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
            item(key = "header") {
                GlowBackground(accent.glow, Modifier.fillMaxWidth(), centerY = 0.4f, radiusFactor = 0.8f) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { nav.back() }, modifier = Modifier.testTag("playlist_back")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LumenColors.paper)
                            }
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("playlist_menu")) {
                                    Icon(Icons.Rounded.MoreVert, "More", tint = LumenColors.paper)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = LumenColors.ink2) {
                                    MenuItem("Edit name & description") { renaming = true; menuOpen = false }
                                    MenuItem("Change cover") {
                                        menuOpen = false
                                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    if (playlist.hasCustomCover) MenuItem("Remove custom cover") { viewModel.removeCover(); menuOpen = false }
                                    MenuItem(if (playlist.pinned) "Unpin" else "Pin") { viewModel.setPinned(!playlist.pinned); menuOpen = false }
                                    MenuItem("Delete playlist", danger = true) { deleting = true; menuOpen = false }
                                }
                            }
                        }
                        PlaylistCover(
                            playlist.id, playlist.name, playlist.hasCustomCover, playlist.updatedAt, state.coverTrackIds,
                            Modifier.size(200.dp), radius = Radii.card,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(playlist.name, style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Space.side).testTag("playlist_title"))
                        if (playlist.description.isNotBlank()) {
                            Text(playlist.description, style = LumenType.body, color = LumenColors.paper2, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Space.side, vertical = 4.dp))
                        }
                        Text(
                            "${formatCount(state.all.size, "song")} · ${formatLength(state.all.sumOf { it.durationMs })}",
                            style = LumenType.label, color = LumenColors.paper2,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Play", { player.play(songs, 0, PlayContext.PLAYLIST, playlist.id, playlist.name) },
                                icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty(), modifier = Modifier.testTag("playlist_play"))
                            PillButton("Shuffle", { player.play(songs, -1, PlayContext.PLAYLIST, playlist.id, playlist.name, shuffle = true) },
                                style = PillStyle.OUTLINED, icon = Icons.Rounded.Shuffle, enabled = songs.isNotEmpty(), modifier = Modifier.testTag("playlist_shuffle"))
                            PillButton("Add songs", { nav.open(AddSongsRoute(playlist.id)) }, style = PillStyle.OUTLINED,
                                icon = Icons.Rounded.Add, modifier = Modifier.testTag("playlist_add_songs"))
                        }
                        Spacer(Modifier.height(12.dp))
                        state.focusNames?.let { names ->
                            Row(
                                Modifier.padding(horizontal = Space.side).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(accent.accent.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Showing ${songs.size} of ${state.all.size} · Focus: $names", style = LumenType.label,
                                    color = LumenColors.paper, modifier = Modifier.weight(1f))
                                Text("Show all", style = LumenType.label, color = accent.accent,
                                    modifier = Modifier.clickable { viewModel.showAll.value = true }.padding(4.dp).testTag("playlist_show_all"))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (state.all.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.side), verticalAlignment = Alignment.CenterVertically) {
                                if (!editing) SortChip(PlaylistSort.entries, sort, { it.label }, { viewModel.sort.value = it })
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (editing) "Done" else "Edit",
                                    style = LumenType.body,
                                    color = accent.accent,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { viewModel.editing.value = !editing }
                                        .padding(8.dp).testTag("playlist_edit"),
                                )
                            }
                        }
                    }
                }
            }
            if (state.all.isEmpty()) {
                item(key = "empty") {
                    EmptyState("This playlist is empty.", "Add songs from your library.", action = "Add songs", onAction = { nav.open(AddSongsRoute(playlist.id)) })
                }
            }
            if (editing) {
                itemsIndexed(order, key = { _, it -> it.id }) { _, track ->
                    ReorderableItem(reorder, key = track.id) { dragging ->
                        val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.remove(track)
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
                            EditRow(track, dragging) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            viewModel.reorder(order.map { it.id })
                                        },
                                    ).testTag("drag_${track.id}"),
                                ) { Icon(Icons.Rounded.DragHandle, "Reorder", tint = LumenColors.paper2) }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, it -> it.id }) { index, track ->
                    TrackRowItem(track, onClick = { player.play(songs, index, PlayContext.PLAYLIST, playlist.id, playlist.name) }, onMore = { menu.open(track) })
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(playlist.name, playlist.description, onDismiss = { renaming = false }) { name, description ->
            viewModel.rename(name, description); renaming = false
        }
    }
    if (deleting) {
        ConfirmDialog("Delete “${playlist.name}”?", "The songs stay in your library.", "Delete", onDismiss = { deleting = false }) {
            deleting = false
            viewModel.delete { nav.back() }
        }
    }
}

@Composable
private fun EditRow(track: TrackRow, dragging: Boolean, handle: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (dragging) LumenColors.ink3 else LumenColors.ink),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { TrackRowItem(track, onClick = {}) }
        handle()
    }
}

@Composable
private fun MenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = if (danger) LumenColors.danger else LumenColors.paper) }, onClick = onClick)
}

@Composable
private fun RenameDialog(name: String, description: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var newName by remember { mutableStateOf(name) }
    var newDescription by remember { mutableStateOf(description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.ink2,
        title = { Text("Edit playlist", style = LumenType.title, color = LumenColors.paper) },
        text = {
            Column {
                OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true, colors = lumenFieldColors(),
                    modifier = Modifier.testTag("rename_name"))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newDescription, { newDescription = it }, label = { Text("Description") }, colors = lumenFieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(newName, newDescription) }, enabled = newName.isNotBlank(), modifier = Modifier.testTag("rename_save")) {
                Text("Save", color = LocalAccent.current.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LumenColors.paper2) } },
    )
}
