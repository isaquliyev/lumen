package com.lumen.player.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lumen.player.core.db.DownloadJobDao
import com.lumen.player.core.db.DownloadJobEntity
import com.lumen.player.core.db.JobStatus
import com.lumen.player.core.util.newId
import com.lumen.player.data.SettingsRepository
import com.lumen.player.download.sources.YtDlpAudio
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadJobDao,
    private val settings: SettingsRepository,
    private val youtube: YtDlpAudio,
) {
    val jobs = dao.observeAll()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val claimMutex = Mutex()

    suspend fun enqueue(request: DownloadRequest, title: String, artist: String, coverUrl: String?): String {
        val now = System.currentTimeMillis()
        val id = newId()
        dao.insert(
            DownloadJobEntity(
                id = id, request = request.encode(), title = title, artist = artist, coverUrl = coverUrl,
                status = JobStatus.QUEUED, stage = Stage.QUEUED, progress = 0, error = null, candidates = null,
                trackId = null, createdAt = now, updatedAt = now,
            )
        )
        schedule()
        return id
    }

    suspend fun cancel(id: String) {
        val job = dao.get(id) ?: return
        when (job.status) {
            JobStatus.QUEUED, JobStatus.NEEDS_CHOICE ->
                dao.setState(id, JobStatus.CANCELLED, job.stage, job.progress, null, System.currentTimeMillis())
            JobStatus.RUNNING -> {
                cancelled += id
                youtube.cancel(id)
            }
            else -> Unit
        }
    }

    fun isCancelled(id: String) = id in cancelled

    fun forget(id: String) = cancelled.remove(id)

    suspend fun retry(id: String) {
        val job = dao.get(id) ?: return
        dao.upsert(job.copy(status = JobStatus.QUEUED, stage = Stage.QUEUED, progress = 0, error = null, updatedAt = System.currentTimeMillis()))
        schedule()
    }

    suspend fun choose(id: String, videoId: String) {
        val job = dao.get(id) ?: return
        val request = decodeRequest(job.request).withVideo(videoId)
        dao.upsert(
            job.copy(
                request = request.encode(), status = JobStatus.QUEUED, stage = Stage.QUEUED, progress = 0, error = null,
                candidates = null, updatedAt = System.currentTimeMillis(),
            )
        )
        schedule()
    }

    suspend fun clearFinished() = dao.deleteFinished()

    suspend fun claimNext(): DownloadJobEntity? = claimMutex.withLock {
        val next = dao.nextQueued() ?: return null
        if (dao.claim(next.id, System.currentTimeMillis()) == 1) next.copy(status = JobStatus.RUNNING) else null
    }

    // jobs left RUNNING by a killed process start again
    suspend fun resumeAfterStart() {
        dao.resetRunning()
        if (dao.activeCount() > 0) schedule()
        val updates = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.UNMETERED))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ytdlp-update", ExistingPeriodicWorkPolicy.KEEP, updates)
    }

    suspend fun schedule() {
        val wifiOnly = settings.current().wifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints(requiredNetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("download-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
