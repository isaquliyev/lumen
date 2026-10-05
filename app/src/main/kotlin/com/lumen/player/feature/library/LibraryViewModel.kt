package com.lumen.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.db.AlbumDao
import com.lumen.player.core.db.AlbumRow
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.ArtistRow
import com.lumen.player.core.db.StatsDao
import com.lumen.player.core.db.TrackRow
import com.lumen.player.data.Focus
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SongSort(val label: String) {
    RECENT("Recently added"), TITLE("Title"), ARTIST("Artist"), MOST_PLAYED("Most played"), DURATION("Duration")
}

enum class ArtistSort(val label: String) { NAME("Name"), LISTENING("Listening time") }

data class SongsState(val songs: List<TrackRow> = emptyList(), val totalMs: Long = 0, val loaded: Boolean = false)

fun sortSongs(list: List<TrackRow>, sort: SongSort, plays: Map<String, Int>): List<TrackRow> = when (sort) {
    SongSort.RECENT -> list.sortedByDescending { it.addedAt }
    SongSort.TITLE -> list.sortedBy { it.sortTitle }
    SongSort.ARTIST -> list.sortedWith(compareBy<TrackRow>({ it.artistSort ?: "" }, { it.albumTitle ?: "" }, { it.trackPosition ?: 0 }, { it.sortTitle }))
    SongSort.MOST_PLAYED -> list.sortedWith(compareByDescending<TrackRow> { plays[it.id] ?: 0 }.thenBy { it.sortTitle })
    SongSort.DURATION -> list.sortedByDescending { it.durationMs }
}

// first letter used by the fast-scroll rail ("#" for digits and symbols)
fun railKey(text: String?): Char = text?.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    tracks: TrackRepository,
    artistDao: ArtistDao,
    albumDao: AlbumDao,
    stats: StatsDao,
    focusRepository: FocusRepository,
) : ViewModel() {
    val songSort = MutableStateFlow(SongSort.RECENT)
    val artistSort = MutableStateFlow(ArtistSort.NAME)
    val selection = MutableStateFlow<Set<String>>(emptySet())
    val focus: StateFlow<Focus> = focusRepository.focus

    private val plays = stats.lifetimePlays().map { list -> list.associate { it.trackId to it.plays } }

    val songs: StateFlow<SongsState> = combine(tracks.songs, songSort, plays) { list, sort, playMap ->
        SongsState(sortSongs(list, sort, playMap), list.sumOf { it.durationMs }, true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsState())

    val artists: StateFlow<List<ArtistRow>> = combine(
        artistDao.observeAll(),
        stats.lifetimeArtists().map { list -> list.associate { it.artistId to it.listenedMs } },
        artistSort,
        focusRepository.focus,
    ) { list, listened, sort, focus ->
        val sorted = if (sort == ArtistSort.NAME) list.sortedBy { it.sortName } else list.sortedByDescending { listened[it.id] ?: 0 }
        sorted.sortedByDescending { it.id in focus.ids }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumRow>> = focusRepository.focus
        .flatMapLatest { albumDao.observeAll(it.off, it.idList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleSelected(id: String) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }
}
