package com.lumen.player.download.sources

import com.lumen.player.core.net.DESKTOP_AGENT
import com.lumen.player.core.net.Http
import com.lumen.player.core.util.AudioCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

// NewPipe Extractor reads YouTube from Java, much faster on the phone than yt-dlp (which runs Python);
// it is used for streaming only, and yt-dlp is the fallback whenever it fails
@Singleton
class NewPipeSource @Inject constructor(private val http: Http) {

    @Volatile private var ready = false

    private fun init() {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(OkHttpDownloader(http))
                ready = true
            }
        }
    }

    suspend fun search(query: String): List<AudioCandidate> = withContext(Dispatchers.IO) {
        init()
        val youtube = ServiceList.YouTube
        val handler = youtube.searchQHFactory.fromQuery(query, listOf("videos"), "")
        SearchInfo.getInfo(youtube, handler).relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            val id = videoIdOf(item.url) ?: return@mapNotNull null
            AudioCandidate(id, item.name.orEmpty(), item.uploaderName.orEmpty(), item.duration.toInt().takeIf { it > 0 })
        }
    }

    // the best audio-only stream with a direct link (m4a first, like downloads), plus the headers it needs
    suspend fun streamUrl(videoId: String): StreamUrl = withContext(Dispatchers.IO) {
        init()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.suffix == "m4a" }.thenByDescending { it.averageBitrate })
            .firstOrNull() ?: error("no audio stream")
        StreamUrl(audio.content, headersFor(audio.content))
    }

    private fun videoIdOf(url: String): String? = url.substringAfter("v=", "").substringBefore('&').takeIf { it.length == 11 }

    // YouTube checks that the player asks for a link with the same kind of client that received it
    private fun headersFor(url: String): Map<String, String> =
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
        else mapOf("User-Agent" to DESKTOP_AGENT, "Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/")
}

private class OkHttpDownloader(private val http: Http) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", DESKTOP_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        http.client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(response.code, response.message, response.headers.toMultimap(), response.body.string(), response.request.url.toString())
        }
    }
}
