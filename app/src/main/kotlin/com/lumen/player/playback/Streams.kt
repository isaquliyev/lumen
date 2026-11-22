package com.lumen.player.playback

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import java.util.concurrent.ConcurrentHashMap

// streamed songs play from the internet and are never saved in the database: no stats, no playlists,
// not part of the saved queue. Their media id is "stream:<deezer id>"
const val STREAM_PREFIX = "stream:"

fun isStream(mediaId: String) = mediaId.startsWith(STREAM_PREFIX)

fun streamIdOf(deezerId: Long) = STREAM_PREFIX + deezerId

data class StreamInfo(
    val deezerId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val coverUrl: String?,
    val durationMs: Long,
    val videoId: String,
)

// the streams the app knows about while it runs (memory only), so the UI can show their title and cover
object Streams {
    private val known = ConcurrentHashMap<String, StreamInfo>()

    fun put(info: StreamInfo) {
        known[streamIdOf(info.deezerId)] = info
    }

    fun get(mediaId: String): StreamInfo? = known[trackIdOf(mediaId)]

    fun cover(trackId: String): String? = if (isStream(trackId)) get(trackId)?.coverUrl else null

    fun videoFor(deezerId: Long): String? = known[streamIdOf(deezerId)]?.videoId

    // a list row for the player screens; fields a stream does not have stay empty
    fun row(mediaId: String): TrackRow? = get(mediaId)?.let { s ->
        TrackRow(
            id = streamIdOf(s.deezerId), title = s.title, sortTitle = s.title.lowercase(), albumId = null, albumTitle = s.album,
            artistLine = s.artist, artistSort = null, primaryArtistId = null, durationMs = s.durationMs, hasCover = s.coverUrl != null,
            addedAt = 0, likedAt = null, toneHue = null, toneSat = null, lyricsKind = LyricsKind.NONE, lyricsOffsetMs = 0,
            trackPosition = null, releaseDate = null, genre = null, fileSize = 0, audioFile = "", gainDb = null, deletedAt = null,
        )
    }
}

fun StreamInfo.toMediaItem(url: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(streamIdOf(deezerId))
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(coverUrl?.let(Uri::parse))
                .setDurationMs(durationMs)
                .setExtras(bundleOf(EXTRA_GAIN to 0f))
                .build()
        )
        .build()

// the player's web requests; YouTube audio links need the headers yt-dlp used to get them
@UnstableApi
object StreamHttp {
    val factory: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    fun use(headers: Map<String, String>) {
        factory.setDefaultRequestProperties(headers)
    }
}
