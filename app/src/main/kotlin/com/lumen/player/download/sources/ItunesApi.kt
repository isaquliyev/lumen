package com.lumen.player.download.sources

import com.lumen.player.core.net.Http
import com.lumen.player.core.net.LumenJson
import com.lumen.player.core.util.normalize
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
data class ItunesTrack(
    val trackName: String? = null,
    val artistName: String? = null,
    val trackTimeMillis: Long? = null,
    val primaryGenreName: String? = null,
)

@Serializable
data class ItunesResults(val results: List<ItunesTrack> = emptyList())

object ItunesParse {
    fun results(text: String) = LumenJson.decodeFromString<ItunesResults>(text).results

    // first result whose artist matches and whose length is within 5 s
    fun pickGenre(results: List<ItunesTrack>, artist: String, durationMs: Long): String? {
        val want = normalize(artist)
        return results.firstOrNull { result ->
            val name = normalize(result.artistName.orEmpty())
            val sameArtist = name == want || name.contains(want) || want.contains(name)
            val closeLength = result.trackTimeMillis?.let { abs(it - durationMs) <= 5_000 } ?: false
            sameArtist && closeLength && !result.primaryGenreName.isNullOrBlank()
        }?.primaryGenreName
    }
}

@Singleton
class ItunesApi @Inject constructor(private val http: Http) {
    suspend fun genre(artist: String, title: String, durationMs: Long): String? = runCatching {
        val term = URLEncoder.encode("$artist $title", "UTF-8")
        val text = http.text("https://itunes.apple.com/search?term=$term&entity=song&limit=5") ?: return null
        ItunesParse.pickGenre(ItunesParse.results(text), artist, durationMs)
    }.getOrNull()
}
