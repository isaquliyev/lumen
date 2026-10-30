package com.lumen.player.download

import com.lumen.player.download.sources.DeezerApi
import com.lumen.player.download.sources.SpotifyResolver
import com.lumen.player.download.sources.spotifyAlbumId
import com.lumen.player.download.sources.spotifyTrackId
import javax.inject.Inject
import javax.inject.Singleton

private val URL = Regex("""https?://[^\s<>"]+""")

fun firstUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', '!')

enum class LinkKind { SPOTIFY_TRACK, SPOTIFY_ALBUM, SPOTIFY_SHORT, YOUTUBE }

fun classifyLink(url: String): LinkKind? {
    val host = url.substringAfter("://").substringBefore('/').lowercase()
    return when {
        spotifyTrackId(url) != null -> LinkKind.SPOTIFY_TRACK
        spotifyAlbumId(url) != null -> LinkKind.SPOTIFY_ALBUM
        host == "spotify.link" || host == "spoti.fi" -> LinkKind.SPOTIFY_SHORT
        host.endsWith("youtube.com") || host == "youtu.be" -> LinkKind.YOUTUBE
        else -> null
    }
}

private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

// the 11-character video id from youtu.be/ID, youtube.com/watch?v=ID, /shorts/ID, /embed/ID or /live/ID
fun youtubeVideoId(text: String): String? {
    val url = firstUrl(text.trim()) ?: return null
    if (classifyLink(url) != LinkKind.YOUTUBE) return null
    val host = url.substringAfter("://").substringBefore('/').lowercase()
    val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val id = when {
        host == "youtu.be" -> path.substringBefore('/')
        path == "watch" -> url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        else -> path.split('/').takeIf { it.size >= 2 && it[0] in setOf("shorts", "embed", "live", "v") }?.get(1)
    }
    return id?.takeIf { VIDEO_ID.matches(it) }
}

// turns a shared or pasted link into download jobs; returns the message for the snackbar
@Singleton
class LinkHandler @Inject constructor(
    private val queue: DownloadQueue,
    private val spotify: SpotifyResolver,
    private val deezer: DeezerApi,
) {
    suspend fun handle(text: String): String {
        var url = firstUrl(text) ?: return "Not a song link"
        var kind = classifyLink(url) ?: return "Not a song link"
        if (kind == LinkKind.SPOTIFY_SHORT) {
            url = spotify.expand(url)
            kind = classifyLink(url)?.takeIf { it != LinkKind.SPOTIFY_SHORT } ?: return "Not a song link"
        }
        return when (kind) {
            LinkKind.SPOTIFY_TRACK -> {
                queue.enqueue(DownloadRequest.SpotifyLink(url), "Spotify song", "Finding the song…", null)
                "Added to downloads"
            }
            LinkKind.YOUTUBE -> {
                queue.enqueue(DownloadRequest.YouTubeLink(url), "YouTube video", "Finding the song…", null)
                "Added to downloads"
            }
            LinkKind.SPOTIFY_ALBUM -> runCatching {
                val item = spotify.album(spotifyAlbumId(url)!!)
                val hit = deezer.preciseAlbum(item.artist, item.title).firstOrNull()
                    ?: deezer.searchAlbums("${item.artist} ${item.title}", 1).firstOrNull()
                    ?: return "No match found for that album."
                "Added ${enqueueAlbum(hit.id)} songs to downloads"
            }.getOrElse { it.message ?: "Not a song link" }
            else -> "Not a song link"
        }
    }

    suspend fun enqueueAlbum(albumId: Long): Int {
        val album = deezer.album(albumId)
        val tracks = album.tracks?.data.orEmpty()
        tracks.forEach { track ->
            queue.enqueue(DownloadRequest.DeezerPick(track.id), track.title, track.artist.name, album.coverMedium ?: album.coverXl)
        }
        return tracks.size
    }
}
