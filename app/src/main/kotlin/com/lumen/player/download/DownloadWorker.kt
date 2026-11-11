package com.lumen.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lumen.player.R
import com.lumen.player.core.db.DownloadJobDao
import com.lumen.player.core.db.DownloadJobEntity
import com.lumen.player.core.db.JobStatus
import com.lumen.player.core.net.isOffline
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.download.sources.YtDlpManager
import com.yausername.youtubedl_android.YoutubeDL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

const val DOWNLOAD_CHANNEL = "downloads"
private const val NOTIFICATION_ID = 2001

fun createDownloadChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(DOWNLOAD_CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val queue: DownloadQueue,
    private val pipeline: DownloadPipeline,
    private val dao: DownloadJobDao,
    private val paths: LumenPaths,
) : CoroutineWorker(context, params) {

    private val running = ConcurrentHashMap<String, Pair<String, Int>>()

    override suspend fun getForegroundInfo() = foregroundInfo("Preparing downloads…", 0)

    private fun foregroundInfo(text: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Adding music")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, progress, progress == 0)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun refreshNotification() {
        val current = running.values.firstOrNull() ?: return
        val waiting = dao.activeCount() - 1
        val more = if (waiting > 0) " (+$waiting waiting)" else ""
        runCatching { setForeground(foregroundInfo("${current.first}$more", current.second)) }
    }

    override suspend fun doWork(): Result {
        createDownloadChannel(applicationContext)
        runCatching { setForeground(getForegroundInfo()) }
        // one song at a time, oldest first; songs added meanwhile wait in the queue
        while (true) {
            val job = queue.claimNext() ?: break
            runJob(job)
        }
        return Result.success()
    }

    private suspend fun runJob(job: DownloadJobEntity) {
        val now = { System.currentTimeMillis() }
        running[job.id] = job.title to 0
        try {
            val outcome = pipeline.run(
                job,
                report = { stage, progress ->
                    if (running[job.id]?.second != progress) Log.d("Lumen", "job ${job.id} $stage $progress")
                    dao.setProgress(job.id, stage, progress, now())
                    running[job.id] = job.title to progress
                    refreshNotification()
                },
                cancelled = { queue.isCancelled(job.id) },
            )
            when (outcome) {
                is Outcome.Done ->
                    dao.finish(job.id, JobStatus.DONE, Stage.DONE, 100, outcome.message, outcome.trackId, null, now())
                is Outcome.NeedsChoice -> dao.finish(
                    job.id, JobStatus.NEEDS_CHOICE, Stage.AUDIO, 35, "No confident audio match. Choose one.", null,
                    encodeCandidates(outcome.candidates), now(),
                )
            }
        } catch (e: Throwable) {
            val wasCancelled = e is JobCancelled || e is YoutubeDL.CanceledException || queue.isCancelled(job.id)
            if (e is CancellationException && !wasCancelled) throw e
            val message = when {
                wasCancelled -> null
                e is JobFailure -> e.message
                e.isOffline() -> "No internet connection."
                else -> "Download failed: ${e.message?.lineSequence()?.firstOrNull().orEmpty().take(160)}"
            }
            Log.w("Lumen", "job ${job.id} ended: ${message ?: "cancelled"}", e)
            dao.setState(job.id, if (wasCancelled) JobStatus.CANCELLED else JobStatus.FAILED, Stage.QUEUED, 0, message, now())
        } finally {
            paths.workDir(job.id).deleteRecursively()
            queue.forget(job.id)
            running.remove(job.id)
        }
    }
}

@HiltWorker
class YtDlpUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: YtDlpManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { manager.update() }.fold({ Result.success() }, { Result.retry() })
}
