package com.lumen.player.data

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import androidx.room.withTransaction
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.db.TrackEntity
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.storage.SnapshotManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class FolderUsage(val name: String, val bytes: Long, val files: Int)

data class CheckResult(val missing: List<TrackEntity>, val orphans: List<File>) {
    val clean get() = missing.isEmpty() && orphans.isEmpty()
}

// storage usage, "Check library", restore, reset statistics and log export
@Singleton
class LibraryMaintenance @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LumenDatabase,
    private val paths: LumenPaths,
    private val tracks: TrackRepository,
) {
    suspend fun usage(): List<FolderUsage> = withContext(Dispatchers.IO) {
        LumenPaths.FOLDERS.map { name ->
            val files = File(paths.root, name).walkTopDown().filter { it.isFile }.toList()
            FolderUsage(name, files.sumOf { it.length() }, files.size)
        }
    }

    fun freeBytes(): Long = paths.root.usableSpace

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val active = db.trackDao().allActive()
        val missing = active.filter { !paths.resolve(it.audioFile).exists() && !it.id.startsWith("seed") }
        val known = mutableSetOf<String>()
        active.forEach { t ->
            known += File(t.audioFile).name
            known += "${t.id}.jpg"; known += "${t.id}_s.jpg"; known += "${t.id}.lrc"; known += "${t.id}.txt"
        }
        val orphans = listOf("audio", "covers", "lyrics").flatMap { folder ->
            File(paths.root, folder).listFiles()?.filter { it.isFile && it.name !in known }.orEmpty()
        }
        CheckResult(missing, orphans)
    }

    // missing songs are removed from the library (their stats stay), orphan files are deleted
    suspend fun fix(result: CheckResult) = withContext(Dispatchers.IO) {
        tracks.delete(result.missing.map { it.id })
        result.orphans.forEach { it.delete() }
    }

    suspend fun resetStatistics() = withContext(Dispatchers.IO) { db.withTransaction { db.playEventDao().deleteAll() } }

    suspend fun setHidden(hidden: Boolean) = withContext(Dispatchers.IO) {
        if (hidden) paths.nomedia.createNewFile() else paths.nomedia.delete()
        val audio = File(paths.root, "audio").listFiles()?.map { it.absolutePath }?.toTypedArray().orEmpty()
        if (audio.isNotEmpty()) MediaScannerConnection.scanFile(context, audio, null, null)
    }

    // copies a backup over the live database and restarts the app so Room opens the restored file
    suspend fun restore(source: File) {
        val temp = File(context.cacheDir, "restore.db")
        withContext(Dispatchers.IO) { source.copyTo(temp, overwrite = true) }
        db.close()
        withContext(Dispatchers.IO) { SnapshotManager.restoreInto(context, temp) }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    suspend fun exportLogs(): File = withContext(Dispatchers.IO) {
        paths.exportsDir.mkdirs()
        val name = "lumen-log-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".txt"
        val file = File(paths.exportsDir, name)
        val process = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=${android.os.Process.myPid()}").redirectErrorStream(true).start()
        file.outputStream().use { out -> process.inputStream.copyTo(out) }
        file
    }
}
