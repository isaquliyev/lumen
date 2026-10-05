package com.lumen.player.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.AlbumRow
import com.lumen.player.core.db.ArtistRow
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.ArtistAvatar
import com.lumen.player.core.design.AlphabetRail
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.ScreenTitle
import com.lumen.player.core.design.SegmentedTabs
import com.lumen.player.core.design.SortChip
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.design.artistThumb
import com.lumen.player.core.design.trackThumb
import com.lumen.player.core.util.formatCount
import com.lumen.player.core.util.formatLength
import com.lumen.player.feature.focus.FocusPill
import com.lumen.player.feature.focus.LocalFocusActions
import com.lumen.player.feature.playlist.PlaylistsTab
import com.lumen.player.feature.shell.AddRoute
import com.lumen.player.feature.shell.AlbumRoute
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.SearchRoute
import com.lumen.player.feature.shell.bottomPadding
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Library", Modifier.weight(1f))
                IconButton(onClick = { nav.open(SearchRoute) }, modifier = Modifier.padding(end = 8.dp).testTag("library_search")) {
                    Icon(Icons.Rounded.Search, "Search", tint = LumenColors.paper)
                }
            }
            FocusPill(Modifier.padding(start = Space.side, top = 4.dp))
            Spacer(Modifier.height(12.dp))
            SegmentedTabs(listOf("Songs", "Playlists", "Artists", "Albums"), tab, { tab = it; viewModel.clearSelection() })
            Spacer(Modifier.height(8.dp))
        }
        when (tab) {
            0 -> SongsTab(viewModel)
            1 -> PlaylistsTab()
            2 -> ArtistsTab(viewModel)
            else -> AlbumsTab(viewModel)
        }
    }
}

@Composable
private fun SongsTab(viewModel: LibraryViewModel) {
    val state by viewModel.songs.collectAsStateWithLifecycle()
    val sort by viewModel.songSort.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val player = LocalPlayer.current
    val menu = LocalTrackMenu.current
    val nav = LocalNav.current
    val addToPlaylist = LocalAddToPlaylist.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val songs = state.songs
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()

    if (state.loaded && songs.isEmpty()) {
        EmptyState("Your library is quiet.", "Find songs in the Add tab.", action = "Add music", onAction = { nav.open(AddRoute) })
        return
    }

    val railLetters = remember(songs, sort) {
        if (sort == SongSort.TITLE || sort == SongSort.ARTIST) {
            songs.map { railKey(if (sort == SongSort.TITLE) it.sortTitle else it.artistSort) }.distinct()
        } else emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
            item(key = "header") {
                if (selection.isEmpty()) {
                    Column(Modifier.padding(horizontal = Space.side, vertical = 8.dp)) {
                        Text(
                            "${formatCount(songs.size, "song")} · ${formatLength(state.totalMs)}",
                            style = LumenType.label,
                            color = LumenColors.paper2,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillButton("Shuffle", { player.play(songs, -1, PlayContext.ALL_SONGS, null, "All songs", shuffle = true) },
                                icon = Icons.Rounded.Shuffle, modifier = Modifier.testTag("songs_shuffle"))
                            Spacer(Modifier.width(8.dp))
                            PillButton("Play", { player.play(songs, 0, PlayContext.ALL_SONGS, null, "All songs") },
                                style = PillStyle.OUTLINED, icon = Icons.Rounded.PlayArrow, modifier = Modifier.testTag("songs_play"))
                            Spacer(Modifier.weight(1f))
                            SortChip(SongSort.entries, sort, { it.label }, { viewModel.songSort.value = it; scope.launch { listState.scrollToItem(0) } })
                        }
                    }
                } else {
                    SelectionBar(
                        count = selection.size,
                        onClose = viewModel::clearSelection,
                        onAddToPlaylist = { addToPlaylist(selection.toList()); viewModel.clearSelection() },
                        onPlayNext = {
                            player.playNext(songs.filter { it.id in selection })
                            viewModel.clearSelection()
                        },
                        onLike = {
                            songs.filter { it.id in selection }.forEach { player.setLiked(it.id, true) }
                            viewModel.clearSelection()
                        },
                        onDelete = { confirmDelete = true },
                    )
                }
            }
            itemsIndexed(songs, key = { _, it -> it.id }, contentType = { _, _ -> "song" }) { index, track ->
                val selecting = selection.isNotEmpty()
                TrackRowItem(
                    track,
                    onClick = {
                        if (selecting) viewModel.toggleSelected(track.id)
                        else player.play(songs, index, PlayContext.ALL_SONGS, null, "All songs")
                    },
                    onLongClick = { viewModel.toggleSelected(track.id) },
                    onMore = if (selecting) null else ({ menu.open(track) }),
                    playing = nowPlaying?.id == track.id,
                    selected = track.id in selection,
                    modifier = Modifier.padding(end = if (railLetters.isNotEmpty()) 20.dp else 0.dp),
                )
            }
        }
        if (railLetters.isNotEmpty() && selection.isEmpty()) {
            AlphabetRail(
                railLetters,
                onLetter = { letter ->
                    val index = songs.indexOfFirst { railKey(if (sort == SongSort.TITLE) it.sortTitle else it.artistSort) == letter }
                    if (index >= 0) scope.launch { listState.scrollToItem(index + 1) }
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = 80.dp, bottom = LocalBottomSpace.current + 16.dp),
            )
        }
    }

    if (confirmDelete) {
        val menuViewModel: TrackMenuViewModel = hiltViewModel()
        ConfirmDialog(
            title = "Delete ${formatCount(selection.size, "song")}?",
            body = "The files are removed from your phone. Your listening history stays.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            menuViewModel.delete(selection.toList())
            viewModel.clearSelection()
            confirmDelete = false
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("selection_close")) { Icon(Icons.Rounded.Close, "Done", tint = LumenColors.paper) }
        Text("$count selected", style = LumenType.title, color = LumenColors.paper, modifier = Modifier.weight(1f))
        IconButton(onClick = onAddToPlaylist, modifier = Modifier.testTag("selection_playlist")) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", tint = LumenColors.paper) }
        IconButton(onClick = onPlayNext, modifier = Modifier.testTag("selection_play_next")) { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", tint = LumenColors.paper) }
        IconButton(onClick = onLike, modifier = Modifier.testTag("selection_like")) { Icon(Icons.Rounded.Favorite, "Like", tint = LumenColors.paper) }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("selection_delete")) { Icon(Icons.Rounded.Delete, "Delete", tint = LumenColors.danger) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistsTab(viewModel: LibraryViewModel) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val sort by viewModel.artistSort.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val focusActions = LocalFocusActions.current
    var menuFor by remember { mutableStateOf<ArtistRow?>(null) }
    val accent = LocalAccent.current.accent

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = Space.side, end = Space.side, bottom = LocalBottomSpace.current + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(3) }) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatCount(artists.size, "artist"), style = LumenType.label, color = LumenColors.paper2, modifier = Modifier.weight(1f))
                SortChip(ArtistSort.entries, sort, { it.label }, { viewModel.artistSort.value = it })
            }
        }
        items(artists, key = { it.id }) { artist ->
            val focused = artist.id in focus.ids
            Box {
                Column(
                    Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { nav.open(ArtistRoute(artist.id)) }, onLongClick = { menuFor = artist })
                        .testTag("artist_${artist.id}")
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        ArtistAvatar(artistThumb(artist.id, artist.hasImage), artist.name, Modifier.fillMaxWidth().aspectRatio(1f))
                        if (focused) Box(Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(accent))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        artist.name,
                        style = LumenType.label,
                        color = if (focused) accent else LumenColors.paper,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                DropdownMenu(expanded = menuFor?.id == artist.id, onDismissRequest = { menuFor = null }, containerColor = LumenColors.ink2) {
                    DropdownMenuItem(
                        text = { Text("Focus on this artist", color = LumenColors.paper) },
                        onClick = { focusActions.only(artist.id); menuFor = null },
                        modifier = Modifier.testTag("artist_focus_only"),
                    )
                    DropdownMenuItem(
                        text = { Text(if (focused) "Remove from focus" else "Add to focus", color = LumenColors.paper) },
                        onClick = { focusActions.toggle(artist.id); menuFor = null },
                        modifier = Modifier.testTag("artist_focus_toggle"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(viewModel: LibraryViewModel) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = Space.side, end = Space.side, top = 8.dp, bottom = LocalBottomSpace.current + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album -> AlbumTile(album) { nav.open(AlbumRoute(album.id)) } }
    }
}

@Composable
fun AlbumTile(album: AlbumRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.card)).clickable(onClick = onClick).testTag("album_${album.id}")) {
        CoverImage(album.coverTrackId?.let { trackThumb(it, true) }, album.title, Modifier.fillMaxWidth().aspectRatio(1f), radius = Radii.card, letterSize = LumenType.display.fontSize)
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(album.artistName, album.releaseDate?.take(4)).joinToString(" · "),
            style = LumenType.label,
            color = LumenColors.paper2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

