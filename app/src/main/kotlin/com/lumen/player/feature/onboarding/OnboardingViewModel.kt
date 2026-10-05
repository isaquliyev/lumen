package com.lumen.player.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.storage.StorageSetup
import com.lumen.player.data.SettingsRepository
import com.lumen.player.data.StorageMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class OnboardingStep { WELCOME, STORAGE, RESTORE, NOTIFICATIONS, BACKGROUND, DONE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val storageMode: StorageMode? = null,
    val restoreSummary: String? = null,
    val restoring: Boolean = false,
    val restoreError: String? = null,
    val lowOnSpace: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val paths: LumenPaths,
    private val setup: StorageSetup,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun next() {
        val current = _state.value.step
        val next = OnboardingStep.entries[current.ordinal + 1]
        if (next == OnboardingStep.RESTORE && _state.value.restoreSummary == null) {
            _state.update { it.copy(step = OnboardingStep.NOTIFICATIONS) }
        } else {
            _state.update { it.copy(step = next) }
        }
        if (_state.value.step == OnboardingStep.DONE) prepareFolders()
    }

    fun chooseStorage(mode: StorageMode) {
        if (_state.value.step != OnboardingStep.STORAGE) return
        paths.useMode(mode)
        _state.update { it.copy(storageMode = mode) }
        viewModelScope.launch {
            settings.setStorageMode(mode)
            val summary = withContext(Dispatchers.IO) { RestoreCheck.summary(paths) }
            _state.update { it.copy(restoreSummary = summary) }
            next()
        }
    }

    private fun prepareFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            setup.createFolders()
            _state.update { it.copy(lowOnSpace = setup.lowOnSpace()) }
        }
    }

    fun finish() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }
}
