package com.lumen.player.data

import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.util.ParsedLyrics
import com.lumen.player.core.util.parseLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TrackLyrics(val kind: LyricsKind, val parsed: ParsedLyrics?)

@Singleton
class LyricsRepository @Inject constructor(private val paths: LumenPaths) {
    suspend fun load(track: TrackRow): TrackLyrics = withContext(Dispatchers.IO) {
        val file = when (track.lyricsKind) {
            LyricsKind.SYNCED -> paths.syncedLyrics(track.id)
            LyricsKind.PLAIN -> paths.plainLyrics(track.id)
            else -> null
        }
        val parsed = file?.takeIf { it.exists() }?.readText()?.let(::parseLyrics)
        // only timed lyrics with real text are shown; anything else counts as no lyrics
        val usable = parsed != null && parsed.synced && parsed.lines.any { it.text.isNotBlank() }
        when {
            usable -> TrackLyrics(LyricsKind.SYNCED, parsed)
            track.lyricsKind == LyricsKind.INSTRUMENTAL -> TrackLyrics(LyricsKind.INSTRUMENTAL, null)
            else -> TrackLyrics(LyricsKind.NONE, null)
        }
    }
}
