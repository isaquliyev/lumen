package com.lumen.player.playback

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.storage.LumenPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

// user-queued items ("Play next" / "Add to queue") carry this suffix in their media id
private const val QUEUED_SUFFIX = "#q"

fun trackIdOf(mediaId: String) = mediaId.substringBefore('#')

fun isUserQueued(mediaId: String) = mediaId.endsWith(QUEUED_SUFFIX)

fun TrackRow.toMediaItem(paths: LumenPaths, userQueued: Boolean = false): MediaItem =
    MediaItem.Builder()
        .setMediaId(if (userQueued) id + QUEUED_SUFFIX else id)
        .setUri(Uri.fromFile(paths.resolve(audioFile)))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistLine)
                .setAlbumTitle(albumTitle)
                .setArtworkUri(if (hasCover) Uri.fromFile(paths.cover(id)) else null)
                .setDurationMs(durationMs)
                .setExtras(bundleOf(EXTRA_GAIN to (gainDb ?: 0f)))
                .build()
        )
        .build()

const val EXTRA_GAIN = "gain_db"

data class PlaybackContextSnapshot(val type: PlayContext, val id: String?, val name: String)

// what the current queue was started from; read by the listening tracker and the player's "PLAYING FROM"
@Singleton
class PlaybackContextHolder @Inject constructor() {
    private val _current = MutableStateFlow(PlaybackContextSnapshot(PlayContext.QUEUE, null, "Your queue"))
    val flow: StateFlow<PlaybackContextSnapshot> = _current.asStateFlow()
    val current get() = _current.value

    fun set(type: PlayContext, id: String?, name: String) {
        _current.value = PlaybackContextSnapshot(type, id, name)
    }
}

@Serializable
data class SavedQueue(
    val mediaIds: List<String>,
    val originalOrder: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val contextType: String,
    val contextId: String?,
    val contextName: String,
)
