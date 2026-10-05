package com.lumen.player.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.DownloadJobDao
import com.lumen.player.core.db.DownloadJobEntity
import com.lumen.player.core.net.isOffline
import com.lumen.player.download.DownloadQueue
import com.lumen.player.download.DownloadRequest
import com.lumen.player.download.JobFailure
import com.lumen.player.download.LinkHandler
import com.lumen.player.download.sources.DeezerApi
import com.lumen.player.download.sources.DzAlbum
import com.lumen.player.download.sources.DzTrack
import com.lumen.player.download.sources.YtDlpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResults(
    val loading: Boolean = false,
    val tracks: List<DzTrack> = emptyList(),
    val albums: List<DzAlbum> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class AddViewModel @Inject constructor(
    private val deezer: DeezerApi,
    private val queue: DownloadQueue,
    private val links: LinkHandler,
    private val events: AppEvents,
    ytdlp: YtDlpManager,
    jobDao: DownloadJobDao,
) : ViewModel() {

    val query = MutableStateFlow("")
    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()
    val ready = ytdlp.ready
    val jobs: StateFlow<List<DownloadJobEntity>> = queue.jobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val inLibrary: StateFlow<Set<Long>> = jobDao.observeLibraryDeezerIds().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            query.debounce(350).map { it.trim() }.distinctUntilChanged().collectLatest { text ->
                if (text.length < 2) {
                    _results.value = SearchResults()
                    return@collectLatest
                }
                _results.value = _results.value.copy(loading = true, error = null)
                _results.value = runCatching {
                    SearchResults(tracks = deezer.search(text), albums = runCatching { deezer.searchAlbums(text) }.getOrDefault(emptyList()))
                }.getOrElse { e ->
                    SearchResults(error = if (e is JobFailure) e.message else if (e.isOffline()) "No internet connection." else "Search failed. Try again.")
                }
            }
        }
    }

    fun add(track: DzTrack) = viewModelScope.launch {
        queue.enqueue(DownloadRequest.DeezerPick(track.id), track.title, track.artist.name, track.album?.coverMedium)
    }

    fun addAlbum(album: DzAlbum) = viewModelScope.launch {
        val count = runCatching { links.enqueueAlbum(album.id) }.getOrElse { events.show("Couldn't load that album."); return@launch }
        events.show("Added $count songs to downloads")
    }

    fun paste(text: String?) = viewModelScope.launch {
        if (text.isNullOrBlank()) {
            events.show("The clipboard is empty")
            return@launch
        }
        events.show(runCatching { links.handle(text) }.getOrElse { "Not a song link" })
    }

    fun cancel(id: String) = viewModelScope.launch { queue.cancel(id) }
    fun retry(id: String) = viewModelScope.launch { queue.retry(id) }
    fun choose(id: String, videoId: String) = viewModelScope.launch { queue.choose(id, videoId) }
    fun clearFinished() = viewModelScope.launch { queue.clearFinished() }
}
