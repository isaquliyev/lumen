package com.lumen.player.feature.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.ArtistDao
import com.lumen.player.core.db.ArtistEntity
import com.lumen.player.core.db.ArtistRow
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.design.ArtistAvatar
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.artistThumb
import com.lumen.player.core.util.normalize
import com.lumen.player.data.Focus
import com.lumen.player.data.FocusRepository
import com.lumen.player.data.TrackRepository
import com.lumen.player.playback.PlayerConnection
import com.lumen.player.playback.QueueManager
import com.lumen.player.playback.trackIdOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// shell-level Artist Focus state and actions
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val repository: FocusRepository,
    private val tracks: TrackRepository,
    private val queue: QueueManager,
    private val connection: PlayerConnection,
    private val events: AppEvents,
    artistDao: ArtistDao,
) : ViewModel() {
    val focus: StateFlow<Focus> = repository.focus
    val focusedArtists: StateFlow<List<ArtistEntity>> = repository.focus
        .flatMapLatest { if (it.off) flowOf(emptyList()) else artistDao.observeByIds(it.idList) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allArtists: StateFlow<List<ArtistRow>> = artistDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sheetOpen = MutableStateFlow(false)

    init {
        // turning Focus on while a non-matching song plays: keep playing, offer to switch
        viewModelScope.launch {
            repository.focus.drop(1).collect { focus ->
                if (focus.off) return@collect
                val playing = connection.nowPlaying.value ?: return@collect
                val matches = tracks.songs.first().any { it.id == playing.id }
                android.util.Log.d("Lumen", "focus changed: playing=${playing.title} matches=$matches isPlaying=${connection.isPlaying.value}")
                if (!matches && connection.isPlaying.value) {
                    val names = focusedArtists.first { list -> list.isNotEmpty() || focus.off }.joinToString(" & ") { it.name }
                    events.show("Play only $names now?", "Play") { playFocused() }
                }
            }
        }
    }

    fun only(id: String) = viewModelScope.launch { repository.only(id) }
    fun toggle(id: String) = viewModelScope.launch { repository.toggle(id) }
    fun clear() = viewModelScope.launch { repository.clear() }

    fun playFocused() = viewModelScope.launch {
        val songs = tracks.songs.first()
        queue.play(songs, -1, PlayContext.ARTIST, focus.value.idList.firstOrNull(), "Focus", shuffle = true)
    }

}

val LocalFocusActions = staticCompositionLocalOf<FocusViewModel> { error("FocusViewModel not provided") }

// the app accent while Focus is on: the first focused artist's photo tone
@Composable
fun focusAccent(): com.lumen.player.core.design.Accent? {
    val artists by LocalFocusActions.current.focusedArtists.collectAsStateWithLifecycle()
    val first = artists.firstOrNull() ?: return null
    return remember(first.id, first.toneHue) {
        com.lumen.player.core.design.accentFor(
            first.toneHue?.let { com.lumen.player.core.util.CoverTone(it, first.toneSat ?: 0.5f) } ?: com.lumen.player.core.util.seededTone(first.name)
        )
    }
}

// a very soft accent vignette around the screen edges while Focus is on
@Composable
fun FocusVignette(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    0.55f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to color.copy(alpha = 0.06f),
                    center = center,
                    radius = size.maxDimension * 0.75f,
                )
            )
        }
    )
}

// "Talk Talk ✕" pill under screen titles while Focus is on
@Composable
fun FocusPill(modifier: Modifier = Modifier) {
    val viewModel = LocalFocusActions.current
    val artists by viewModel.focusedArtists.collectAsStateWithLifecycle()
    val accent = LocalAccent.current.accent
    AnimatedVisibility(visible = artists.isNotEmpty(), modifier = modifier) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .clickable { viewModel.sheetOpen.value = true }
                .testTag("focus_pill")
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                artists.take(3).forEachIndexed { i, artist ->
                    ArtistAvatar(
                        artistThumb(artist.id, artist.hasImage),
                        artist.name,
                        Modifier.offset(x = (i * 14).dp).size(24.dp).border(1.5.dp, LumenColors.ink, CircleShape),
                        letterSize = LumenType.label.fontSize,
                    )
                }
            }
            Spacer(Modifier.width((10 + (artists.take(3).size - 1) * 14).dp))
            Text(
                artists.joinToString(" & ") { it.name },
                style = LumenType.label,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Rounded.Close,
                "Clear focus",
                tint = accent,
                modifier = Modifier.padding(start = 6.dp).size(20.dp).clip(CircleShape).clickable { viewModel.clear() }.testTag("focus_clear"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusSheet() {
    val viewModel = LocalFocusActions.current
    val open by viewModel.sheetOpen.collectAsStateWithLifecycle()
    if (!open) return
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val artists by viewModel.allArtists.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val accent = LocalAccent.current.accent
    val shown = remember(artists, query, focus) {
        val q = normalize(query)
        artists.filter { q.isEmpty() || normalize(it.name).contains(q) }.sortedByDescending { it.id in focus.ids }.take(60)
    }
    ModalBottomSheet(
        onDismissRequest = { viewModel.sheetOpen.value = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LumenColors.ink2,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Artist Focus", style = LumenType.display, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side))
            Text(
                "Pick artists, and Lumen shows and plays only their songs.",
                style = LumenType.label,
                color = LumenColors.paper2,
                modifier = Modifier.padding(horizontal = Space.side),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.padding(horizontal = Space.side).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(LumenColors.ink3).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = LumenColors.paper3)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search artists", style = LumenType.body, color = LumenColors.paper3)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = LumenType.body.copy(color = LumenColors.paper),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth().testTag("focus_search"),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(horizontal = Space.side),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEach { artist ->
                    val on = artist.id in focus.ids
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (on) accent.copy(alpha = 0.18f) else LumenColors.ink3)
                            .clickable { viewModel.toggle(artist.id) }
                            .testTag("focus_chip_${artist.id}")
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtistAvatar(artistThumb(artist.id, artist.hasImage), artist.name, Modifier.size(28.dp), letterSize = LumenType.label.fontSize)
                        Spacer(Modifier.width(8.dp))
                        Text(artist.name, style = LumenType.label, color = if (on) accent else LumenColors.paper)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = Space.side)) {
                PillButton("Clear focus", { viewModel.clear() }, style = PillStyle.OUTLINED, enabled = focus.active, modifier = Modifier.testTag("focus_sheet_clear"))
                Spacer(Modifier.weight(1f))
                PillButton("Done", { viewModel.sheetOpen.value = false }, modifier = Modifier.testTag("focus_done"))
            }
        }
    }
}
