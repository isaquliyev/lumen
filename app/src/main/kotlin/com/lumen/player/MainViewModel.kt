package com.lumen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.AppEvents
import com.lumen.player.core.TabRequest
import com.lumen.player.core.storage.SnapshotManager
import com.lumen.player.data.Settings
import com.lumen.player.data.SettingsRepository
import com.lumen.player.download.DownloadQueue
import com.lumen.player.download.LinkHandler
import com.lumen.player.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val snapshots: dagger.Lazy<SnapshotManager>,
    private val queue: dagger.Lazy<DownloadQueue>,
    private val links: dagger.Lazy<LinkHandler>,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val events: AppEvents,
    private val player: dagger.Lazy<PlayerConnection>,
) : ViewModel() {

    fun openPlayer() = events.openPlayer()
    val settings: StateFlow<Settings?> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // nothing may touch the database before onboarding is finished (restore needs it closed)
        viewModelScope.launch {
            repository.settings.first { it.onboardingDone }
            snapshots.get().start()
            player.get().connect()
            // the monthly recap was removed: stop its old daily job and notification channel
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("recap-check")
            context.getSystemService(android.app.NotificationManager::class.java).deleteNotificationChannel("recaps")
            queue.get().resumeAfterStart()
        }
    }

    override fun onCleared() {
        player.get().release()
    }

    fun handleShared(text: String) {
        viewModelScope.launch {
            repository.settings.first { it.onboardingDone }
            val message = links.get().handle(text)
            if (message.startsWith("Added")) events.open(TabRequest.ADD)
            events.show(message)
        }
    }
}
