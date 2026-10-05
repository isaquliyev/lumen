package com.lumen.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.storage.SnapshotManager
import com.lumen.player.data.DebugSeeder
import com.lumen.player.data.Settings
import com.lumen.player.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val snapshots: SnapshotManager,
    private val seeder: DebugSeeder,
    val paths: LumenPaths,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val lastBackup = snapshots.lastBackup
    val message = MutableStateFlow<String?>(null)

    fun setReduceMotion(value: Boolean) = viewModelScope.launch { repository.setReduceMotion(value) }

    private fun run(start: String, block: suspend () -> String) = viewModelScope.launch {
        message.value = start
        message.value = runCatching { block() }.getOrElse { "Failed: ${it.message}" }
    }

    fun backUpNow() = run("Backing up…") { if (snapshots.snapshot()) "Backup saved" else "Backup failed" }
    fun seedSongs() = run("Seeding songs…") { "Seeded ${seeder.seedSongs()} songs" }
    fun seedPlays() = run("Seeding plays…") { "Seeded ${seeder.seedPlays()} plays" }
    fun removeSeeded() = run("Removing seeded data…") { seeder.removeSeeded(); "Seeded data removed" }
}
