package com.lumen.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackArtistDao
import com.lumen.player.core.db.TrackArtistName
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.Accent
import com.lumen.player.core.design.accentFor
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.seededTone
import com.lumen.player.data.LyricsRepository
import com.lumen.player.data.SettingsRepository
import com.lumen.player.data.TrackLyrics
import com.lumen.player.data.TrackRepository
import com.lumen.player.playback.PlaybackContextHolder
import com.lumen.player.playback.PlaybackExtras
import com.lumen.player.playback.PlayerConnection
import com.lumen.player.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val connection: PlayerConnection,
    private val queue: QueueManager,
    private val tracks: TrackRepository,
    private val extras: PlaybackExtras,
    trackArtistDao: TrackArtistDao,
    lyricsRepository: LyricsRepository,
    contextHolder: PlaybackContextHolder,
    private val settings: SettingsRepository,
) : ViewModel() {
    val nowPlaying = connection.nowPlaying
    val isPlaying = connection.isPlaying
    val durationMs = connection.durationMs
    val repeatMode = connection.repeatMode
    val shuffle = connection.shuffle
    val queueUi = connection.queueUi
    val context = contextHolder.flow
    val sleep = extras.sleep
    val shuffleMode: StateFlow<Boolean> = settings.settings.map { it.songsShuffle }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val currentId = nowPlaying.map { it?.id }.distinctUntilChanged()

    val lyrics: StateFlow<TrackLyrics?> = nowPlaying.map { it?.let { row -> row.id to row.lyricsKind } }.distinctUntilChanged()
        .mapLatest { pair -> pair?.let { nowPlaying.value?.let { row -> lyricsRepository.load(row) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val artists: StateFlow<List<TrackArtistName>> = currentId.mapLatest { id -> id?.let { trackArtistDao.artistsOf(it) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // the player's accent comes from the playing song's cover
    val accent: StateFlow<Accent?> = nowPlaying.map { row ->
        row?.let { accentFor(it.toneHue?.let { hue -> CoverTone(hue, it.toneSat ?: 0.5f) } ?: seededTone(it.title)) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun position(intervalMs: Long = 250) = connection.position(intervalMs)

    fun setShuffleMode(on: Boolean, type: PlayContext, id: String?) = viewModelScope.launch {
        settings.setSongsShuffle(on)
        if (context.value.type == type && context.value.id == id && shuffle.value != on) queue.setShuffle(on)
    }

    fun play(rows: List<TrackRow>, index: Int, context: PlayContext, contextId: String?, name: String, shuffle: Boolean = false) =
        viewModelScope.launch { queue.play(rows, index, context, contextId, name, shuffle) }

    fun playIds(ids: List<String>, context: PlayContext = PlayContext.QUEUE, name: String = "Your queue") = viewModelScope.launch {
        queue.play(tracks.rows(ids), 0, context, null, name)
    }

    fun playNext(rows: List<TrackRow>) = viewModelScope.launch { queue.enqueue(rows) }
    fun addToQueue(rows: List<TrackRow>) = viewModelScope.launch { queue.enqueue(rows) }
    fun clearUserQueue() = viewModelScope.launch { queue.clearUserQueue() }
    fun setLiked(id: String, liked: Boolean) = viewModelScope.launch { tracks.setLiked(id, liked) }

    fun sleepIn(minutes: Int) = extras.sleepIn(minutes)
    fun sleepAtEndOfSong() = extras.sleepAtEndOfSong()
    fun cancelSleep() = extras.cancelSleep()
}
