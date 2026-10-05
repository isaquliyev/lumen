package com.lumen.player.data

import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.util.bestCandidate
import com.lumen.player.core.util.rankedCandidates
import com.lumen.player.download.CandidateUi
import com.lumen.player.download.DownloadQueue
import com.lumen.player.download.DownloadRequest
import com.lumen.player.download.sources.LrcLibApi
import com.lumen.player.download.sources.YtDlpAudio
import com.lumen.player.download.toUi
import com.lumen.player.playback.QueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// actions from the track menu that need more than one repository
@Singleton
class TrackActions @Inject constructor(
    private val db: LumenDatabase,
    private val tracks: TrackRepository,
    private val paths: LumenPaths,
    private val lrclib: LrcLibApi,
    private val youtube: YtDlpAudio,
    private val downloads: DownloadQueue,
    private val queue: QueueManager,
) {
    suspend fun delete(ids: Collection<String>) {
        withContext(Dispatchers.Main) { queue.removeTracks(ids.toSet()) }
        tracks.delete(ids)
    }

    suspend fun refetchLyrics(track: TrackRow): LyricsKind = withContext(Dispatchers.IO) {
        val primary = db.trackArtistDao().artistsOf(track.id).firstOrNull()?.name ?: track.artistLine.orEmpty()
        val result = lrclib.fetch(track.title, primary, track.albumTitle, (track.durationMs / 1000).toInt())
        paths.syncedLyrics(track.id).delete()
        paths.plainLyrics(track.id).delete()
        result.text?.let { text ->
            val file = if (result.kind == LyricsKind.SYNCED) paths.syncedLyrics(track.id) else paths.plainLyrics(track.id)
            file.parentFile?.mkdirs()
            file.writeText(text)
        }
        tracks.setLyricsKind(track.id, result.kind)
        result.kind
    }

    suspend fun audioCandidates(track: TrackRow): List<CandidateUi> {
        val artist = db.trackArtistDao().artistsOf(track.id).firstOrNull()?.name ?: track.artistLine.orEmpty()
        val seconds = (track.durationMs / 1000).toInt()
        val first = youtube.search("$artist - ${track.title}")
        val merged = if (bestCandidate(first, track.title, artist, seconds) == null) {
            (first + youtube.search("$artist ${track.title} audio")).distinctBy { it.videoId }
        } else first
        return rankedCandidates(merged, track.title, artist, seconds).take(6).map { it.toUi() }
    }

    suspend fun replaceAudio(track: TrackRow, videoId: String) {
        downloads.enqueue(DownloadRequest.ReplaceAudio(track.id, videoId), track.title, track.artistLine.orEmpty(), null)
    }
}
