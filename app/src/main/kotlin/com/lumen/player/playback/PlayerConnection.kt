package com.lumen.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.lumen.player.core.db.TrackRow
import com.lumen.player.data.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class QueueEntry(val index: Int, val mediaId: String, val row: TrackRow?, val userQueued: Boolean)

data class QueueUi(val currentIndex: Int = -1, val entries: List<QueueEntry> = emptyList()) {
    val current get() = entries.getOrNull(currentIndex)
    val upNextUser get() = entries.drop(currentIndex + 1).takeWhile { it.userQueued }
    val upNextContext get() = entries.drop(currentIndex + 1).dropWhile { it.userQueued }
}

// wraps the MediaController and exposes the player state as flows for the UI
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: QueueManager,
    private val tracks: TrackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _mediaId = MutableStateFlow<String?>(null)
    val mediaId: StateFlow<String?> = _mediaId.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _duration.asStateFlow()
    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeat.asStateFlow()
    private val _ids = MutableStateFlow<List<String>>(emptyList())
    private val _index = MutableStateFlow(-1)
    val shuffle = queue.shuffle

    val nowPlaying: StateFlow<TrackRow?> = _mediaId
        .flatMapLatest { id -> if (id == null) flowOf(null) else tracks.observe(trackIdOf(id)) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val queueUi: StateFlow<QueueUi> = combine(_ids, _index) { ids, index -> ids to index }
        .mapLatest { (ids, index) ->
            val rows = tracks.rows(ids.map(::trackIdOf).distinct()).associateBy { it.id }
            QueueUi(index, ids.mapIndexed { i, id -> QueueEntry(i, id, rows[trackIdOf(id)], isUserQueued(id)) })
        }
        .stateIn(scope, SharingStarted.Eagerly, QueueUi())

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        queue.connectRequest = { scope.launch { connect() } }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener({
            runCatching { pending.get() }.onSuccess { controller ->
                controller.addListener(listener)
                _controller.value = controller
                sync(controller)
            }.onFailure { future = null }
        }, ContextCompat.getMainExecutor(context))
    }

    // lets go of the service when the app's screen is closed; a playing service stays on its own
    fun release() {
        _controller.value?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
    }

    private fun sync(player: Player) {
        _isPlaying.value = player.isPlaying
        _mediaId.value = player.currentMediaItem?.mediaId
        _duration.value = player.duration.takeIf { it > 0 } ?: player.currentMediaItem?.mediaMetadata?.durationMs ?: 0L
        _repeat.value = player.repeatMode
        _index.value = player.currentMediaItemIndex
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (ids != _ids.value) _ids.value = ids
    }

    // position is polled only while someone collects it (a visible player UI)
    fun position(intervalMs: Long = 250): Flow<Long> = flow {
        while (true) {
            emit(_controller.value?.currentPosition ?: 0L)
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.Main)

    fun playPause() {
        val c = _controller.value ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0)
            c.play()
        }
    }

    // at the end of the queue, Next goes back to the first song
    fun next() {
        val c = _controller.value ?: return
        if (c.hasNextMediaItem()) c.seekToNext()
        else if (c.mediaItemCount > 0) {
            c.seekTo(0, 0)
            c.play()
        }
    }
    fun previous() = _controller.value?.seekToPrevious()
    fun seekTo(ms: Long) = _controller.value?.seekTo(ms)
    fun seekToIndex(index: Int) = _controller.value?.let { it.seekTo(index, 0); it.play() }
    fun cycleRepeat() = _controller.value?.let { queue.cycleRepeat(it) }
    fun toggleShuffle() = scope.launch { queue.toggleShuffle() }
    fun moveItem(from: Int, to: Int) = _controller.value?.moveMediaItem(from, to)
    fun removeItem(index: Int) = _controller.value?.removeMediaItem(index)
}
