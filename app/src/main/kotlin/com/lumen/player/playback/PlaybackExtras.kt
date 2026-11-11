package com.lumen.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepState {
    data object Off : SleepState
    data class At(val endsAt: Long) : SleepState
    data object EndOfSong : SleepState
}

// things that run inside the playback service next to the player: the sleep timer (and the listening tracker)
@Singleton
class PlaybackExtras @Inject constructor() {
    @Volatile var fading = false
    private var player: ExoPlayer? = null
    private var scope: CoroutineScope? = null
    private var timer: Job? = null
    private val _sleep = MutableStateFlow<SleepState>(SleepState.Off)
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()
    var onRestoreVolume: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // REPEAT: the same song starting again (one-song queue or "repeat one")
            val songEnded = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (_sleep.value == SleepState.EndOfSong && songEnded) {
                player?.pause()
                player?.seekTo(player?.currentMediaItemIndex ?: 0, 0)
                cancelSleep()
            }
        }
    }

    fun attach(player: ExoPlayer, scope: CoroutineScope) {
        this.player = player
        this.scope = scope
        player.addListener(listener)
    }

    fun detach() {
        player?.removeListener(listener)
        cancelSleep()
        player = null
        scope = null
    }

    // fades out over the last 10 s, pauses, then puts the volume back
    fun sleepIn(minutes: Int) {
        val scope = scope ?: return
        timer?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleep.value = SleepState.At(endsAt)
        timer = scope.launch {
            delay((endsAt - System.currentTimeMillis() - 10_000).coerceAtLeast(0))
            val p = player ?: return@launch
            fading = true
            val start = p.volume
            for (step in 1..20) {
                delay(500)
                p.volume = start * (1f - step / 20f)
            }
            p.pause()
            fading = false
            onRestoreVolume?.invoke()
            _sleep.value = SleepState.Off
        }
    }

    fun sleepAtEndOfSong() {
        timer?.cancel()
        _sleep.value = SleepState.EndOfSong
    }

    fun cancelSleep() {
        timer?.cancel()
        if (fading) {
            fading = false
            onRestoreVolume?.invoke()
        }
        _sleep.value = SleepState.Off
    }
}
