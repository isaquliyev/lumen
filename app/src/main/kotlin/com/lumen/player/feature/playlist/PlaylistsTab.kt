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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
    smart: SmartPlaylists,
) : ViewModel() {
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
    val nav = LocalNav.current
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            ListRow(
                title = "Create playlist",
                subtitle = null,
                onClick = { creating = true },
                tag = "playlist_create",
                cover = {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(LumenColors.ink3), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = LumenColors.paper)
                    }
                },
            )
        }
        items(SmartKind.entries, key = { it.name }) { kind ->
            val count = counts[kind] ?: 0
            if (kind == SmartKind.LIKED || count > 0) {
                ListRow(
                    title = kind.title,
                    subtitle = if (kind == SmartKind.LIKED) "Playlist · ${formatCount(count, "song")}" else "Smart playlist · ${formatCount(count, "song")}",
                    onClick = { nav.open(SmartPlaylistRoute(kind.name)) },
                    tag = "smart_${kind.name}",
                    cover = { SmartCover(kind, Modifier.size(56.dp)) },
                )
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            ListRow(
                title = playlist.name,
                subtitle = "Playlist · ${formatCount(playlist.trackCount, "song")}",
                pinned = playlist.pinned,
                onClick = { nav.open(PlaylistRoute(playlist.id)) },
                tag = "playlist_${playlist.id}",
                cover = {
                    PlaylistCover(playlist.id, playlist.name, playlist.hasCustomCover, playlist.updatedAt, covers[playlist.id].orEmpty(), Modifier.size(56.dp))
                },
            )
        }
    }

    if (creating) {
        CreatePlaylistSheet(viewModel, onDismiss = { creating = false }) { id ->
            creating = false
            nav.open(PlaylistRoute(id))
        }
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

