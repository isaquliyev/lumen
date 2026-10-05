package com.lumen.player.download.sources

import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.net.Http
import com.lumen.player.core.net.LumenJson
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
data class LrcRecord(
    val id: Long = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

data class LyricsResult(val kind: LyricsKind, val text: String?)

object LrcLibPick {
    fun record(text: String) = LumenJson.decodeFromString<LrcRecord>(text)
    fun records(text: String) = LumenJson.decodeFromString<List<LrcRecord>>(text)

    // the pick order from the plan: synced from get, synced from search (±3 s), plain, instrumental
    fun choose(exact: LrcRecord?, search: List<LrcRecord>, durationSec: Int): LyricsResult {
        exact?.syncedLyrics?.takeIf { it.isNotBlank() }?.let { return LyricsResult(LyricsKind.SYNCED, it) }
        search.firstOrNull { !it.syncedLyrics.isNullOrBlank() && it.duration != null && abs(it.duration - durationSec) <= 3 }
            ?.let { return LyricsResult(LyricsKind.SYNCED, it.syncedLyrics) }
        (exact?.plainLyrics?.takeIf { it.isNotBlank() } ?: search.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics)
            ?.let { return LyricsResult(LyricsKind.PLAIN, it) }
        if (exact?.instrumental == true || search.any { it.instrumental }) return LyricsResult(LyricsKind.INSTRUMENTAL, null)
        return LyricsResult(LyricsKind.NONE, null)
    }
}

@Singleton
class LrcLibApi @Inject constructor(private val http: Http) {
    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    suspend fun fetch(title: String, artist: String, album: String?, durationSec: Int): LyricsResult = runCatching {
        val exactUrl = "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
            "&album_name=${enc(album.orEmpty())}&duration=$durationSec"
        val exact = http.text(exactUrl)?.let { LrcLibPick.record(it) }
        if (!exact?.syncedLyrics.isNullOrBlank()) return LyricsResult(LyricsKind.SYNCED, exact.syncedLyrics)
        val search = http.text("https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}")
            ?.let { LrcLibPick.records(it) }.orEmpty()
        LrcLibPick.choose(exact, search, durationSec)
    }.getOrElse { LyricsResult(LyricsKind.NONE, null) }
}
