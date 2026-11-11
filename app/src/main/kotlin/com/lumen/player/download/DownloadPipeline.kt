package com.lumen.player.download

import android.util.Log
import androidx.room.withTransaction
import com.lumen.player.core.db.AlbumEntity
import com.lumen.player.core.db.ArtistEntity
import com.lumen.player.core.db.ArtistRole
import com.lumen.player.core.db.DownloadJobEntity
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackArtistEntity
import com.lumen.player.core.db.TrackEntity
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.storage.moveInto
import com.lumen.player.core.storage.processSquareImage
import com.lumen.player.core.util.AudioCandidate
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.albumIdFor
import com.lumen.player.core.util.artistIdFor
import com.lumen.player.core.util.bestCandidate
import com.lumen.player.core.util.newId
import com.lumen.player.core.util.normalize
import com.lumen.player.core.util.rankedCandidates
import com.lumen.player.core.util.sortKey
import com.lumen.player.core.util.splitCredits
import com.lumen.player.download.sources.DeezerApi
import com.lumen.player.download.sources.DzAlbum
import com.lumen.player.download.sources.DzTrack
import com.lumen.player.download.sources.ItunesApi
import com.lumen.player.download.sources.LrcLibApi
import com.lumen.player.download.sources.SpotifyResolver
import com.lumen.player.download.sources.YtDlpAudio
import com.lumen.player.download.sources.YtDlpError
import com.lumen.player.download.sources.YtDlpManager
import com.lumen.player.download.sources.spotifyTrackId
import com.lumen.player.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

class JobCancelled : Exception("Cancelled")

sealed interface Outcome {
    data class Done(val trackId: String, val message: String? = null) : Outcome
    data class NeedsChoice(val candidates: List<CandidateUi>) : Outcome
}

data class Credit(val id: String, val name: String, val deezerId: Long?, val pictureUrl: String?, val role: ArtistRole)

data class Resolved(
    val title: String,
    val durationSec: Int?,
    val credits: List<Credit>,
    val albumId: String?,
    val albumTitle: String?,
    val album: DzAlbum?,
    val coverUrl: String?,
    val deezerId: Long?,
    val isrc: String?,
    val bpm: Float?,
    val explicit: Boolean,
    val releaseDate: String?,
    val trackPosition: Int?,
    val spotifyId: String?,
    val videoId: String?,
)

private val FEAT_PART = Regex("""\s*[(\[](?:feat|ft|featuring|with)\.?\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

@Singleton
class DownloadPipeline @Inject constructor(
    private val db: LumenDatabase,
    private val paths: LumenPaths,
    private val http: Http,
    private val deezer: DeezerApi,
    private val itunes: ItunesApi,
    private val lrclib: LrcLibApi,
    private val spotify: SpotifyResolver,
    private val youtube: YtDlpAudio,
    private val ytdlp: YtDlpManager,
) {
    @Volatile private var updatedThisRun = false

    suspend fun run(job: DownloadJobEntity, report: suspend (String, Int) -> Unit, cancelled: () -> Boolean): Outcome {
        fun check() { if (cancelled()) throw JobCancelled() }
        val work = paths.workDir(job.id).apply { deleteRecursively(); mkdirs() }
        val request = decodeRequest(job.request)
        if (request is DownloadRequest.ReplaceAudio) return replaceAudio(job, request, work, report, ::check)

        report(Stage.RESOLVING, 5)
        val resolved = resolve(request)
        db.downloadJobDao().setInfo(job.id, resolved.title, resolved.credits.joinToString(", ") { it.name }, resolved.coverUrl)
        check()

        report(Stage.METADATA, 12)
        val existing = db.trackDao().findSame(resolved.deezerId, resolved.isrc)
        if (existing != null && existing.deletedAt == null) return Outcome.Done(existing.id, "Already in your library")
        val trackId = existing?.id ?: newId()
        val primary = resolved.credits.first()
        val durationMs = (resolved.durationSec ?: 0) * 1000L
        val genre = itunes.genre(primary.name, resolved.title, durationMs)
            ?: resolved.album?.genres?.data?.firstOrNull()?.name
        check()

        report(Stage.COVER, 20)
        val coverTone = optional("cover") { downloadImage(resolved.coverUrl, work, "cover") }
        check()

        report(Stage.ARTISTS, 26)
        val artistTones = mutableMapOf<String, CoverTone?>()
        for (credit in resolved.credits) {
            val known = db.artistDao().get(credit.id)
            if (known?.hasImage == true || credit.pictureUrl.isNullOrBlank() || credit.pictureUrl.contains("/artist//")) continue
            artistTones[credit.id] = optional("artist ${credit.name}") { downloadImage(credit.pictureUrl, work, "artist_${credit.id}") }
        }
        check()

        report(Stage.LYRICS, 32)
        val lyrics = optional("lyrics") {
            lrclib.fetch(resolved.title, primary.name, resolved.albumTitle, resolved.durationSec ?: 0)
        }
        lyrics?.text?.let { text -> File(work, if (lyrics.kind == LyricsKind.SYNCED) "lyrics.lrc" else "lyrics.txt").writeText(text) }
        check()

        report(Stage.AUDIO, 35)
        val videoId = resolved.videoId ?: when (val found = findAudio(resolved.title, primary.name, resolved.durationSec)) {
            is Found.Video -> found.id
            is Found.Choice -> return Outcome.NeedsChoice(found.candidates)
        }
        check()
        val audio = downloadAudio(videoId, work, trackId, job.id, report)
        check()

        report(Stage.ANALYSING, 93)
        val analysis = optional("analysis") { withContext(Dispatchers.Default) { analyseAudio(audio) } }

        report(Stage.SAVING, 98)
        save(trackId, existing, resolved, genre, coverTone, artistTones, lyrics?.kind ?: LyricsKind.NONE, audio, analysis, videoId, work)
        return Outcome.Done(trackId)
    }

    private suspend fun <T> optional(what: String, block: suspend () -> T?): T? =
        runCatching { block() }.onFailure { if (it is JobCancelled) throw it; Log.w("Lumen", "optional stage $what failed", it) }.getOrNull()

    private suspend fun downloadImage(url: String?, work: File, name: String): CoverTone? {
        if (url.isNullOrBlank()) return null
        val source = http.download(url, File(work, "$name.src"))
        return withContext(Dispatchers.Default) { processSquareImage(source, File(work, "$name.jpg"), File(work, "${name}_s.jpg")) }
    }

    // link or pick -> Deezer metadata (or YouTube metadata when Deezer has no match)
    private suspend fun resolve(request: DownloadRequest): Resolved = when (request) {
        is DownloadRequest.DeezerPick -> fromDeezer(deezer.track(request.deezerId), request.videoId, null)
        is DownloadRequest.FreeText -> {
            val hit = deezer.search(request.query, 5).firstOrNull() ?: throw JobFailure("No match found for that search.")
            fromDeezer(deezer.track(hit.id), request.videoId, null)
        }
        is DownloadRequest.SpotifyLink -> {
            val url = spotify.expand(request.url)
            val id = spotifyTrackId(url) ?: throw JobFailure("Not a song link")
            val item = spotify.track(id)
            val hits = deezer.precise(item.artist, item.title).ifEmpty { deezer.search("${item.artist} ${item.title}", 5) }
            val hit = hits.firstOrNull { normalize(it.title).startsWith(normalize(item.title)) } ?: hits.firstOrNull()
                ?: throw JobFailure("No match found for that search.")
            fromDeezer(deezer.track(hit.id), request.videoId, id)
        }
        is DownloadRequest.YouTubeLink -> fromYouTube(request.url)
        is DownloadRequest.ReplaceAudio -> error("handled separately")
    }

    private suspend fun fromDeezer(track: DzTrack, videoId: String?, spotifyId: String?): Resolved {
        val album = track.album?.id?.let { id -> runCatching { deezer.album(id) }.getOrNull() }
        val contributors = track.contributors.ifEmpty { listOf(track.artist) }.distinctBy { it.id }
        val ordered = listOf(contributors.firstOrNull { it.id == track.artist.id } ?: track.artist) +
            contributors.filter { it.id != track.artist.id }
        val credits = ordered.mapIndexed { i, a ->
            Credit(artistIdFor(a.id, a.name), a.name, a.id, a.pictureXl, if (i == 0) ArtistRole.MAIN else ArtistRole.FEATURED)
        }
        val albumTitle = album?.title?.ifEmpty { null } ?: track.album?.title
        return Resolved(
            title = track.title.replace(FEAT_PART, "").trim().ifEmpty { track.title },
            durationSec = track.duration.takeIf { it > 0 },
            credits = credits,
            albumId = track.album?.let { albumIdFor(it.id, it.title, track.artist.name) },
            albumTitle = albumTitle,
            album = album,
            coverUrl = track.album?.coverXl ?: album?.coverXl,
            deezerId = track.id,
            isrc = track.isrc,
            bpm = track.bpm?.takeIf { it > 0 },
            explicit = track.explicit,
            releaseDate = track.releaseDate ?: album?.releaseDate,
            trackPosition = track.trackPosition,
            spotifyId = spotifyId,
            videoId = videoId,
        )
    }

    private suspend fun fromYouTube(url: String): Resolved {
        val meta = youtube.info(url)
        val uploader = meta.uploader.removeSuffix(" - Topic").trim()
        val (artistGuess, titleGuess) = if (" - " in meta.title && !meta.uploader.endsWith(" - Topic")) {
            meta.title.substringBefore(" - ").trim() to meta.title.substringAfter(" - ").trim()
        } else uploader to meta.title
        val cleanTitle = titleGuess.replace(Regex("""\s*[(\[](official|lyric|audio|video|hd|4k)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "").trim()
        val hit = runCatching { deezer.search("$artistGuess $cleanTitle", 5) }.getOrDefault(emptyList())
            .firstOrNull { meta.durationSec == null || abs(it.duration - meta.durationSec) <= 5 }
        if (hit != null) return fromDeezer(deezer.track(hit.id), meta.id, null)
        val split = splitCredits(cleanTitle, artistGuess)
        // reuse an artist already in the library with the same name, so it is not listed twice
        val known = db.artistDao().all().sortedBy { it.deezerId != null }.associateBy { normalize(it.name) }
        val credits = split.artists.mapIndexed { i, name ->
            val match = known[normalize(name)]
            Credit(match?.id ?: artistIdFor(null, name), match?.name ?: name, match?.deezerId, null, if (i == 0) ArtistRole.MAIN else ArtistRole.FEATURED)
        }
        return Resolved(
            title = split.title, durationSec = meta.durationSec, credits = credits, albumId = null, albumTitle = null,
            album = null, coverUrl = "https://i.ytimg.com/vi/${meta.id}/hqdefault.jpg", deezerId = null, isrc = null, bpm = null,
            explicit = false, releaseDate = null, trackPosition = null, spotifyId = null, videoId = meta.id,
        )
    }

    private sealed interface Found {
        data class Video(val id: String) : Found
        data class Choice(val candidates: List<CandidateUi>) : Found
    }

    private suspend fun findAudio(title: String, artist: String, durationSec: Int?): Found {
        val first = youtube.search("$artist - $title")
        bestCandidate(first, title, artist, durationSec)?.let { return Found.Video(it.videoId) }
        val merged = (first + youtube.search("$artist $title audio")).distinctBy { it.videoId }
        bestCandidate(merged, title, artist, durationSec)?.let { return Found.Video(it.videoId) }
        val top = rankedCandidates(merged, title, artist, durationSec).take(5).map(AudioCandidate::toUi)
        if (top.isEmpty()) throw JobFailure("No match found for that search.")
        return Found.Choice(top)
    }

    // yt-dlp progress 0..100 is shown as 35..90; one automatic yt-dlp update and retry on refusal
    private suspend fun downloadAudio(videoId: String, work: File, trackId: String, jobId: String, report: suspend (String, Int) -> Unit): File = coroutineScope {
        val progress = MutableStateFlow(-1)
        suspend fun attempt(): File = youtube.download(videoId, work, trackId, jobId) { progress.value = it }
        val ticker = launch {
            while (isActive) {
                delay(500)
                val p = progress.value
                if (p >= 0) report(Stage.AUDIO, 35 + (p * 0.55f).toInt())
            }
        }
        try {
            try {
                attempt()
            } catch (e: YtDlpError) {
                if (e.botCheck) throw JobFailure("YouTube asked to confirm you're not a bot. Try again later.")
                if (updatedThisRun) throw JobFailure("Download failed: ${e.message}")
                report(Stage.AUDIO, 35)
                Log.w("Lumen", "YouTube refused the download. Updating the downloader…", e)
                runCatching { ytdlp.update() }
                updatedThisRun = true
                try {
                    attempt()
                } catch (again: YtDlpError) {
                    throw JobFailure(if (again.botCheck) "YouTube asked to confirm you're not a bot. Try again later." else "Download failed: ${again.message}")
                }
            }
        } finally {
            ticker.cancel()
        }
    }

    private suspend fun replaceAudio(
        job: DownloadJobEntity,
        request: DownloadRequest.ReplaceAudio,
        work: File,
        report: suspend (String, Int) -> Unit,
        check: () -> Unit,
    ): Outcome {
        val track = db.trackDao().get(request.trackId) ?: throw JobFailure("That song is no longer in your library")
        report(Stage.AUDIO, 35)
        val audio = downloadAudio(request.videoId, work, track.id, job.id, report)
        check()
        report(Stage.ANALYSING, 93)
        val analysis = optional("analysis") { withContext(Dispatchers.Default) { analyseAudio(audio) } }
        report(Stage.SAVING, 98)
        val relative = paths.audioRelative(track.id, audio.extension)
        withContext(Dispatchers.IO) {
            paths.resolve(track.audioFile).delete()
            moveInto(audio, paths.resolve(relative))
        }
        db.trackDao().replaceAudio(track.id, relative, request.videoId, analysis?.waveform, analysis?.gainDb, paths.resolve(relative).length())
        return Outcome.Done(track.id, "Audio replaced")
    }

    private suspend fun save(
        trackId: String,
        existing: TrackEntity?,
        resolved: Resolved,
        genre: String?,
        coverTone: CoverTone?,
        artistTones: Map<String, CoverTone?>,
        lyricsKind: LyricsKind,
        audio: File,
        analysis: AudioAnalysis?,
        videoId: String,
        work: File,
    ) = withContext(Dispatchers.IO) {
        val relative = paths.audioRelative(trackId, audio.extension)
        existing?.let { paths.resolve(it.audioFile).delete() }
        moveInto(audio, paths.resolve(relative))
        val hasCover = File(work, "cover.jpg").exists()
        if (hasCover) {
            moveInto(File(work, "cover.jpg"), paths.cover(trackId))
            moveInto(File(work, "cover_s.jpg"), paths.coverThumb(trackId))
        }
        paths.syncedLyrics(trackId).delete()
        paths.plainLyrics(trackId).delete()
        File(work, "lyrics.lrc").takeIf { it.exists() }?.let { moveInto(it, paths.syncedLyrics(trackId)) }
        File(work, "lyrics.txt").takeIf { it.exists() }?.let { moveInto(it, paths.plainLyrics(trackId)) }
        val artistImages = resolved.credits.associate { credit ->
            val large = File(work, "artist_${credit.id}.jpg")
            val saved = large.exists()
            if (saved) {
                moveInto(large, paths.artistImage(credit.id))
                moveInto(File(work, "artist_${credit.id}_s.jpg"), paths.artistThumb(credit.id))
            }
            credit.id to saved
        }
        val now = System.currentTimeMillis()
        val primary = resolved.credits.first()
        db.withTransaction {
            for (credit in resolved.credits) {
                val known = db.artistDao().get(credit.id)
                val gotImage = artistImages[credit.id] == true
                val tone = if (gotImage) artistTones[credit.id] else null
                db.artistDao().upsert(
                    ArtistEntity(
                        id = credit.id, name = known?.name ?: credit.name, sortName = known?.sortName ?: sortKey(credit.name),
                        deezerId = credit.deezerId ?: known?.deezerId, hasImage = gotImage || known?.hasImage == true,
                        toneHue = tone?.hue ?: known?.toneHue, toneSat = tone?.saturation ?: known?.toneSat, hideSongs = known?.hideSongs == true,
                    )
                )
            }
            resolved.albumId?.let { albumId ->
                val known = db.albumDao().get(albumId)
                db.albumDao().upsert(
                    AlbumEntity(
                        id = albumId, title = resolved.albumTitle ?: known?.title ?: resolved.title, artistId = known?.artistId ?: primary.id,
                        releaseDate = resolved.album?.releaseDate ?: resolved.releaseDate ?: known?.releaseDate,
                        genre = genre ?: known?.genre, coverTrackId = known?.coverTrackId ?: trackId, hideSongs = known?.hideSongs == true,
                    )
                )
            }
            db.trackDao().upsert(
                TrackEntity(
                    id = trackId, title = resolved.title, sortTitle = sortKey(resolved.title), albumId = resolved.albumId,
                    trackPosition = resolved.trackPosition, durationMs = resolved.durationSec?.let { it * 1000L } ?: analysis?.durationMs ?: 0L,
                    audioFile = relative, hasCover = hasCover, lyricsKind = lyricsKind, lyricsOffsetMs = existing?.lyricsOffsetMs ?: 0,
                    genre = genre, releaseDate = resolved.releaseDate, isrc = resolved.isrc, deezerId = resolved.deezerId,
                    spotifyId = resolved.spotifyId, youtubeId = videoId, bpm = resolved.bpm, explicit = resolved.explicit,
                    toneHue = coverTone?.hue, toneSat = coverTone?.saturation, waveform = analysis?.waveform, gainDb = analysis?.gainDb,
                    fileSize = paths.resolve(relative).length(), addedAt = now, likedAt = null, deletedAt = null,
                )
            )
            db.trackArtistDao().deleteForTrack(trackId)
            db.trackArtistDao().upsertAll(resolved.credits.mapIndexed { i, c -> TrackArtistEntity(trackId, c.id, i, c.role) })
        }
    }
}

fun AudioCandidate.toUi() = CandidateUi(videoId, title, channel, durationSec)
