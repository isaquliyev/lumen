package com.lumen.player.download.sources

import com.lumen.player.core.net.Http
import com.lumen.player.core.net.LumenJson
import com.lumen.player.download.JobFailure
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DzError(val type: String? = null, val message: String? = null, val code: Int? = null)

@Serializable
data class DzArtist(
    val id: Long,
    val name: String,
    @SerialName("picture_xl") val pictureXl: String? = null,
    @SerialName("picture_medium") val pictureMedium: String? = null,
    val role: String? = null,
)

@Serializable
data class DzAlbumRef(
    val id: Long,
    val title: String,
    @SerialName("cover_xl") val coverXl: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
)

@Serializable
data class DzTrack(
    val id: Long,
    val title: String,
    @SerialName("title_short") val titleShort: String? = null,
    val duration: Int = 0,
    @SerialName("explicit_lyrics") val explicit: Boolean = false,
    val artist: DzArtist,
    val album: DzAlbumRef? = null,
    val isrc: String? = null,
    val bpm: Float? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("track_position") val trackPosition: Int? = null,
    @SerialName("disk_number") val diskNumber: Int? = null,
    val contributors: List<DzArtist> = emptyList(),
    val error: DzError? = null,
)

@Serializable
data class DzGenre(val name: String)

@Serializable
data class DzGenres(val data: List<DzGenre> = emptyList())

@Serializable
data class DzTrackList(val data: List<DzTrack> = emptyList())

@Serializable
data class DzAlbum(
    val id: Long = 0,
    val title: String = "",
    @SerialName("cover_xl") val coverXl: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
    val genres: DzGenres? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("nb_tracks") val trackCount: Int? = null,
    val artist: DzArtist? = null,
    val tracks: DzTrackList? = null,
    val error: DzError? = null,
)

@Serializable
data class DzSearch(val data: List<DzTrack> = emptyList(), val error: DzError? = null)

@Serializable
data class DzAlbumSearch(val data: List<DzAlbum> = emptyList(), val error: DzError? = null)

object DeezerParse {
    fun search(text: String) = LumenJson.decodeFromString<DzSearch>(text)
    fun albumSearch(text: String) = LumenJson.decodeFromString<DzAlbumSearch>(text)
    fun track(text: String) = LumenJson.decodeFromString<DzTrack>(text)
    fun album(text: String) = LumenJson.decodeFromString<DzAlbum>(text)
    fun artist(text: String) = LumenJson.decodeFromString<DzArtist>(text)
    fun errorCode(text: String): Int? = runCatching {
        LumenJson.decodeFromString<DzSearch>(text).error?.code
    }.getOrNull()
}

@Singleton
class DeezerApi @Inject constructor(private val http: Http) {

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    // Deezer sends errors as HTTP 200; code 4 = quota, so back off 1 s, 2 s, 4 s
    private suspend fun get(path: String): String {
        val url = "https://api.deezer.com/$path"
        for (wait in listOf(1_000L, 2_000L, 4_000L, -1L)) {
            val text = http.text(url, mapOf("Accept-Language" to "en")) ?: throw JobFailure("No match found for that search.")
            val code = DeezerParse.errorCode(text)
            if (code == 4) {
                if (wait < 0) throw JobFailure("Search is busy. Try again in a minute.")
                delay(wait)
                continue
            }
            if (code != null) throw JobFailure("No match found for that search.")
            return text
        }
        throw JobFailure("Search is busy. Try again in a minute.")
    }

    suspend fun search(query: String, limit: Int = 25): List<DzTrack> =
        DeezerParse.search(get("search?q=${enc(query)}&limit=$limit")).data

    suspend fun searchAlbums(query: String, limit: Int = 5): List<DzAlbum> =
        DeezerParse.albumSearch(get("search/album?q=${enc(query)}&limit=$limit")).data

    suspend fun precise(artist: String, title: String): List<DzTrack> =
        DeezerParse.search(get("search?q=${enc("artist:\"$artist\" track:\"$title\"")}&limit=10")).data

    suspend fun preciseAlbum(artist: String, album: String): List<DzAlbum> =
        DeezerParse.albumSearch(get("search/album?q=${enc("artist:\"$artist\" album:\"$album\"")}&limit=5")).data

    suspend fun track(id: Long): DzTrack = DeezerParse.track(get("track/$id"))

    suspend fun album(id: Long): DzAlbum = DeezerParse.album(get("album/$id"))

    suspend fun artist(id: Long): DzArtist = DeezerParse.artist(get("artist/$id"))
}
