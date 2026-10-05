package com.lumen.player.feature.playlist

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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.GlowBackground
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.util.formatCount
import com.lumen.player.core.util.formatLength
import com.lumen.player.data.SmartKind
import com.lumen.player.data.SmartPlaylists
import com.lumen.player.feature.library.LocalTrackMenu
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.SmartPlaylistRoute
import com.lumen.player.feature.shell.bottomPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(handle: SavedStateHandle, smart: SmartPlaylists) : ViewModel() {
    val kind = SmartKind.valueOf(handle.toRoute<SmartPlaylistRoute>().kind)
    val songs: StateFlow<List<TrackRow>?> = smart.observe(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SmartPlaylistScreen(viewModel: SmartPlaylistViewModel = hiltViewModel()) {
    val list by viewModel.songs.collectAsStateWithLifecycle()
    val songs = list ?: return
    val kind = viewModel.kind
    val nav = LocalNav.current
    val player = LocalPlayer.current
    val menu = LocalTrackMenu.current
    val context = if (kind == SmartKind.LIKED) PlayContext.LIKED else PlayContext.PLAYLIST
    val accent = LocalAccent.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            GlowBackground(accent.glow, Modifier.fillMaxWidth(), centerY = 0.4f, radiusFactor = 0.8f) {
                Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth()) {
                        IconButton(onClick = { nav.back() }, modifier = Modifier.padding(8.dp).testTag("smart_back")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LumenColors.paper)
                        }
                    }
                    SmartCover(kind, Modifier.size(200.dp), radius = Radii.card)
                    Spacer(Modifier.height(20.dp))
                    Text(kind.title, style = LumenType.display, color = LumenColors.paper)
                    Text(kind.subtitle, style = LumenType.body, color = LumenColors.paper2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.side))
                    Text("${formatCount(songs.size, "song")} · ${formatLength(songs.sumOf { it.durationMs })}", style = LumenType.label, color = LumenColors.paper2)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Play", { player.play(songs, 0, context, kind.name, kind.title) }, icon = Icons.Rounded.PlayArrow, enabled = songs.isNotEmpty())
                        PillButton("Shuffle", { player.play(songs, -1, context, kind.name, kind.title, shuffle = true) },
                            style = PillStyle.OUTLINED, icon = Icons.Rounded.Shuffle, enabled = songs.isNotEmpty())
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        if (songs.isEmpty()) item { EmptyState("Nothing here yet.", kind.subtitle) }
        itemsIndexed(songs, key = { _, it -> it.id }) { index, track ->
            TrackRowItem(track, onClick = { player.play(songs, index, context, kind.name, kind.title) }, onMore = { menu.open(track) })
        }
    }
}
