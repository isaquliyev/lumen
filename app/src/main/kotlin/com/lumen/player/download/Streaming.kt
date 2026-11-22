package com.lumen.player.download

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.lumen.player.core.ApplicationScope
import com.lumen.player.core.db.DownloadJobDao
import com.lumen.player.core.db.JobStatus
import com.lumen.player.core.util.bestCandidate
import com.lumen.player.core.util.rankedCandidates
import com.lumen.player.core.util.AudioCandidate
import com.lumen.player.download.sources.DzTrack
import com.lumen.player.download.sources.NewPipeSource
import com.lumen.player.download.sources.YtDlpAudio
import com.lumen.player.playback.StreamHttp
import com.lumen.player.playback.StreamInfo
import com.lumen.player.playback.Streams
import com.lumen.player.playback.streamIdOf
import com.lumen.player.playback.toMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// finds a song's audio on YouTube and turns it into something the player can stream
@OptIn(UnstableApi::class)
@Singleton
class StreamResolver @Inject constructor(private val youtube: YtDlpAudio, private val newPipe: NewPipeSource) {

    // the same search a download does, but there is no "choose audio" step: the best guess plays
    private suspend fun findVideo(title: String, artist: String, durationSec: Int?): String {
        val first = search("$artist - $title")
        bestCandidate(first, title, artist, durationSec)?.let { return it.videoId }
        val merged = (first + search("$artist $title audio")).distinctBy { it.videoId }
        val pick = bestCandidate(merged, title, artist, durationSec) ?: rankedCandidates(merged, title, artist, durationSec).firstOrNull()
        return pick?.videoId ?: throw JobFailure("No audio found for this song.")
    }

    // a song streamed before reuses its video, so only a fresh link is needed
    suspend fun prepare(track: DzTrack, cover: String?): MediaItem {
        val started = System.currentTimeMillis()
        val known = Streams.get(streamIdOf(track.id))
        val info = known ?: StreamInfo(
            deezerId = track.id,
            title = track.title,
            artist = track.artist.name,
            album = track.album?.title,
            coverUrl = cover,
            durationMs = track.duration * 1000L,
            videoId = findVideo(track.titleShort?.ifBlank { null } ?: track.title, track.artist.name, track.duration.takeIf { it > 0 }),
        )
        Streams.put(info)
        val found = System.currentTimeMillis()
        return mediaItem(info).also {
            Log.i("Lumen", "stream ready: find ${found - started} ms, link ${System.currentTimeMillis() - found} ms")
        }
    }

    // audio links stop working after a few hours, so every play gets a new one; after a playback
    // error the fresh link comes from yt-dlp, in case YouTube refused NewPipe's link
    suspend fun mediaItem(info: StreamInfo, ytDlpOnly: Boolean = false): MediaItem {
        val link = if (ytDlpOnly) youtube.streamUrl(info.videoId) else fast({ newPipe.streamUrl(info.videoId) }) { youtube.streamUrl(info.videoId) }
        StreamHttp.use(link.headers)
        return info.toMediaItem(link.url)
    }

    private suspend fun search(query: String): List<AudioCandidate> = fast({ newPipe.search(query).ifEmpty { error("no results") } }) { youtube.search(query) }

    // NewPipe first (fast), yt-dlp when it fails (YouTube changes often break one of them)
    private suspend fun <T> fast(primary: suspend () -> T, fallback: suspend () -> T): T = try {
        primary()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Lumen", "NewPipe failed, using yt-dlp: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
        fallback()
    }
}

// downloads a streamed song (with the audio that was streamed) and runs actions, such as liking it
// or adding it to a playlist, once it is in the library; pending actions live in memory only
@Singleton
class StreamSaver @Inject constructor(
    private val downloads: DownloadQueue,
    private val jobDao: DownloadJobDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val pending = ConcurrentHashMap<Long, MutableList<suspend (String) -> Unit>>()
    @Volatile private var watching = false

    suspend fun libraryTrack(deezerId: Long): String? = jobDao.observeLibraryDeezerLinks().first().firstOrNull { it.deezerId == deezerId }?.trackId

    suspend fun save(info: StreamInfo) {
        if (libraryTrack(info.deezerId) != null) return
        val busy = downloads.jobs.first().any { job ->
            (job.status == JobStatus.QUEUED || job.status == JobStatus.RUNNING) &&
                (runCatching { decodeRequest(job.request) }.getOrNull() as? DownloadRequest.DeezerPick)?.deezerId == info.deezerId
        }
        if (!busy) downloads.enqueue(DownloadRequest.DeezerPick(info.deezerId, info.videoId), info.title, info.artist, info.coverUrl)
    }

    suspend fun whenSaved(info: StreamInfo, action: suspend (String) -> Unit) {
        libraryTrack(info.deezerId)?.let { action(it); return }
        pending.getOrPut(info.deezerId) { mutableListOf() }.add(action)
        watch()
        save(info)
    }

    private fun watch() {
        if (watching) return
        watching = true
        scope.launch {
            jobDao.observeLibraryDeezerLinks().collect { links ->
                links.forEach { link -> pending.remove(link.deezerId)?.forEach { it(link.trackId) } }
            }
        }
    }
}
