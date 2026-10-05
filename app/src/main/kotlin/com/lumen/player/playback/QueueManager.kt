package com.lumen.player.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.net.LumenJson
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.data.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// gives the service's ExoPlayer to in-process code; set by PlaybackService
@Singleton
class PlayerHolder @Inject constructor() {
    val player = MutableStateFlow<ExoPlayer?>(null)
    suspend fun await(): ExoPlayer = player.filterNotNull().first()
}

// the queue: our own shuffle (ExoPlayer always plays in order), play next, add to queue, persistence.
// every method must be called on the main thread, like the player itself
@Singleton
class QueueManager @Inject constructor(
    private val holder: PlayerHolder,
    private val contextHolder: PlaybackContextHolder,
    private val tracks: TrackRepository,
    private val paths: LumenPaths,
    private val store: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("saved_queue")
    private var originalOrder: List<String> = emptyList()
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    var connectRequest: (() -> Unit)? = null

    private suspend fun player(): ExoPlayer {
        if (holder.player.value == null) connectRequest?.invoke()
        return holder.await()
    }

    suspend fun play(rows: List<TrackRow>, startIndex: Int, context: PlayContext, contextId: String?, contextName: String, shuffle: Boolean = false) {
        if (rows.isEmpty()) return
        val player = player()
        val start = if (startIndex in rows.indices) startIndex else if (shuffle) rows.indices.random() else 0
        val ordered = if (shuffle) listOf(rows[start]) + (rows.take(start) + rows.drop(start + 1)).shuffled() else rows
        originalOrder = rows.map { it.id }
        _shuffle.value = shuffle
        contextHolder.set(context, contextId, contextName)
        player.setMediaItems(ordered.map { it.toMediaItem(paths) }, if (shuffle) 0 else start, 0L)
        player.prepare()
        player.play()
    }

    fun userQueuedCount(player: Player): Int {
        var count = 0
        var i = player.currentMediaItemIndex + 1
        while (i < player.mediaItemCount && isUserQueued(player.getMediaItemAt(i).mediaId)) {
            count++
            i++
        }
        return count
    }

    // "Play next" and "Add to queue" both go to the end of the user-queued block, first in first out
    suspend fun enqueue(rows: List<TrackRow>, playNow: Boolean = false) {
        if (rows.isEmpty()) return
        val player = player()
        if (player.mediaItemCount == 0) {
            play(rows, 0, PlayContext.QUEUE, null, "Your queue")
            return
        }
        val at = player.currentMediaItemIndex + 1 + userQueuedCount(player)
        player.addMediaItems(at, rows.map { it.toMediaItem(paths, userQueued = true) })
        if (playNow) player.seekTo(at, 0)
    }

    suspend fun toggleShuffle() = setShuffle(!_shuffle.value)

    suspend fun setShuffle(on: Boolean) {
        val player = player()
        _shuffle.value = on
        if (player.mediaItemCount == 0) return
        val current = player.currentMediaItemIndex
        val tailStart = current + 1 + userQueuedCount(player)
        if (on) {
            if (tailStart < player.mediaItemCount) {
                val tail = (tailStart until player.mediaItemCount).map { player.getMediaItemAt(it) }.shuffled()
                player.replaceMediaItems(tailStart, player.mediaItemCount, tail)
            }
        } else {
            val currentId = trackIdOf(player.currentMediaItem?.mediaId ?: return)
            val position = originalOrder.indexOf(currentId)
            if (position < 0) return
            val after = tracks.rows(originalOrder.drop(position + 1)).map { it.toMediaItem(paths) }
            val before = tracks.rows(originalOrder.take(position)).map { it.toMediaItem(paths) }
            player.replaceMediaItems(tailStart, player.mediaItemCount, after)
            player.replaceMediaItems(0, current, before)
        }
    }

    fun cycleRepeat(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    suspend fun clearUserQueue() {
        val player = player()
        val start = player.currentMediaItemIndex + 1
        val count = userQueuedCount(player)
        if (count > 0) player.removeMediaItems(start, start + count)
    }

    // removes the given tracks everywhere in the queue (used after deleting songs)
    suspend fun removeTracks(ids: Set<String>) {
        val player = holder.player.value ?: return
        for (i in player.mediaItemCount - 1 downTo 0) {
            if (trackIdOf(player.getMediaItemAt(i).mediaId) in ids) player.removeMediaItem(i)
        }
        originalOrder = originalOrder.filter { it !in ids }
    }

    suspend fun save(player: Player) {
        if (player.mediaItemCount == 0) return
        val saved = SavedQueue(
            mediaIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            originalOrder = originalOrder,
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = _shuffle.value,
            repeatMode = player.repeatMode,
            contextType = contextHolder.current.type.name,
            contextId = contextHolder.current.id,
            contextName = contextHolder.current.name,
        )
        store.edit { it[key] = LumenJson.encodeToString(SavedQueue.serializer(), saved) }
    }

    suspend fun loadSaved(): SavedQueue? =
        store.data.first()[key]?.let { runCatching { LumenJson.decodeFromString(SavedQueue.serializer(), it) }.getOrNull() }

    // builds the saved queue's media items (deleted songs are dropped) and restores the queue state
    suspend fun restoreItems(): Pair<List<MediaItem>, SavedQueue>? {
        val saved = loadSaved() ?: return null
        val rows = tracks.rows(saved.mediaIds.map(::trackIdOf).distinct()).filter { it.deletedAt == null }.associateBy { it.id }
        val items = saved.mediaIds.mapNotNull { mediaId -> rows[trackIdOf(mediaId)]?.toMediaItem(paths, isUserQueued(mediaId)) }
        if (items.isEmpty()) return null
        originalOrder = saved.originalOrder.filter { it in rows }
        _shuffle.value = saved.shuffle
        contextHolder.set(runCatching { PlayContext.valueOf(saved.contextType) }.getOrDefault(PlayContext.QUEUE), saved.contextId, saved.contextName)
        return items to saved.copy(index = saved.index.coerceIn(0, items.lastIndex))
    }
}
