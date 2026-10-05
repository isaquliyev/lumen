package com.lumen.player.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.db.AlbumDao
import com.lumen.player.core.db.AlbumRow
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.ArtistRow
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.PlaylistRow
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.ArtistAvatar
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.SectionHeader
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.design.artistThumb
import com.lumen.player.core.design.trackThumb
import com.lumen.player.core.net.LumenJson
import com.lumen.player.core.util.normalize
import com.lumen.player.data.PlaylistRepository
import com.lumen.player.data.TrackRepository
import com.lumen.player.feature.library.LocalTrackMenu
import com.lumen.player.feature.shell.AlbumRoute
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.PlaylistRoute
import com.lumen.player.feature.shell.bottomPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject

data class SearchResult(
    val songs: List<TrackRow> = emptyList(),
    val artists: List<ArtistRow> = emptyList(),
    val albums: List<AlbumRow> = emptyList(),
    val playlists: List<PlaylistRow> = emptyList(),
) {
    val empty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

private class Indexed<T>(val item: T, val key: String)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    tracks: TrackRepository,
    artistDao: ArtistDao,
    albumDao: AlbumDao,
    playlists: PlaylistRepository,
    focusRepository: com.lumen.player.data.FocusRepository,
    private val store: DataStore<Preferences>,
) : ViewModel() {
    private val recentKey = stringPreferencesKey("recent_searches")
    val query = MutableStateFlow("")
    private val list = ListSerializer(String.serializer())

    val recent: StateFlow<List<String>> = store.data.map { prefs ->
        prefs[recentKey]?.let { runCatching { LumenJson.decodeFromString(list, it) }.getOrNull() }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // normalised text is computed once per library change, not per keystroke
    private val songIndex = tracks.songs.map { songs -> songs.map { Indexed(it, normalize("${it.title} ${it.artistLine} ${it.albumTitle}")) } }
    private val artistIndex = artistDao.observeAll().map { all -> all.map { Indexed(it, normalize(it.name)) } }
    private val albumIndex = focusRepository.focus.flatMapLatest { albumDao.observeAll(it.off, it.idList) }
        .map { all -> all.map { Indexed(it, normalize("${it.title} ${it.artistName}")) } }
    private val playlistIndex = playlists.all.map { all -> all.map { Indexed(it, normalize(it.name)) } }

    val results: StateFlow<SearchResult> = combine(query.debounce(150), songIndex, artistIndex, albumIndex, playlistIndex) { q, songs, artists, albums, lists ->
        val terms = normalize(q).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return@combine SearchResult()
        fun <T> List<Indexed<T>>.match(limit: Int) = filter { entry -> terms.all { entry.key.contains(it) } }.take(limit).map { it.item }
        SearchResult(songs.match(50), artists.match(10), albums.match(10), lists.match(10))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResult())

    fun remember(text: String) = viewModelScope.launch {
        val clean = text.trim()
        if (clean.isEmpty()) return@launch
        store.edit { prefs ->
            val old = prefs[recentKey]?.let { runCatching { LumenJson.decodeFromString(list, it) }.getOrNull() }.orEmpty()
            prefs[recentKey] = LumenJson.encodeToString(list, (listOf(clean) + old.filter { it != clean }).take(10))
        }
    }

    fun forget(text: String) = viewModelScope.launch {
        store.edit { prefs ->
            val old = prefs[recentKey]?.let { runCatching { LumenJson.decodeFromString(list, it) }.getOrNull() }.orEmpty()
            prefs[recentKey] = LumenJson.encodeToString(list, old.filter { it != text })
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val player = LocalPlayer.current
    val menu = LocalTrackMenu.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = Space.side, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LumenColors.paper) }
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(LumenColors.ink2).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Songs, artists, albums, playlists", style = LumenType.body, color = LumenColors.paper3)
                    BasicTextField(
                        query, { viewModel.query.value = it }, singleLine = true,
                        textStyle = LumenType.body.copy(color = LumenColors.paper),
                        cursorBrush = SolidColor(LocalAccent.current.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.remember(query) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("search_field"),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, "Clear", tint = LumenColors.paper3, modifier = Modifier.clip(CircleShape).clickable { viewModel.query.value = "" })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
            if (query.isBlank()) {
                if (recent.isNotEmpty()) item { SectionHeader("Recent searches", Modifier.padding(top = 12.dp)) }
                items(recent, key = { "recent_$it" }) { text ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.query.value = text }.padding(start = Space.side, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.History, null, tint = LumenColors.paper3)
                        Spacer(Modifier.width(16.dp))
                        Text(text, style = LumenType.body, color = LumenColors.paper, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.forget(text) }) { Icon(Icons.Rounded.Close, "Remove", tint = LumenColors.paper3) }
                    }
                }
                return@LazyColumn
            }
            if (results.empty) {
                item { Text("No matches in your library.", style = LumenType.body, color = LumenColors.paper2, modifier = Modifier.padding(Space.side)) }
            }
            if (results.songs.isNotEmpty()) {
                item { SectionHeader("Songs", Modifier.padding(top = 8.dp)) }
                itemsIndexed(results.songs, key = { _, it -> "s_${it.id}" }) { index, track ->
                    TrackRowItem(track, onClick = {
                        viewModel.remember(query)
                        player.play(results.songs, index, PlayContext.SEARCH, null, "Search: $query")
                    }, onMore = { menu.open(track) })
                }
            }
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists", Modifier.padding(top = 8.dp)) }
                items(results.artists, key = { "a_${it.id}" }) { artist ->
                    ResultLine(onClick = { viewModel.remember(query); nav.open(ArtistRoute(artist.id)) }) {
                        ArtistAvatar(artistThumb(artist.id, artist.hasImage), artist.name, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(artist.name, style = LumenType.body, color = LumenColors.paper)
                    }
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums", Modifier.padding(top = 8.dp)) }
                items(results.albums, key = { "l_${it.id}" }) { album ->
                    ResultLine(onClick = { viewModel.remember(query); nav.open(AlbumRoute(album.id)) }) {
                        CoverImage(album.coverTrackId?.let { trackThumb(it, true) }, album.title, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(album.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.artistName.orEmpty(), style = LumenType.label, color = LumenColors.paper2)
                        }
                    }
                }
            }
            if (results.playlists.isNotEmpty()) {
                item { SectionHeader("Playlists", Modifier.padding(top = 8.dp)) }
                items(results.playlists, key = { "p_${it.id}" }) { list ->
                    ResultLine(onClick = { viewModel.remember(query); nav.open(PlaylistRoute(list.id)) }) {
                        CoverImage(null, list.name, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(list.name, style = LumenType.body, color = LumenColors.paper)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultLine(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick).padding(horizontal = Space.side),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
