package com.lumen.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.lumen.player.MainActivity
import com.lumen.player.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.pow

const val EXTRA_OPEN_PLAYER = "open_player"

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var holder: PlayerHolder
    @Inject lateinit var queue: QueueManager
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var extras: PlaybackExtras

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var saver: Job? = null
    @Volatile private var normalize = true

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.shuffleModeEnabled = false

        val openPlayer = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayer)
            .setCallback(SessionCallback())
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = applyGain(player)

            // skips and queue edits while paused are saved too
            override fun onEvents(p: Player, events: Player.Events) {
                if (!p.isPlaying && holder.player.value != null &&
                    events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)
                ) scope.launch { queue.save(player) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startSaver(player) else {
                    saver?.cancel()
                    scope.launch { queue.save(player) }
                }
            }
        })

        scope.launch { settings.settings.collect { normalize = it.normalize; applyGain(player) } }
        extras.attach(player, scope)

        // restore the last queue, paused
        scope.launch {
            if (settings.settings.first().resumeOnLaunch && player.mediaItemCount == 0) {
                queue.restoreItems()?.let { (items, saved) ->
                    player.setMediaItems(items, saved.index, saved.positionMs)
                    player.repeatMode = saved.repeatMode
                    player.prepare()
                }
            }
            holder.player.value = player
        }
    }

    private fun applyGain(player: Player) {
        if (extras.fading) return
        val gain = player.currentMediaItem?.mediaMetadata?.extras?.getFloat(EXTRA_GAIN) ?: 0f
        player.volume = if (normalize) 10f.pow(gain.coerceAtMost(0f) / 20f) else 1f
    }

    private fun startSaver(player: Player) {
        saver?.cancel()
        saver = scope.launch {
            while (isActive) {
                queue.save(player)
                delay(5_000)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.let { s ->
            runCatching { runBlocking { queue.save(s.player) } }
            extras.detach()
            holder.player.value = null
            s.player.release()
            s.release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        // "play" from Bluetooth or the system media controls after the app was killed
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val restored = queue.restoreItems()
            Log.i("Lumen", "playback resumption: ${restored?.first?.size ?: 0} items")
            if (restored == null) MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            else MediaSession.MediaItemsWithStartPosition(restored.first, restored.second.index, restored.second.positionMs)
        }
    }
}
