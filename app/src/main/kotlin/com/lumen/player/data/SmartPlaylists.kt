package com.lumen.player.data

import com.lumen.player.core.db.StatsDao
import com.lumen.player.core.db.TrackDao
import com.lumen.player.core.db.TrackRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class SmartKind(val title: String, val subtitle: String) {
    LIKED("Liked Songs", "Songs you liked"),
    ON_REPEAT("On Repeat", "Your most played songs of the last 30 days"),
    RECENT("Recently Added", "The last 50 songs you added"),
    FORGOTTEN("Forgotten Gems", "Songs you loved but haven't played in 60 days"),
}

private const val DAY = 86_400_000L

// the smart playlists are queries, not rows; all of them respect Artist Focus
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SmartPlaylists @Inject constructor(
    private val tracks: TrackRepository,
    private val trackDao: TrackDao,
    private val stats: StatsDao,
    private val focus: FocusRepository,
) {
    private fun focusedIds(): Flow<Set<String>?> = focus.focus.flatMapLatest { f ->
        if (f.off) kotlinx.coroutines.flow.flowOf(null) else tracks.songs.map { list -> list.map { it.id }.toSet() }
    }

    fun observe(kind: SmartKind): Flow<List<TrackRow>> = when (kind) {
        SmartKind.LIKED -> combine(trackDao.observeLiked(), focusedIds()) { list, ids -> list.filter { ids == null || it.id in ids } }
        SmartKind.RECENT -> tracks.songs.map { list -> list.sortedByDescending { it.addedAt }.take(50) }
        SmartKind.ON_REPEAT -> {
            val now = System.currentTimeMillis()
            combine(stats.topTracks(now - 30 * DAY, now + DAY, 30, true, emptyList()), tracks.songs) { top, songs ->
                val byId = songs.associateBy { it.id }
                top.mapNotNull { byId[it.trackId] }
            }
        }
        SmartKind.FORGOTTEN -> combine(stats.forgotten(3, System.currentTimeMillis() - 60 * DAY), tracks.songs) { list, songs ->
            val byId = songs.associateBy { it.id }
            list.mapNotNull { byId[it.trackId] }
        }
    }
}
