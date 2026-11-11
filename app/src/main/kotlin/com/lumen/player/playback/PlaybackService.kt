package com.lumen.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var holder: PlayerHolder
    @Inject lateinit var queue: QueueManager
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var extras: PlaybackExtras
    @Inject lateinit var tracker: ListeningTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var saver: Job? = null
    @Volatile private var normalize = true
    @Volatile private var mono = false
    private var enhancer: LoudnessEnhancer? = null

    override fun onCreate() {
        super.onCreate()
        // a custom audio sink so the mono processor sits in the audio path
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(MonoAudioProcessor { mono }))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.shuffleModeEnabled = false
        // after the last song the queue starts again from the first one
        player.repeatMode = Player.REPEAT_MODE_ALL

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

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attachEnhancer(audioSessionId)
                applyGain(player)
            }

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

        attachEnhancer(player.audioSessionId)
        scope.launch {
            settings.settings.collect {
                normalize = it.normalize
                mono = !it.stereo
                applyGain(player)
            }
        }
        extras.attach(player, scope)
        extras.onRestoreVolume = { applyGain(player) }
        tracker.attach(player)

        // restore the last queue, paused
        scope.launch {
            if (settings.settings.first().resumeOnLaunch && player.mediaItemCount == 0) {
                queue.restoreItems()?.let { (items, saved) ->
                    player.setMediaItems(items, saved.index, saved.positionMs)
                    player.repeatMode = queue.restoredRepeatMode(saved.repeatMode)
                    player.prepare()
                }
            }
            holder.player.value = player
        }
    }

    // loud songs: lower the player volume. quiet songs: boost with LoudnessEnhancer,
    // which also limits the peaks so the boost does not clip
    private fun applyGain(player: Player) {
        if (extras.fading) return
        val stored = player.currentMediaItem?.mediaMetadata?.extras?.getFloat(EXTRA_GAIN) ?: 0f
        val gain = if (normalize) stored else 0f
        player.volume = 10f.pow(gain.coerceAtMost(0f) / 20f)
        enhancer?.let { effect ->
            runCatching {
                effect.setTargetGain((gain.coerceAtLeast(0f) * 100).toInt())
                effect.enabled = gain > 0f
            }
        }
    }

    private fun attachEnhancer(sessionId: Int) {
        enhancer?.release()
        enhancer = if (sessionId == C.AUDIO_SESSION_ID_UNSET) null else runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
    }

    private fun startSaver(player: Player) {
        saver?.cancel()
        saver = scope.launch {
            while (isActive) {
                queue.save(player)
                delay(15_000)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // swiping the app away from recents stops the music and the service
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        session?.let { s ->
            runCatching { runBlocking { queue.save(s.player) } }
            tracker.release()
            enhancer?.release()
            enhancer = null
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
