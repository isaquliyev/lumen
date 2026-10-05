package com.lumen.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.data.Settings
import com.lumen.player.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    val paths: LumenPaths,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun setReduceMotion(value: Boolean) = viewModelScope.launch { repository.setReduceMotion(value) }
}
