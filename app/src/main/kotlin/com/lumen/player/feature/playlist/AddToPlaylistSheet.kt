package com.lumen.player.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.PlaylistRow
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import com.lumen.player.core.util.formatCount
import com.lumen.player.data.PlaylistRepository
import com.lumen.player.feature.library.LocalAddToPlaylist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val events: AppEvents,
) : ViewModel() {
    val trackIds = MutableStateFlow<List<String>?>(null)
    val playlists: StateFlow<List<PlaylistRow>> = repository.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val covers: StateFlow<Map<String, List<String>>> = repository.coverTracks
        .map { list -> list.groupBy({ it.playlistId }, { it.trackId }).mapValues { (_, ids) -> ids.distinct().take(4) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // checkmarks only make sense for a single song
    val containing: StateFlow<Set<String>> = trackIds.flatMapLatest { ids ->
        if (ids?.size == 1) repository.observeContaining(ids.first()).map { it.toSet() } else flowOf(emptySet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun addTo(playlist: PlaylistRow) = viewModelScope.launch {
        val ids = trackIds.value ?: return@launch
        trackIds.value = null
        val added = repository.add(playlist.id, ids)
        events.show(
            when {
                added == 0 && ids.size == 1 -> "Already in ${playlist.name}"
                added == 0 -> "All already in ${playlist.name}"
                else -> "Added to ${playlist.name}"
            }
        )
    }

    fun createWith(onDone: () -> Unit) = viewModelScope.launch {
        val ids = trackIds.value ?: return@launch
        trackIds.value = null
        val name = repository.nextDefaultName()
        val id = repository.create(name)
        repository.add(id, ids)
        events.show("Added to $name")
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistHost(viewModel: AddToPlaylistViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val ids by viewModel.trackIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val containing by viewModel.containing.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val accent = LocalAccent.current.accent

    CompositionLocalProvider(LocalAddToPlaylist provides { list -> viewModel.trackIds.value = list }) { content() }

    if (ids != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.trackIds.value = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LumenColors.ink2,
            shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    if (ids!!.size == 1) "Add to playlist" else "Add ${ids!!.size} songs to playlist",
                    style = LumenType.title, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side, vertical = 8.dp),
                )
                SheetRow("New playlist", null, "atp_new", icon = {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(LumenColors.ink3), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = LumenColors.paper)
                    }
                }) { viewModel.createWith { } }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val inside = playlist.id in containing
                        SheetRow(playlist.name, formatCount(playlist.trackCount, "song"), "atp_${playlist.id}", icon = {
                            PlaylistCover(playlist.id, playlist.name, playlist.hasCustomCover, playlist.updatedAt, covers[playlist.id].orEmpty(), Modifier.size(44.dp))
                        }, trailing = { if (inside) Icon(Icons.Rounded.CheckCircle, "Already added", tint = accent) }) {
                            viewModel.addTo(playlist)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String?,
    tag: String,
    icon: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = Space.side, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = LumenType.label, color = LumenColors.paper2)
        }
        trailing()
    }
}
