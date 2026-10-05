package com.lumen.player.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.ArtistRow
import com.lumen.player.core.db.PlaylistRow
import com.lumen.player.core.db.StatsDao
import com.lumen.player.core.db.TrackRow
import com.lumen.player.data.Focus
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.PlaylistRepository
import com.lumen.player.data.SmartKind
import com.lumen.player.data.SmartPlaylists
import com.lumen.player.data.StatsCalculator
import com.lumen.player.data.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class PulseStrip(val todayMs: Long, val streak: Int, val topArtist: String?)

data class HomeState(
    val loaded: Boolean = false,
    val songCount: Int = 0,
    val strip: PulseStrip? = null,
    val onRepeat: List<TrackRow> = emptyList(),
    val forThisHour: List<TrackRow> = emptyList(),
    val playlists: List<PlaylistRow> = emptyList(),
    val covers: Map<String, List<String>> = emptyMap(),
    val artists: List<ArtistRow> = emptyList(),
    val recentlyAdded: List<TrackRow> = emptyList(),
    val rediscover: List<TrackRow> = emptyList(),
)

private const val DAY = 86_400_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    tracks: TrackRepository,
    smart: SmartPlaylists,
    playlists: PlaylistRepository,
    stats: StatsDao,
    artistDao: ArtistDao,
    private val focusRepository: FocusRepository,
) : ViewModel() {

    // every section follows Artist Focus
    private fun <T> scoped(block: (Focus) -> Flow<T>): Flow<T> = focusRepository.focus.flatMapLatest(block)

    private val strip: Flow<PulseStrip> = scoped { focus ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekFrom = from - 6 * DAY
        combine(
            stats.totalListened(from, from + DAY, focus.off, focus.idList),
            stats.days(0, Long.MAX_VALUE, focus.off, focus.idList),
            stats.topArtists(weekFrom, from + DAY, 1, focus.off, focus.idList),
            artistDao.observeAll(),
        ) { todayMs, days, top, artists ->
            val streak = StatsCalculator.streaks(days.filter { it.listenedMs > 0 }.map { LocalDate.parse(it.bucket) }.toSet(), today).current
            PulseStrip(todayMs, streak, top.firstOrNull()?.let { t -> artists.firstOrNull { it.id == t.artistId }?.name })
        }
    }

    private val forThisHour: Flow<List<TrackRow>> = scoped { focus ->
        val hour = LocalTime.now().hour
        combine(stats.aroundHours(listOf((hour + 23) % 24, hour, (hour + 1) % 24), 20, focus.off, focus.idList), tracks.songs) { top, songs ->
            val byId = songs.associateBy { it.id }
            top.mapNotNull { byId[it.trackId] }
        }
    }

    private val lovedArtists: Flow<List<ArtistRow>> = combine(artistDao.observeAll(), stats.lifetimeArtists(), focusRepository.focus) { all, listened, focus ->
        val ms = listened.associate { it.artistId to it.listenedMs }
        all.filter { (ms[it.id] ?: 0) > 0 && (focus.off || it.id in focus.ids) }.sortedByDescending { ms[it.id] ?: 0 }.take(12)
    }

    private val covers = playlists.coverTracks.map { list ->
        list.groupBy({ it.playlistId }, { it.trackId }).mapValues { (_, ids) -> ids.distinct().take(4) }
    }

    private val first = combine(tracks.songs, strip, smart.observe(SmartKind.ON_REPEAT), forThisHour) { songs, s, repeat, hour ->
        HomeState(
            loaded = true,
            songCount = songs.size,
            strip = s,
            onRepeat = repeat.take(12),
            forThisHour = hour,
            recentlyAdded = songs.sortedByDescending { it.addedAt }.take(12),
        )
    }

    val state: StateFlow<HomeState> = combine(first, playlists.all, covers, lovedArtists, smart.observe(SmartKind.FORGOTTEN)) { base, lists, c, artists, forgotten ->
        base.copy(playlists = lists.take(10), covers = c, artists = artists, rediscover = forgotten.take(12))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
