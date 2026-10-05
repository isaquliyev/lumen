package com.lumen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.data.Settings
import com.lumen.player.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
