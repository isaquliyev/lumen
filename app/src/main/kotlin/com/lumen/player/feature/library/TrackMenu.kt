package com.lumen.player.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.trackThumb
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.TrackActions
import com.lumen.player.data.TrackRepository
import com.lumen.player.download.CandidateUi
import com.lumen.player.feature.add.ChooseAudioSheet
import com.lumen.player.feature.shell.AlbumRoute
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

fun interface TrackMenuOpener {
    fun open(track: TrackRow)
}

val LocalTrackMenu = staticCompositionLocalOf { TrackMenuOpener { } }

// opens the "Add to playlist" sheet for these track ids (set by the playlist feature)
val LocalAddToPlaylist = staticCompositionLocalOf<(List<String>) -> Unit> { {} }

@HiltViewModel
class TrackMenuViewModel @Inject constructor(
    private val actions: TrackActions,
    private val tracks: TrackRepository,
    private val focus: FocusRepository,
    private val events: AppEvents,
) : ViewModel() {
    val track = MutableStateFlow<TrackRow?>(null)
    val candidates = MutableStateFlow<List<CandidateUi>?>(null)

    fun setLiked(row: TrackRow, liked: Boolean) = viewModelScope.launch {
        tracks.setLiked(row.id, liked)
        events.show(if (liked) "Added to Liked Songs" else "Removed from Liked Songs")
    }

    fun delete(ids: Collection<String>) = viewModelScope.launch {
        actions.delete(ids)
        events.show(if (ids.size == 1) "Deleted from device" else "Deleted ${ids.size} songs")
    }

    fun editInfo(row: TrackRow, title: String, genre: String) = viewModelScope.launch { tracks.editInfo(row.id, title, genre) }

    fun refetchLyrics(row: TrackRow) = viewModelScope.launch {
        events.show("Looking for lyrics…")
        val kind = runCatching { actions.refetchLyrics(row) }.getOrNull()
        events.show(
            when (kind) {
                LyricsKind.SYNCED -> "Found synced lyrics"
                LyricsKind.PLAIN -> "Found lyrics (not synced)"
                LyricsKind.INSTRUMENTAL -> "Marked as instrumental"
                else -> "No lyrics found"
            }
        )
    }

    fun setOffset(row: TrackRow, offset: Long) = viewModelScope.launch { tracks.setLyricsOffset(row.id, offset) }

    fun loadCandidates(row: TrackRow) = viewModelScope.launch {
        events.show("Searching YouTube…")
        candidates.value = runCatching { actions.audioCandidates(row) }.getOrElse {
            events.show("Couldn't search YouTube right now")
            null
        }
    }

    fun replaceAudio(row: TrackRow, videoId: String) = viewModelScope.launch {
        candidates.value = null
        actions.replaceAudio(row, videoId)
        events.show("Replacing audio… (see Add)")
    }

    fun focusOn(row: TrackRow) = viewModelScope.launch {
        val id = row.primaryArtistId ?: return@launch
        focus.only(id)
        events.show("Focus: ${row.artistLine?.substringBefore(',')}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuHost(viewModel: TrackMenuViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val track by viewModel.track.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TrackRow?>(null) }
    var offsetFor by remember { mutableStateOf<TrackRow?>(null) }
    var deleting by remember { mutableStateOf<TrackRow?>(null) }
    var replacing by remember { mutableStateOf<TrackRow?>(null) }
    val player = LocalPlayer.current
    val nav = LocalNav.current
    val addToPlaylist = LocalAddToPlaylist.current

    androidx.compose.runtime.CompositionLocalProvider(LocalTrackMenu provides TrackMenuOpener { viewModel.track.value = it }) {
        content()
    }

    track?.let { row ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun close() { viewModel.track.value = null }
        ModalBottomSheet(
            onDismissRequest = ::close,
            sheetState = state,
            containerColor = LumenColors.ink2,
            shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                Row(Modifier.padding(horizontal = Space.side, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(trackThumb(row.id, row.hasCover), row.title, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(row.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.artistLine.orEmpty(), style = LumenType.label, color = LumenColors.paper2, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", "menu_play_next") { player.playNext(listOf(row)); close() }
                MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", "menu_add_queue") { player.addToQueue(listOf(row)); close() }
                MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…", "menu_add_playlist") { close(); addToPlaylist(listOf(row.id)) }
                val liked = row.likedAt != null
                MenuRow(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like", "menu_like") {
                    viewModel.setLiked(row, !liked); close()
                }
                row.primaryArtistId?.let { artistId ->
                    MenuRow(Icons.Rounded.Person, "Go to artist", "menu_artist") { close(); nav.open(ArtistRoute(artistId)) }
                }
                row.albumId?.let { albumId ->
                    MenuRow(Icons.Rounded.Album, "Go to album", "menu_album") { close(); nav.open(AlbumRoute(albumId)) }
                }
                if (row.primaryArtistId != null) {
                    MenuRow(Icons.Rounded.CenterFocusStrong, "Focus on artist", "menu_focus") { viewModel.focusOn(row); close() }
                }
                MenuRow(Icons.Rounded.Edit, "Edit info", "menu_edit") { editing = row; close() }
                MenuRow(Icons.Rounded.Lyrics, "Lyrics: re-fetch", "menu_lyrics") { viewModel.refetchLyrics(row); close() }
                if (row.lyricsKind == LyricsKind.SYNCED) {
                    MenuRow(Icons.Rounded.Timer, "Lyrics: adjust offset", "menu_offset") { offsetFor = row; close() }
                }
                MenuRow(Icons.Rounded.SwapHoriz, "Wrong audio? Choose another", "menu_replace") {
                    replacing = row; viewModel.loadCandidates(row); close()
                }
                MenuRow(Icons.Rounded.Delete, "Delete from device", "menu_delete", danger = true) { deleting = row; close() }
            }
        }
    }

    editing?.let { row -> EditInfoDialog(row, onDismiss = { editing = null }) { title, genre -> viewModel.editInfo(row, title, genre); editing = null } }
    offsetFor?.let { row -> OffsetDialog(row, onDismiss = { offsetFor = null }) { viewModel.setOffset(row, it); offsetFor = null } }
    deleting?.let { row ->
        ConfirmDialog(
            title = "Delete “${row.title}”?",
            body = "The song file is removed from your phone. Your listening history stays.",
            confirm = "Delete",
            onDismiss = { deleting = null },
        ) { viewModel.delete(listOf(row.id)); deleting = null }
    }
    val replaceRow = replacing
    val list = candidates
    if (replaceRow != null && list != null) {
        ChooseAudioSheet(list, onDismiss = { replacing = null; viewModel.candidates.value = null }) { videoId ->
            viewModel.replaceAudio(replaceRow, videoId); replacing = null
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = Space.side, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) LumenColors.danger else LumenColors.paper2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = LumenType.body, color = if (danger) LumenColors.danger else LumenColors.paper)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.ink2,
        title = { Text(title, style = LumenType.title, color = LumenColors.paper) },
        text = { Text(body, style = LumenType.body, color = LumenColors.paper2) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("dialog_confirm")) { Text(confirm, color = LumenColors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LumenColors.paper2) } },
    )
}

@Composable
fun lumenFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalAccent.current.accent,
    unfocusedBorderColor = LumenColors.line,
    focusedTextColor = LumenColors.paper,
    unfocusedTextColor = LumenColors.paper,
    cursorColor = LocalAccent.current.accent,
    focusedLabelColor = LumenColors.paper2,
    unfocusedLabelColor = LumenColors.paper3,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)

@Composable
private fun EditInfoDialog(row: TrackRow, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(row.title) }
    var genre by remember { mutableStateOf(row.genre.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.ink2,
        title = { Text("Edit info", style = LumenType.title, color = LumenColors.paper) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, colors = lumenFieldColors())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true, colors = lumenFieldColors())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, genre) }, enabled = title.isNotBlank()) { Text("Save", color = LocalAccent.current.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LumenColors.paper2) } },
    )
}

@Composable
private fun OffsetDialog(row: TrackRow, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var offset by remember { mutableLongStateOf(row.lyricsOffsetMs) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumenColors.ink2,
        title = { Text("Lyrics offset", style = LumenType.title, color = LumenColors.paper) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset -= 100 }) { Text("−100 ms", color = LumenColors.paper) }
                Text("${if (offset > 0) "+" else ""}$offset ms", style = LumenType.title, color = LumenColors.paper, modifier = Modifier.weight(1f))
                TextButton(onClick = { offset += 100 }) { Text("+100 ms", color = LumenColors.paper) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(offset) }) { Text("Save", color = LocalAccent.current.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LumenColors.paper2) } },
    )
}
