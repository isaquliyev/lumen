package com.lumen.player.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.lumen.player.core.ApplicationScope
import com.lumen.player.core.db.DATABASE_NAME
import com.lumen.player.core.db.LumenDatabase
import com.lumen.player.core.util.formatCount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SnapshotSummary(val songs: Int, val playlists: Int) {
    override fun toString() = "${formatCount(songs, "song")}, ${formatCount(playlists, "playlist")}"
}

@OptIn(FlowPreview::class)
@Singleton
class SnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LumenDatabase,
    private val paths: LumenPaths,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    @Volatile private var dirty = false
    private var started = false
    private val _lastBackup = MutableStateFlow<Long?>(null)
    val lastBackup: StateFlow<Long?> = _lastBackup

    // starts the automatic backups; called once onboarding is finished
    fun start() {
        if (started) return
        started = true
        _lastBackup.value = paths.snapshot.takeIf { it.exists() }?.lastModified()
        val tables = arrayOf("tracks", "artists", "track_artists", "albums", "playlists", "playlist_tracks", "play_events")
        scope.launch {
            db.invalidationTracker.createFlow(*tables, emitInitialState = false)
                .onEach { dirty = true }
                .debounce(30_000)
                .collect { snapshot() }
        }
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && dirty) scope.launch { snapshot() }
            })
        }
    }

    suspend fun snapshot(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val temp = File(context.cacheDir, "lumen-snap.db").apply { delete() }
                db.openHelper.writableDatabase.execSQL("VACUUM INTO '${temp.absolutePath.replace("'", "''")}'")
                paths.dataDir.mkdirs()
                if (paths.snapshot.exists()) moveInto(paths.snapshot, paths.previousSnapshot)
                moveInto(temp, paths.snapshot)
                dirty = false
                _lastBackup.value = System.currentTimeMillis()
                Log.i("Lumen", "snapshot written: ${paths.snapshot.length()} bytes")
            }.onFailure { Log.e("Lumen", "snapshot failed", it) }.isSuccess
        }
    }

    companion object {
        fun summary(file: File): SnapshotSummary? = runCatching {
            if (!file.exists()) return null
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val songs = db.rawQuery("SELECT COUNT(*) FROM tracks WHERE deleted_at IS NULL", null).use { it.moveToFirst(); it.getInt(0) }
                val lists = db.rawQuery("SELECT COUNT(*) FROM playlists", null).use { it.moveToFirst(); it.getInt(0) }
                SnapshotSummary(songs, lists)
            }
        }.getOrNull()

        // copies a snapshot over the private database; Room must not be open yet
        fun restoreInto(context: Context, snapshot: File) {
            val target = context.getDatabasePath(DATABASE_NAME)
            target.parentFile?.mkdirs()
            listOf(target, File(target.path + "-wal"), File(target.path + "-shm"), File(target.path + "-journal")).forEach { it.delete() }
            snapshot.copyTo(target, overwrite = true)
        }

        fun privateDatabaseIsEmpty(context: Context): Boolean {
            val file = context.getDatabasePath(DATABASE_NAME)
            return summary(file)?.let { it.songs == 0 && it.playlists == 0 } ?: true
        }
    }
}
