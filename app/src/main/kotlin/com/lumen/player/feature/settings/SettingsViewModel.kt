package com.lumen.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.AppEvents
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.storage.SnapshotManager
import com.lumen.player.core.util.formatCount
import com.lumen.player.data.CheckResult
import com.lumen.player.data.DebugSeeder
import com.lumen.player.data.FolderUsage
import com.lumen.player.data.LibraryMaintenance
import com.lumen.player.data.Settings
import com.lumen.player.data.SettingsRepository
import com.lumen.player.download.sources.YtDlpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val snapshots: SnapshotManager,
    private val seeder: DebugSeeder,
    private val maintenance: LibraryMaintenance,
    private val ytdlp: YtDlpManager,
    private val events: AppEvents,
    val paths: LumenPaths,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val lastBackup = snapshots.lastBackup
    val ytdlpVersion = ytdlp.version
    val usage = MutableStateFlow<List<FolderUsage>>(emptyList())
    val free = MutableStateFlow(0L)
    val check = MutableStateFlow<CheckResult?>(null)
    val busy = MutableStateFlow<String?>(null)

    init {
        refreshUsage()
    }

    fun refreshUsage() = viewModelScope.launch {
        usage.value = maintenance.usage()
        free.value = maintenance.freeBytes()
    }

    private fun run(start: String, block: suspend () -> String) = viewModelScope.launch {
        busy.value = start
        val message = runCatching { block() }.getOrElse { "Failed: ${it.message}" }
        busy.value = null
        events.show(message)
    }

    fun setReduceMotion(value: Boolean) = viewModelScope.launch { repository.setReduceMotion(value) }
    fun setNormalize(value: Boolean) = viewModelScope.launch { repository.setNormalize(value) }
    fun setResumeOnLaunch(value: Boolean) = viewModelScope.launch { repository.setResumeOnLaunch(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { repository.setWifiOnly(value) }
    fun setParallel(value: Int) = viewModelScope.launch { repository.setParallelDownloads(value) }
    fun setPreferM4a(value: Boolean) = viewModelScope.launch { repository.setPreferM4a(value) }

    fun setHidden(value: Boolean) = run(if (value) "Hiding songs…" else "Showing songs…") {
        repository.setHideFromOtherApps(value)
        maintenance.setHidden(value)
        if (value) "Songs hidden from other apps" else "Songs visible to other apps"
    }

    fun backUpNow() = run("Backing up…") { if (snapshots.snapshot()) "Backup saved" else "Backup failed" }

    fun backups(): List<File> = listOf(paths.snapshot, paths.previousSnapshot).filter { it.exists() }

    fun restore(file: File) = run("Restoring…") {
        maintenance.restore(file)
        "Restored"
    }

    fun checkLibrary() = run("Checking library…") {
        val result = maintenance.check()
        check.value = result
        if (result.clean) "Everything is in order" else "Found ${formatCount(result.missing.size, "missing song")} and ${formatCount(result.orphans.size, "orphan file")}"
    }

    fun fixLibrary() = run("Fixing…") {
        check.value?.let { maintenance.fix(it) }
        check.value = null
        refreshUsage()
        "Library fixed"
    }

    fun updateYtDlp() = run("Updating downloader…") { ytdlp.update() }
    fun resetStats() = run("Resetting…") { maintenance.resetStatistics(); "Statistics reset" }
    fun exportLogs() = run("Exporting…") { "Saved ${maintenance.exportLogs().name} to Lumen/exports" }
    fun seedSongs() = run("Seeding songs…") { "Seeded ${seeder.seedSongs()} songs" }
    fun seedPlays() = run("Seeding plays…") { "Seeded ${seeder.seedPlays()} plays" }
    fun removeSeeded() = run("Removing seeded data…") { seeder.removeSeeded(); "Seeded data removed" }
}
