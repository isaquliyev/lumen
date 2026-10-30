package com.lumen.player.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.db.PlaylistRow
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import com.lumen.player.core.util.formatCount
import com.lumen.player.data.PlaylistRepository
import com.lumen.player.data.SettingsRepository
import com.lumen.player.data.SmartKind
import com.lumen.player.data.SmartPlaylists
import com.lumen.player.feature.library.lumenFieldColors
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.PlaylistRoute
import com.lumen.player.feature.shell.SmartPlaylistRoute
import com.lumen.player.feature.shell.bottomPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val settings: SettingsRepository,
    smart: SmartPlaylists,
) : ViewModel() {
    val grid: StateFlow<Boolean> = settings.settings.map { it.playlistGrid }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setGrid(value: Boolean) = viewModelScope.launch { settings.setPlaylistGrid(value) }

    val playlists: StateFlow<List<PlaylistRow>> = repository.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // playlist id -> first 4 distinct cover track ids, for the mosaics
    val covers: StateFlow<Map<String, List<String>>> = repository.coverTracks
        .map { list -> list.groupBy({ it.playlistId }, { it.trackId }).mapValues { (_, ids) -> ids.distinct().take(4) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val smartCounts: StateFlow<Map<SmartKind, Int>> = combine(SmartKind.entries.map { kind -> smart.observe(kind).map { kind to it.size } }) { pairs ->
        pairs.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun defaultName() = repository.nextDefaultName()

    fun create(name: String, onCreated: (String) -> Unit) = viewModelScope.launch { onCreated(repository.create(name)) }
}

@Composable
fun PlaylistsTab(viewModel: PlaylistsViewModel = hiltViewModel()) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val counts by viewModel.smartCounts.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var creating by remember { mutableStateOf(false) }

    // the same entries are shown as a list or as a 3-column card grid
    val entries = buildList {
        add(
            PlaylistEntry("create", "Create playlist", null, false, "playlist_create", { creating = true }) { modifier ->
                Box(modifier.clip(RoundedCornerShape(6.dp)).background(LumenColors.ink3), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = LumenColors.paper)
                }
            }
        )
        SmartKind.entries.forEach { kind ->
            val count = counts[kind] ?: 0
            if (kind == SmartKind.LIKED || count > 0) {
                val subtitle = if (kind == SmartKind.LIKED) "Playlist · ${formatCount(count, "song")}" else "Smart playlist · ${formatCount(count, "song")}"
                add(PlaylistEntry(kind.name, kind.title, subtitle, false, "smart_${kind.name}", { nav.open(SmartPlaylistRoute(kind.name)) }) { modifier ->
                    SmartCover(kind, modifier)
                })
            }
        }
        playlists.forEach { playlist ->
            add(
                PlaylistEntry(
                    playlist.id, playlist.name, "Playlist · ${formatCount(playlist.trackCount, "song")}", playlist.pinned,
                    "playlist_${playlist.id}", { nav.open(PlaylistRoute(playlist.id)) },
                ) { modifier ->
                    PlaylistCover(playlist.id, playlist.name, playlist.hasCustomCover, playlist.updatedAt, covers[playlist.id].orEmpty(), modifier)
                }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { viewModel.setGrid(!grid) }, modifier = Modifier.testTag("playlists_view")) {
                Icon(
                    if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    if (grid) "Show as list" else "Show as cards",
                    tint = LumenColors.paper2,
                )
            }
        }
        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.side, end = Space.side, bottom = LocalBottomSpace.current + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItems(entries, key = { it.key }) { entry -> CardItem(entry) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
                items(entries, key = { it.key }) { entry ->
                    ListRow(entry.title, entry.subtitle, entry.onClick, entry.tag, entry.pinned) { entry.cover(Modifier.size(56.dp)) }
                }
            }
        }
    }

    if (creating) {
        CreatePlaylistSheet(viewModel, onDismiss = { creating = false }) { id ->
            creating = false
            nav.open(PlaylistRoute(id))
        }
    }
}

private class PlaylistEntry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val pinned: Boolean,
    val tag: String,
    val onClick: () -> Unit,
    val cover: @Composable (Modifier) -> Unit,
)

@Composable
private fun CardItem(entry: PlaylistEntry) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = entry.onClick).testTag(entry.tag)) {
        entry.cover(Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.title, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (entry.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = LocalAccent.current.accent, modifier = Modifier.size(14.dp))
        }
        if (entry.subtitle != null) Text(entry.subtitle, style = LumenType.numeric, color = LumenColors.paper2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    tag: String,
    pinned: Boolean = false,
    cover: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = Space.side, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = LumenType.label, color = LumenColors.paper2)
        }
        if (pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = LocalAccent.current.accent, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistSheet(viewModel: PlaylistsViewModel, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        name = viewModel.defaultName()
        runCatching { focusRequester.requestFocus() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LumenColors.ink2,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = Space.side, vertical = 8.dp)) {
            Text("New playlist", style = LumenType.display, color = LumenColors.paper)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                name, { name = it }, singleLine = true, label = { Text("Name") }, colors = lumenFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("playlist_name"),
            )
            Spacer(Modifier.height(16.dp))
            PillButton("Create", { viewModel.create(name, onCreated) }, Modifier.fillMaxWidth().testTag("playlist_create_confirm"), enabled = name.isNotBlank())
            Spacer(Modifier.height(16.dp))
        }
    }
}

