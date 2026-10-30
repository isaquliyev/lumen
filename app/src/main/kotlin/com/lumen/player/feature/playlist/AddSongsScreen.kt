package com.lumen.player.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.util.normalize
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.PlaylistRepository
import com.lumen.player.data.TrackRepository
import com.lumen.player.feature.shell.AddSongsRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickerState(val songs: List<TrackRow> = emptyList(), val inPlaylist: Set<String> = emptySet())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddSongsViewModel @Inject constructor(
    handle: SavedStateHandle,
    tracks: TrackRepository,
    focus: FocusRepository,
    artistDao: ArtistDao,
    private val playlists: PlaylistRepository,
    private val events: AppEvents,
) : ViewModel() {
    val playlistId = handle.toRoute<AddSongsRoute>().playlistId
    val query = MutableStateFlow("")
    val selected = MutableStateFlow<List<String>>(emptyList())

    // "Only {focus artist}" chip, on by default while Focus is active
    val onlyFocus = MutableStateFlow(focus.isActive)
    val focusName: StateFlow<String?> = focus.focus.flatMapLatest { f ->
        if (f.off) flowOf(null) else artistDao.observeByIds(f.idList).map { list -> list.joinToString(" & ") { it.name } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val indexed = combine(tracks.allSongs, tracks.songs.map { list -> list.map { it.id }.toSet() }) { all, focused ->
        all.sortedBy { it.sortTitle }.map { Triple(it, normalize("${it.title} ${it.artistLine} ${it.albumTitle}"), it.id in focused) }
    }

    val state: StateFlow<PickerState> = combine(
        indexed, query, onlyFocus, playlists.observeEntries(playlistId).map { list -> list.map { it.trackId }.toSet() },
    ) { list, q, only, inPlaylist ->
        val terms = normalize(q).split(' ').filter { it.isNotEmpty() }
        PickerState(
            list.filter { (_, key, focused) -> (!only || focused) && terms.all { key.contains(it) } }.map { it.first },
            inPlaylist,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerState())

    fun toggle(id: String) {
        selected.value = selected.value.let { if (id in it) it - id else it + id }
    }

    fun add(onDone: () -> Unit) = viewModelScope.launch {
        val added = playlists.add(playlistId, selected.value)
        events.show(if (added == 1) "Added 1 song" else "Added $added songs")
        onDone()
    }
}

@Composable
fun AddSongsScreen(viewModel: AddSongsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val onlyFocus by viewModel.onlyFocus.collectAsStateWithLifecycle()
    val focusName by viewModel.focusName.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val accent = LocalAccent.current.accent

    Column(Modifier.fillMaxSize().background(LumenColors.ink).statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = Space.side, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.Close, "Close", tint = LumenColors.paper) }
            Text("Add songs", style = LumenType.title, color = LumenColors.paper)
        }
        Row(
            Modifier.padding(horizontal = Space.side, vertical = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                .background(LumenColors.ink2).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = LumenColors.paper3)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search your library", style = LumenType.body, color = LumenColors.paper3)
                BasicTextField(query, { viewModel.query.value = it }, singleLine = true, textStyle = LumenType.body.copy(color = LumenColors.paper),
                    cursorBrush = SolidColor(accent), modifier = Modifier.fillMaxWidth().testTag("picker_search"))
            }
        }
        focusName?.let { name ->
            Row(
                Modifier.padding(horizontal = Space.side).clip(CircleShape)
                    .background(if (onlyFocus) accent.copy(alpha = 0.16f) else LumenColors.ink3)
                    .clickable { viewModel.onlyFocus.value = !onlyFocus }
                    .padding(horizontal = 14.dp, vertical = 8.dp).testTag("picker_only_focus"),
            ) { Text("Only $name", style = LumenType.label, color = if (onlyFocus) accent else LumenColors.paper2) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.songs, key = { it.id }) { track ->
                val already = track.id in state.inPlaylist
                TrackRowItem(
                    track,
                    onClick = { if (!already) viewModel.toggle(track.id) },
                    trailing = {
                        Checkbox(
                            checked = already || track.id in selected,
                            enabled = !already,
                            onCheckedChange = { viewModel.toggle(track.id) },
                            colors = CheckboxDefaults.colors(checkedColor = accent, checkmarkColor = LumenColors.ink, uncheckedColor = LumenColors.paper3),
                            modifier = Modifier.testTag("pick_${track.id}"),
                        )
                    },
                )
            }
        }
        PillButton(
            if (selected.isEmpty()) "Pick songs" else "Add ${selected.size} ${if (selected.size == 1) "song" else "songs"}",
            { viewModel.add { nav.back() } },
            Modifier.padding(Space.side).padding(bottom = LocalBottomSpace.current).fillMaxWidth().testTag("picker_add"),
            enabled = selected.isNotEmpty(),
        )
    }
}
