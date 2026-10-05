package com.lumen.player.core.storage

import android.content.Context
import android.os.Environment
import com.lumen.player.data.SettingsRepository
import com.lumen.player.data.StorageMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LumenPaths @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
) {
    companion object {
        const val ROOT_NAME = "Lumen"
        val FOLDERS = listOf("audio", "covers", "artists", "lyrics", "playlists", "data", "exports")
    }

    @Volatile
    var mode: StorageMode = runBlocking { settings.current().storageMode } ?: StorageMode.SHARED
        private set

    val root: File
        get() = when (mode) {
            StorageMode.SHARED -> File(Environment.getExternalStorageDirectory(), ROOT_NAME)
            StorageMode.PRIVATE -> File(context.getExternalFilesDir(null), ROOT_NAME)
        }

    fun useMode(mode: StorageMode) {
        this.mode = mode
    }

    fun resolve(relative: String) = File(root, relative)

    fun audioRelative(trackId: String, ext: String) = "audio/$trackId.$ext"
    fun cover(trackId: String) = File(root, "covers/$trackId.jpg")
    fun coverThumb(trackId: String) = File(root, "covers/${trackId}_s.jpg")
    fun artistImage(artistId: String) = File(root, "artists/$artistId.jpg")
    fun artistThumb(artistId: String) = File(root, "artists/${artistId}_s.jpg")
    fun syncedLyrics(trackId: String) = File(root, "lyrics/$trackId.lrc")
    fun plainLyrics(trackId: String) = File(root, "lyrics/$trackId.txt")
    fun playlistCover(playlistId: String) = File(root, "playlists/$playlistId.jpg")
    val dataDir get() = File(root, "data")
    val snapshot get() = File(root, "data/lumen.db")
    val previousSnapshot get() = File(root, "data/lumen-prev.db")
    val exportsDir get() = File(root, "exports")
    val nomedia get() = File(root, ".nomedia")
    fun workDir(jobId: String) = File(context.cacheDir, "work/$jobId")
}
