package com.lumen.player.feature.shell

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object LibraryRoute
@Serializable object PulseRoute
@Serializable object AddRoute
@Serializable object SettingsRoute
@Serializable object SearchRoute
@Serializable data class ArtistRoute(val id: String)
@Serializable data class AlbumRoute(val id: String)
@Serializable data class PlaylistRoute(val id: String)
@Serializable data class SmartPlaylistRoute(val kind: String)
@Serializable data class AddSongsRoute(val playlistId: String)
@Serializable data class RecapRoute(val year: Int, val month: Int)
@Serializable object LyricsRoute

interface Navigator {
    fun open(route: Any)
    fun back()
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
