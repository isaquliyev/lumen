package com.lumen.player.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

// things that run inside the playback service next to the player
@Singleton
class PlaybackExtras @Inject constructor() {
    @Volatile var fading = false

    fun attach(player: ExoPlayer, scope: CoroutineScope) = Unit

    fun detach() = Unit
}
