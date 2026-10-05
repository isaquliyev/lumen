package com.lumen.player.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val USER_AGENT = "Lumen/1.0 (personal music player)"
const val DESKTOP_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36"

val LumenJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

class HttpException(val code: Int, message: String) : IOException(message)

fun Throwable.isOffline(): Boolean = this is UnknownHostException || this is ConnectException ||
    (this is SocketTimeoutException) || cause?.isOffline() == true

// spaces calls to one host so we stay under its rate limit
class RateLimiter(private val minGapMs: Long) {
    private val mutex = Mutex()
    private var last = 0L

    suspend fun acquire() = mutex.withLock {
        val wait = last + minGapMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        last = System.currentTimeMillis()
    }
}

@Singleton
class Http @Inject constructor() {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.header("User-Agent") == null) builder.header("User-Agent", USER_AGENT)
            chain.proceed(builder.build())
        }
        .build()

    private val limiters = mapOf(
        "api.deezer.com" to RateLimiter(120),
        "itunes.apple.com" to RateLimiter(1000),
        "lrclib.net" to RateLimiter(200),
        "open.spotify.com" to RateLimiter(300),
    )

    private suspend fun limit(url: String) {
        val host = url.substringAfter("://").substringBefore('/')
        limiters[host]?.acquire()
    }

    // returns null for 404, throws for other errors
    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
        limit(url)
        val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw HttpException(response.code, "HTTP ${response.code} for $url")
            response.body.string()
        }
    }

    suspend fun finalUrl(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", DESKTOP_AGENT).build()).execute().use {
            it.request.url.toString()
        }
    }

    suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        limit(url)
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "HTTP ${response.code} for $url")
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> response.body.byteStream().copyTo(out) }
        }
        target
    }
}
