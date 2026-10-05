package com.lumen.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.data.TrackRepository
import com.lumen.player.playback.PlaybackContextHolder
import com.lumen.player.playback.PlayerConnection
import com.lumen.player.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val connection: PlayerConnection,
    private val queue: QueueManager,
    private val tracks: TrackRepository,
    contextHolder: PlaybackContextHolder,
) : ViewModel() {
    val nowPlaying = connection.nowPlaying
    val isPlaying = connection.isPlaying
    val durationMs = connection.durationMs
    val repeatMode = connection.repeatMode
    val shuffle = connection.shuffle
    val queueUi = connection.queueUi
    val context = contextHolder.flow

    fun position(intervalMs: Long = 250) = connection.position(intervalMs)

    fun play(rows: List<TrackRow>, index: Int, context: PlayContext, contextId: String?, name: String, shuffle: Boolean = false) =
        viewModelScope.launch { queue.play(rows, index, context, contextId, name, shuffle) }

    fun playIds(ids: List<String>, context: PlayContext = PlayContext.QUEUE, name: String = "Your queue") = viewModelScope.launch {
        queue.play(tracks.rows(ids), 0, context, null, name)
    }

    fun playNext(rows: List<TrackRow>) = viewModelScope.launch { queue.enqueue(rows) }
    fun addToQueue(rows: List<TrackRow>) = viewModelScope.launch { queue.enqueue(rows) }
    fun clearUserQueue() = viewModelScope.launch { queue.clearUserQueue() }
    fun setLiked(id: String, liked: Boolean) = viewModelScope.launch { tracks.setLiked(id, liked) }
}
