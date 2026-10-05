package com.lumen.player.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lumen.player.core.ApplicationScope
import com.lumen.player.core.db.EndReason
import com.lumen.player.core.db.PlayEventDao
import com.lumen.player.core.db.PlayEventEntity
import com.lumen.player.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// writes play events, the source of truth for every statistic; runs inside the playback service
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ListeningTracker @Inject constructor(
    private val events: PlayEventDao,
    private val context: PlaybackContextHolder,
    private val focus: FocusRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : Player.Listener {

    private class Open(val id: String, val trackId: String, val durationMs: Long, val startedAt: Long) {
        var listenedMs = 0L
        var playingSince: Long? = null
        fun total(now: Long) = listenedMs + (playingSince?.let { now - it } ?: 0L)
    }

    private val writer = Dispatchers.IO.limitedParallelism(1)
    private var player: Player? = null
    private var open: Open? = null
    private var sessionId = UUID.randomUUID().toString()
    private var lastEndedAt = 0L
    private var ticker: Job? = null

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
        player.currentMediaItem?.let { start(it) }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        close(if (ended) EndReason.COMPLETED else null)
        mediaItem?.let { start(it) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val o = open ?: return
        val now = SystemClock.elapsedRealtime()
        if (isPlaying && o.playingSince == null) o.playingSince = now
        if (!isPlaying && o.playingSince != null) {
            o.listenedMs += now - o.playingSince!!
            o.playingSince = null
            save(o, null)
        }
        if (isPlaying) startTicker() else ticker?.cancel()
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) close(EndReason.COMPLETED)
    }

    fun release() {
        close(EndReason.STOPPED)
        player?.removeListener(this)
        player = null
    }

    private fun start(item: MediaItem) {
        val now = System.currentTimeMillis()
        if (now - lastEndedAt > 15 * 60_000) sessionId = UUID.randomUUID().toString()
        open = Open(UUID.randomUUID().toString(), trackIdOf(item.mediaId), item.mediaMetadata.durationMs ?: 0, now)
            .also { if (player?.isPlaying == true) it.playingSince = SystemClock.elapsedRealtime() }
    }

    private fun close(reason: EndReason?) {
        val o = open ?: return
        open = null
        ticker?.cancel()
        val listened = o.total(SystemClock.elapsedRealtime())
        val short = listened < 30_000 && (o.durationMs == 0L || listened < o.durationMs / 2)
        val final = reason ?: if (short) EndReason.SKIPPED else EndReason.STOPPED
        lastEndedAt = System.currentTimeMillis()
        if (listened < 1_000) {
            scope.launch(writer) { events.delete(o.id) }
            return
        }
        save(o, final, listened)
    }

    private fun save(o: Open, reason: EndReason?, listened: Long = o.total(SystemClock.elapsedRealtime())) {
        val snapshot = context.current
        val focusActive = focus.isActive
        scope.launch(writer) {
            // only a real play (30 s or more) can be the first listen of a song
            val first = listened >= 30_000 && !events.hasQualifyingPlay(o.trackId, excludeId = o.id)
            events.upsert(
                PlayEventEntity(
                    o.id, o.trackId, o.startedAt, System.currentTimeMillis(), listened, o.durationMs,
                    reason, snapshot.type, snapshot.id, focusActive, sessionId, first,
                )
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        // checkpoint every 30 s so a killed app loses little; same thread as the player callbacks
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(30_000)
                open?.let { save(it, null) }
            }
        }
    }
}
