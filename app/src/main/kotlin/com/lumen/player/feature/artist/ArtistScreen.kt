package com.lumen.player.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.ArtistEntity
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.StatsDao
import com.lumen.player.core.db.TrackArtistDao
import com.lumen.player.core.db.TrackDao
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.HideToggle
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LocalPaths
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.SectionHeader
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.design.accentFor
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.formatCount
import com.lumen.player.core.util.formatLength
import com.lumen.player.core.util.seededTone
import com.lumen.player.feature.library.AlbumTile
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ArtistState(
    val artist: ArtistEntity? = null,
    val songs: List<TrackRow> = emptyList(),
    val topSongs: List<TrackRow> = emptyList(),
    val appearsOn: List<TrackRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val listenedMs: Long = 0,
    val firstListen: Long? = null,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val artistDao: ArtistDao,
    trackDao: TrackDao,
    trackArtistDao: TrackArtistDao,
    albumDao: AlbumDao,
    stats: StatsDao,
    private val events: AppEvents,
) : ViewModel() {
    val id = handle.toRoute<ArtistRoute>().id

    private val songsAndRoles = combine(trackDao.observeByArtist(id), trackArtistDao.observeAll()) { songs, links ->
        val featured = links.filter { it.artistId == id && it.position > 0 }.map { it.trackId }.toSet()
        songs to featured
    }

    val state: StateFlow<ArtistState> = combine(
        artistDao.observe(id),
        songsAndRoles,
        albumDao.observeAll(true, emptyList()).map { list -> list.filter { it.artistId == id } },
        stats.lifetimePlays().map { list -> list.associateBy { it.trackId } },
        combine(stats.lifetimeArtists(), stats.firstListenOfArtist(id)) { a, f -> a.firstOrNull { it.artistId == id } to f },
    ) { artist, (songs, featured), albums, plays, (listening, first) ->
        val sorted = songs.sortedWith(compareBy({ it.albumTitle ?: "" }, { it.trackPosition ?: 0 }, { it.sortTitle }))
        ArtistState(
            artist = artist,
            songs = sorted.filter { it.id !in featured }.sortedBy { it.sortTitle },
            topSongs = songs.filter { (plays[it.id]?.plays ?: 0) > 0 }.sortedByDescending { plays[it.id]?.listenedMs ?: 0 }.take(5),
            appearsOn = sorted.filter { it.id in featured },
            albums = albums,
            listenedMs = listening?.listenedMs ?: 0,
            firstListen = first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistState())

    fun setHideSongs(hide: Boolean) = viewModelScope.launch {
        artistDao.setHideSongs(id, hide)
        events.show(if (hide) "Songs hidden from Library" else "Songs shown in Library")
    }
}

@Composable
fun ArtistScreen(viewModel: ArtistViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val artist = state.artist ?: return
    val player = LocalPlayer.current
    val shuffleOn by player.shuffleMode.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val menu = LocalTrackMenu.current
    val paths = LocalPaths.current
    val tone = remember(artist) { if (artist.toneHue != null) CoverTone(artist.toneHue, artist.toneSat ?: 0.5f) else seededTone(artist.name) }
    val accent = remember(tone) { accentFor(tone) }
    val playable = state.songs + state.appearsOn

    CompositionLocalProvider(LocalAccent provides accent) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
            item {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    CoverImage(
                        if (artist.hasImage) paths.artistImage(artist.id) else null,
                        artist.name,
                        Modifier.fillMaxSize(),
                        radius = 0.dp,
                        letterSize = LumenType.displayHero.fontSize,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to LumenColors.ink.copy(alpha = 0.55f), 0.18f to Color.Transparent, 0.4f to Color.Transparent, 1f to LumenColors.ink)
                        )
                    )
                    IconButton(onClick = { nav.back() }, modifier = Modifier.statusBarsPadding().padding(8.dp).testTag("artist_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LumenColors.paper)
                    }
                    Text(
                        artist.name,
                        style = LumenType.displayLarge,
                        color = LumenColors.paper,
                        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = Space.side),
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = Space.side, vertical = 12.dp)) {
                    val since = state.firstListen?.let {
                        " · since " + DateTimeFormatter.ofPattern("MMM yyyy").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                    } ?: ""
                    Text(
                        "${formatCount(playable.size, "song")} · ${formatLength(playable.sumOf { it.durationMs })}",
                        style = LumenType.label,
                        color = LumenColors.paper2,
                    )
                    if (state.listenedMs > 0) {
                        Text("${formatLength(state.listenedMs)} listened$since", style = LumenType.label, color = LumenColors.paper2)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("Play", { player.play(playable, -1, PlayContext.ARTIST, artist.id, artist.name, shuffle = shuffleOn) },
                            icon = Icons.Rounded.PlayArrow, modifier = Modifier.testTag("artist_play"))
                        PlayModeButton(PlayContext.ARTIST, artist.id)
                        HideToggle(artist.hideSongs, { viewModel.setHideSongs(!artist.hideSongs) })
                    }
                }
            }
            if (state.topSongs.isNotEmpty()) {
                item { SectionHeader("Your top songs", Modifier.padding(top = 8.dp)) }
                itemsIndexed(state.topSongs, key = { _, it -> "top_${it.id}" }) { index, track ->
                    TrackRowItem(track, onClick = { player.play(state.topSongs, index, PlayContext.ARTIST, artist.id, artist.name, shuffle = shuffleOn) }, onMore = { menu.open(track) })
                }
            }
            if (state.songs.isNotEmpty()) {
                item { SectionHeader("All songs", Modifier.padding(top = 8.dp)) }
                itemsIndexed(state.songs, key = { _, it -> "all_${it.id}" }) { index, track ->
                    TrackRowItem(track, onClick = { player.play(state.songs, index, PlayContext.ARTIST, artist.id, artist.name, shuffle = shuffleOn) }, onMore = { menu.open(track) })
                }
            }
            if (state.albums.isNotEmpty()) {
                item { SectionHeader("Albums", Modifier.padding(top = 8.dp)) }
                item {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(state.albums, key = { it.id }) { album -> AlbumTile(album, Modifier.width(140.dp)) { nav.open(AlbumRoute(album.id)) } }
                    }
                }
            }
            if (state.appearsOn.isNotEmpty()) {
                item { SectionHeader("Appears on", Modifier.padding(top = 8.dp)) }
                itemsIndexed(state.appearsOn, key = { _, it -> "feat_${it.id}" }) { index, track ->
                    TrackRowItem(track, onClick = { player.play(state.appearsOn, index, PlayContext.ARTIST, artist.id, artist.name, shuffle = shuffleOn) }, onMore = { menu.open(track) })
                }
            }
        }
    }
}
