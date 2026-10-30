package com.lumen.player.feature.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.AlbumDao
import com.lumen.player.core.db.AlbumRow
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackDao
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.GlowBackground
import com.lumen.player.core.design.HideToggle
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.design.accentFor
import com.lumen.player.core.design.trackCover
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.formatCount
import com.lumen.player.core.util.formatLength
import com.lumen.player.core.util.seededTone
import com.lumen.player.feature.library.LocalTrackMenu
import com.lumen.player.feature.player.PlayModeButton
import com.lumen.player.feature.shell.AlbumRoute
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.bottomPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumState(val album: AlbumRow? = null, val songs: List<TrackRow> = emptyList())

@HiltViewModel
class AlbumViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val albumDao: AlbumDao,
    trackDao: TrackDao,
    private val events: AppEvents,
) : ViewModel() {
    val id = handle.toRoute<AlbumRoute>().id
    val state: StateFlow<AlbumState> = combine(albumDao.observe(id), trackDao.observeByAlbum(id)) { album, songs -> AlbumState(album, songs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumState())

    fun setHideSongs(hide: Boolean) = viewModelScope.launch {
        albumDao.setHideSongs(id, hide)
        events.show(if (hide) "Songs hidden from Library" else "Songs shown in Library")
    }
}

@Composable
fun AlbumScreen(viewModel: AlbumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val album = state.album ?: return
    val songs = state.songs
    val player = LocalPlayer.current
    val shuffleOn by player.shuffleMode.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val menu = LocalTrackMenu.current
    val coverTrack = songs.firstOrNull { it.id == album.coverTrackId } ?: songs.firstOrNull()
    val tone = remember(coverTrack) { coverTrack?.toneHue?.let { CoverTone(it, coverTrack.toneSat ?: 0.5f) } ?: seededTone(album.title) }
    val accent = remember(tone) { accentFor(tone) }

    CompositionLocalProvider(LocalAccent provides accent) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
            item {
                GlowBackground(accent.glow, Modifier.fillMaxWidth(), centerY = 0.45f, radiusFactor = 0.75f) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth()) {
                            IconButton(onClick = { nav.back() }, modifier = Modifier.padding(8.dp).testTag("album_back")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LumenColors.paper)
                            }
                        }
                        CoverImage(album.coverTrackId?.let { trackCover(it, true) }, album.title, Modifier.size(240.dp), radius = Radii.card, letterSize = LumenType.displayHero.fontSize)
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.side), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(album.title, style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center)
                    album.artistName?.let { name ->
                        Text(name, style = LumenType.body, color = accent.accent, modifier = Modifier.clickable { nav.open(ArtistRoute(album.artistId)) }.padding(4.dp))
                    }
                    Text(
                        listOfNotNull(album.releaseDate?.take(4), album.genre, formatCount(album.trackCount, "song"), formatLength(album.durationMs)).joinToString(" · "),
                        style = LumenType.label,
                        color = LumenColors.paper2,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("Play", { player.play(songs, -1, PlayContext.ALBUM, album.id, album.title, shuffle = shuffleOn) }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.testTag("album_play"))
                        PlayModeButton(PlayContext.ALBUM, album.id)
                        HideToggle(album.hideSongs, { viewModel.setHideSongs(!album.hideSongs) })
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(songs, key = { _, it -> it.id }) { index, track ->
                TrackRowItem(track, onClick = { player.play(songs, index, PlayContext.ALBUM, album.id, album.title, shuffle = shuffleOn) }, onMore = { menu.open(track) })
            }
        }
    }
}
