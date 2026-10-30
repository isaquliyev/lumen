package com.lumen.player.feature.playlist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.PlaylistEntity
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.PlaylistRepository
import com.lumen.player.data.SettingsRepository
import com.lumen.player.data.TrackRepository
import com.lumen.player.feature.shell.PlaylistRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PlaylistSort(val label: String) { CUSTOM("Custom order"), TITLE("Title"), ARTIST("Artist"), RECENT("Recently added") }

data class PlaylistState(
    val playlist: PlaylistEntity? = null,
    val all: List<TrackRow> = emptyList(),
    val shown: List<TrackRow> = emptyList(),
    val focusNames: String? = null,
    val coverTrackIds: List<String> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val paths: LumenPaths,
    private val events: AppEvents,
    tracks: TrackRepository,
    focusRepository: FocusRepository,
    artistDao: com.lumen.player.core.db.ArtistDao,
    private val settings: SettingsRepository,
) : ViewModel() {
    val id = handle.toRoute<PlaylistRoute>().id
    private val sortFlow = settings.playlistSort(id).map { s -> s?.let { runCatching { PlaylistSort.valueOf(it) }.getOrNull() } ?: PlaylistSort.CUSTOM }
    val sort: StateFlow<PlaylistSort> = sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistSort.CUSTOM)
    val showAll = MutableStateFlow(false)
    val editing = MutableStateFlow(false)

    private val focusIds = focusRepository.focus.flatMapLatest { f ->
        if (f.off) flowOf(null) else tracks.songsWithHidden.map { list -> list.map { it.id }.toSet() }
    }
    private val focusNames = focusRepository.focus.flatMapLatest { f ->
        if (f.off) flowOf(null) else artistDao.observeByIds(f.idList).map { list -> list.joinToString(" & ") { it.name } }
    }
    private val view = combine(sortFlow, showAll, editing) { s, all, edit -> Triple(s, all, edit) }

    val state: StateFlow<PlaylistState> = combine(
        repository.observe(id),
        combine(repository.observeTracks(id), repository.observeEntries(id)) { list, entries -> list to entries.associate { it.trackId to it.addedAt } },
        combine(focusIds, focusNames) { ids, names -> ids to names },
        view,
    ) { playlist, (list, addedAt), (ids, names), (sortBy, all, edit) ->
        val filtered = if (ids == null || all || edit) list else list.filter { it.id in ids }
        val sorted = if (edit) filtered else when (sortBy) {
            PlaylistSort.CUSTOM -> filtered
            PlaylistSort.TITLE -> filtered.sortedBy { it.sortTitle }
            PlaylistSort.ARTIST -> filtered.sortedWith(compareBy({ it.artistSort ?: "" }, { it.sortTitle }))
            PlaylistSort.RECENT -> filtered.sortedByDescending { addedAt[it.id] ?: 0 }
        }
        PlaylistState(
            playlist = playlist,
            all = list,
            shown = sorted,
            focusNames = if (ids != null && !all && !edit) names else null,
            coverTrackIds = list.filter { it.hasCover }.map { it.id }.distinct().take(4),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistState())

    fun setSort(value: PlaylistSort) = viewModelScope.launch { settings.setPlaylistSort(id, value.name) }

    fun rename(name: String, description: String) = viewModelScope.launch { repository.rename(id, name, description) }

    fun setPinned(pinned: Boolean) = viewModelScope.launch {
        repository.setPinned(id, pinned)
        events.show(if (pinned) "Pinned" else "Unpinned")
    }

    fun setHideSongs(hide: Boolean) = viewModelScope.launch {
        repository.setHideSongs(id, hide)
        events.show(if (hide) "Songs hidden from Library" else "Songs shown in Library")
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val name = state.value.playlist?.name
        repository.delete(id)
        events.show("Deleted “$name”")
        onDone()
    }

    fun setCover(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { saveSquareCover(context, uri, paths.playlistCover(id)) }.isSuccess }
        if (ok) repository.setCustomCover(id, true) else events.show("Couldn't use that image")
    }

    fun removeCover() = viewModelScope.launch {
        withContext(Dispatchers.IO) { paths.playlistCover(id).delete() }
        repository.setCustomCover(id, false)
    }

    fun remove(track: TrackRow) = viewModelScope.launch {
        val entry = repository.observeEntries(id).first().firstOrNull { it.trackId == track.id } ?: return@launch
        repository.remove(id, track.id)
        events.show("Removed “${track.title}”", "Undo") { viewModelScope.launch { repository.restoreEntry(entry) } }
    }

    fun reorder(orderedIds: List<String>) = viewModelScope.launch { repository.reorder(id, orderedIds) }
}
