package com.lumen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.storage.SnapshotManager
import com.lumen.player.data.Settings
import com.lumen.player.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val snapshots: dagger.Lazy<SnapshotManager>,
) : ViewModel() {
    val settings: StateFlow<Settings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // nothing may touch the database before onboarding is finished (restore needs it closed)
        viewModelScope.launch {
            settings.settings.first { it.onboardingDone }
            snapshots.get().start()
        }
    }
}
