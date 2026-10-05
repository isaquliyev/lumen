package com.lumen.player.core.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val README = """This is the Lumen music library.

audio/      your songs
covers/     album covers
artists/    artist photos
lyrics/     song lyrics (.lrc = synced, .txt = plain)
playlists/  custom playlist covers
data/       automatic backup of the Lumen database
exports/    recap images you shared

Please don't rename or move files here. Lumen finds them by their names.
Copy this whole folder to keep a backup of your library.
"""

@Singleton
class StorageSetup @Inject constructor(private val paths: LumenPaths) {

    fun createFolders(hideFromOtherApps: Boolean = true) {
        val root = paths.root
        LumenPaths.FOLDERS.forEach { File(root, it).mkdirs() }
        if (hideFromOtherApps && !paths.nomedia.exists()) paths.nomedia.createNewFile()
        val readme = File(root, "README.txt")
        if (!readme.exists()) readme.writeText(README)
    }

    fun freeBytes(): Long = paths.root.let { if (it.exists()) it.usableSpace else it.parentFile?.usableSpace ?: 0L }

    fun lowOnSpace() = freeBytes() < 1_000_000_000L
}

// moves a finished file into the library without ever leaving a half-written file there
fun moveInto(source: File, target: File) {
    target.parentFile?.mkdirs()
    if (target.exists()) target.delete()
    if (source.renameTo(target)) return
    val partial = File(target.parentFile, ".${target.name}.part")
    source.copyTo(partial, overwrite = true)
    if (!partial.renameTo(target)) {
        partial.delete()
        error("Could not move ${source.name} into the library")
    }
    source.delete()
}
