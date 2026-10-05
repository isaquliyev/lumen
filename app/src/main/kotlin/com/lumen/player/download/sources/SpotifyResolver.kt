package com.lumen.player.download.sources

import com.lumen.player.core.net.Http
import com.lumen.player.core.net.LumenJson
import com.lumen.player.download.JobFailure
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private val SPOTIFY_TRACK = Regex("""https?://open\.spotify\.com/(?:intl-[a-z-]+/)?track/([A-Za-z0-9]{22})""", RegexOption.IGNORE_CASE)
private val SPOTIFY_ALBUM = Regex("""https?://open\.spotify\.com/(?:intl-[a-z-]+/)?album/([A-Za-z0-9]{22})""", RegexOption.IGNORE_CASE)
private val OG_DESCRIPTION = Regex("""<meta property="og:description" content="([^"]+)"""")

fun spotifyTrackId(text: String): String? = SPOTIFY_TRACK.find(text)?.groupValues?.get(1)

fun spotifyAlbumId(text: String): String? = SPOTIFY_ALBUM.find(text)?.groupValues?.get(1)

fun htmlUnescape(text: String): String = text
    .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
    .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

// artist from the track page: "Talk Talk · Laughing Stock · Song · 1991" -> "Talk Talk"
fun spotifyArtistFromPage(html: String): String? =
    OG_DESCRIPTION.find(html)?.groupValues?.get(1)
        ?.substringBefore(" · ")
        ?.let { htmlUnescape(it).trim() }
        ?.takeIf { it.isNotEmpty() }

@Serializable
private data class OEmbed(val title: String? = null)

data class SpotifyItem(val title: String, val artist: String)

@Singleton
class SpotifyResolver @Inject constructor(private val http: Http) {

    // follows spotify.link short links to the real open.spotify.com URL
    suspend fun expand(url: String): String =
        if (url.contains("spotify.link") || url.contains("spoti.fi")) runCatching { http.finalUrl(url) }.getOrDefault(url) else url

    suspend fun track(id: String) = item("https://open.spotify.com/track/$id")

    suspend fun album(id: String) = item("https://open.spotify.com/album/$id")

    private suspend fun item(pageUrl: String): SpotifyItem {
        val oembed = http.text("https://open.spotify.com/oembed?url=${URLEncoder.encode(pageUrl, "UTF-8")}")
            ?: throw JobFailure("No match found for that search.")
        val title = LumenJson.decodeFromString<OEmbed>(oembed).title ?: throw JobFailure("No match found for that search.")
        // a desktop browser agent now gets an empty web-player shell, so use our own agent (crawler agent as backup)
        val artist = spotifyArtistFromPage(http.text(pageUrl).orEmpty())
            ?: spotifyArtistFromPage(http.text(pageUrl, mapOf("User-Agent" to "facebookexternalhit/1.1")).orEmpty())
            ?: throw JobFailure("No match found for that search.")
        return SpotifyItem(title, artist)
    }
}
