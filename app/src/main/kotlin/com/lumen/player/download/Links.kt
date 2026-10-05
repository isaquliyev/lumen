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
