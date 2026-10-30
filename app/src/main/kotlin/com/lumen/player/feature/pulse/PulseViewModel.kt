package com.lumen.player.feature.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.data.Focus
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.PulseData
import com.lumen.player.data.PulseRepository
import com.lumen.player.data.RangeKind
import com.lumen.player.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PulseQuery(val kind: RangeKind, val offset: Int, val scoped: Boolean, val focus: Focus)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PulseViewModel @Inject constructor(
    repository: PulseRepository,
    focusRepository: FocusRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val kindFlow = settings.settings.map { s -> s.pulseRange?.let { runCatching { RangeKind.valueOf(it) }.getOrNull() } ?: RangeKind.MONTH }
    val kind: StateFlow<RangeKind> = kindFlow.stateIn(viewModelScope, SharingStarted.Eagerly, RangeKind.MONTH)
    val offset = MutableStateFlow(0)
    val scoped = MutableStateFlow(false)
    val focus = focusRepository.focus

    private val query = combine(kindFlow, offset, scoped, focusRepository.focus) { k, o, s, f -> PulseQuery(k, o, s && f.active, f) }

    val data: StateFlow<PulseData?> = combine(query, repository.changes()) { q, _ -> q }
        .conflate()
        .mapLatest { q -> repository.build(q.kind, q.offset, q.focus, q.scoped) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setKind(value: RangeKind) {
        offset.value = 0
        viewModelScope.launch { settings.setPulseRange(value.name) }
    }

    fun older() {
        if (kind.value != RangeKind.ALL) offset.value += 1
    }

    fun newer() {
        if (offset.value > 0) offset.value -= 1
    }
}
