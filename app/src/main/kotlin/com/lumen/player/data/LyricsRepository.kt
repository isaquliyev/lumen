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
        val text = file?.takeIf { it.exists() }?.readText()
        TrackLyrics(if (text == null && file != null) LyricsKind.NONE else track.lyricsKind, text?.let(::parseLyrics))
    }
}
