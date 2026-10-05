package com.lumen.player.download

import com.lumen.player.core.net.LumenJson
import kotlinx.serialization.Serializable

@Serializable
sealed interface DownloadRequest {
    @Serializable data class DeezerPick(val deezerId: Long, val videoId: String? = null) : DownloadRequest
    @Serializable data class SpotifyLink(val url: String, val videoId: String? = null) : DownloadRequest
    @Serializable data class YouTubeLink(val url: String) : DownloadRequest
    @Serializable data class FreeText(val query: String, val videoId: String? = null) : DownloadRequest
    @Serializable data class ReplaceAudio(val trackId: String, val videoId: String) : DownloadRequest
}

// the user picked this video in the "Choose the right audio" sheet
fun DownloadRequest.withVideo(videoId: String): DownloadRequest = when (this) {
    is DownloadRequest.DeezerPick -> copy(videoId = videoId)
    is DownloadRequest.SpotifyLink -> copy(videoId = videoId)
    is DownloadRequest.FreeText -> copy(videoId = videoId)
    is DownloadRequest.YouTubeLink -> this
    is DownloadRequest.ReplaceAudio -> copy(videoId = videoId)
}

fun DownloadRequest.encode(): String = LumenJson.encodeToString(DownloadRequest.serializer(), this)

fun decodeRequest(text: String): DownloadRequest = LumenJson.decodeFromString(DownloadRequest.serializer(), text)

@Serializable
data class CandidateUi(val videoId: String, val title: String, val channel: String, val durationSec: Int?) {
    val thumbnail get() = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
}

fun encodeCandidates(list: List<CandidateUi>) = LumenJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(CandidateUi.serializer()), list)

fun decodeCandidates(text: String?): List<CandidateUi> =
    text?.let { runCatching { LumenJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CandidateUi.serializer()), it) }.getOrNull() }
        .orEmpty()

// a failure with a short message that can be shown to the user as it is
class JobFailure(message: String, val needsChoice: List<CandidateUi>? = null) : Exception(message)

object Stage {
    const val QUEUED = "queued"
    const val RESOLVING = "resolving"
    const val METADATA = "metadata"
    const val COVER = "cover"
    const val ARTISTS = "artists"
    const val LYRICS = "lyrics"
    const val AUDIO = "audio"
    const val ANALYSING = "analysing"
    const val SAVING = "saving"
    const val DONE = "done"

    fun label(stage: String) = when (stage) {
        QUEUED -> "Waiting…"
        RESOLVING -> "Finding the song…"
        METADATA -> "Reading details…"
        COVER -> "Getting the cover…"
        ARTISTS -> "Getting artist photos…"
        LYRICS -> "Finding lyrics…"
        AUDIO -> "Finding audio…"
        ANALYSING -> "Analysing…"
        SAVING -> "Saving…"
        else -> "Done"
    }
}
