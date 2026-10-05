package com.lumen.player.download.sources

import android.content.Context
import android.util.Log
import com.lumen.player.core.ApplicationScope
import com.lumen.player.core.util.AudioCandidate
import com.lumen.player.download.JobFailure
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VideoMeta(val id: String, val title: String, val uploader: String, val durationSec: Int?, val thumbnail: String?)

object YtDlpParse {
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    fun searchEntries(json: String): List<AudioCandidate> {
        val root = Json.parseToJsonElement(json).jsonObject
        val entries = root["entries"]?.jsonArray ?: return emptyList()
        return entries.mapNotNull { element ->
            val entry = element.jsonObject
            val id = entry.str("id") ?: return@mapNotNull null
            AudioCandidate(
                videoId = id,
                title = entry.str("title").orEmpty(),
                channel = entry.str("channel") ?: entry.str("uploader").orEmpty(),
                durationSec = entry["duration"]?.jsonPrimitive?.doubleOrNull?.toInt(),
            )
        }
    }

    fun video(json: String): VideoMeta {
        val root = Json.parseToJsonElement(json).jsonObject
        return VideoMeta(
            id = root.str("id").orEmpty(),
            title = root.str("track") ?: root.str("title").orEmpty(),
            uploader = root.str("artist") ?: root.str("channel") ?: root.str("uploader").orEmpty(),
            durationSec = root["duration"]?.jsonPrimitive?.doubleOrNull?.toInt(),
            thumbnail = root.str("thumbnail"),
        )
    }
}

class YtDlpError(message: String, val botCheck: Boolean) : Exception(message)

@Singleton
class YtDlpManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version
    private val updateMutex = Mutex()

    fun init() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                YoutubeDL.getInstance().init(context)
                _version.value = YoutubeDL.getInstance().version(context)
                _ready.value = true
                Log.i("Lumen", "yt-dlp ready: ${_version.value}")
            }.onFailure { Log.e("Lumen", "yt-dlp init failed", it) }
        }
    }

    suspend fun awaitReady() = ready.first { it }

    suspend fun update(): String = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            awaitReady()
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            _version.value = YoutubeDL.getInstance().version(context)
            Log.i("Lumen", "yt-dlp update: $status ${_version.value}")
            if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) "Already up to date" else "Updated to ${_version.value}"
        }
    }
}

@Singleton
class YtDlpAudio @Inject constructor(private val manager: YtDlpManager) {

    private fun classify(e: Throwable): Throwable {
        if (e is YoutubeDL.CanceledException) return e
        val text = e.message.orEmpty()
        val bot = "Sign in to confirm" in text || "not a bot" in text
        val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("ERROR") } ?: text.lineSequence().firstOrNull().orEmpty()
        return YtDlpError(firstLine.removePrefix("ERROR:").trim().take(160), bot)
    }

    private suspend fun <T> run(block: () -> T): T = withContext(Dispatchers.IO) {
        manager.awaitReady()
        try {
            block()
        } catch (e: YoutubeDLException) {
            throw classify(e)
        }
    }

    suspend fun search(query: String, count: Int = 10): List<AudioCandidate> = run {
        val request = YoutubeDLRequest("ytsearch$count:$query").apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
        }
        val response = YoutubeDL.getInstance().execute(request)
        if (response.err.isNotBlank()) Log.d("Lumen", "yt-dlp stderr: ${response.err.take(500)}")
        YtDlpParse.searchEntries(response.out)
    }

    suspend fun info(url: String): VideoMeta = run {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
        }
        YtDlpParse.video(YoutubeDL.getInstance().execute(request).out)
    }

    suspend fun download(videoId: String, workDir: File, trackId: String, jobId: String, preferM4a: Boolean, onProgress: (Int) -> Unit): File = run {
        workDir.mkdirs()
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
            addOption("-f", if (preferM4a) "bestaudio[ext=m4a]/bestaudio" else "bestaudio")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-part")
            addOption("-o", File(workDir, "$trackId.%(ext)s").absolutePath)
        }
        val response = YoutubeDL.getInstance().execute(request, jobId) { progress, _, _ ->
            if (progress >= 0) onProgress(progress.toInt().coerceIn(0, 100))
        }
        response.err.lineSequence().filter { "WARNING" in it || "JavaScript" in it }.forEach { Log.w("Lumen", "yt-dlp: $it") }
        workDir.listFiles { f -> f.name.startsWith(trackId) && !f.name.endsWith(".part") }?.singleOrNull()
            ?: throw JobFailure("Download failed: the audio file is missing")
    }

    fun cancel(jobId: String) = runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }
}
