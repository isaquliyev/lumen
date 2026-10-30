package com.lumen.player.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.ArtistAvatar
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.SectionHeader
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.artistThumb
import com.lumen.player.core.design.trackThumb
import com.lumen.player.core.util.formatLength
import com.lumen.player.feature.focus.FocusPill
import com.lumen.player.feature.focus.LocalFocusActions
import com.lumen.player.feature.playlist.PlaylistCover
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.PlaylistRoute
import com.lumen.player.feature.shell.PulseRoute
import com.lumen.player.feature.shell.bottomPadding
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun greeting(hour: Int = LocalTime.now().hour) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
fun HomeHeader(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    val focusActions = LocalFocusActions.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.side, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)).uppercase(),
                style = LumenType.overline,
                color = LumenColors.paper3,
            )
            Text(greeting(), style = LumenType.display, color = LumenColors.paper)
        }
        IconButton(onClick = { focusActions.sheetOpen.value = true }, modifier = Modifier.testTag("home_focus")) {
            Icon(Icons.Rounded.CenterFocusStrong, "Artist Focus", tint = LumenColors.paper)
        }
        IconButton(onClick = onOpenSearch, modifier = Modifier.testTag("home_search")) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = LumenColors.paper)
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings")) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = LumenColors.paper)
        }
    }
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onOpenSearch: () -> Unit, onOpenAdd: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = LocalPlayer.current
    val nav = LocalNav.current
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val context by player.context.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            HomeHeader(onOpenSearch, onOpenSettings)
            FocusPill(Modifier.padding(start = Space.side, top = 8.dp))
        }
        if (state.loaded && state.songCount == 0) {
            item {
                EmptyState(
                    serifLine = "Your library is quiet.",
                    body = "Songs you add live on this phone, in your Lumen folder.",
                    action = "Add your first song",
                    onAction = onOpenAdd,
                    modifier = Modifier.padding(top = 80.dp),
                )
            }
            return@LazyColumn
        }
        nowPlaying?.let { track ->
            item(key = "resume") { ResumeCard(track, context.name, isPlaying) { player.connection.playPause() } }
        }
        state.strip?.takeIf { it.todayMs > 0 || it.streak > 0 }?.let { strip ->
            item(key = "strip") {
                val parts = listOfNotNull(
                    "Today ${formatLength(strip.todayMs)}",
                    strip.streak.takeIf { it > 0 }?.let { "$it-day streak" },
                    strip.topArtist?.let { "Top: $it" },
                )
                Row(
                    Modifier.padding(horizontal = Space.side, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(LumenColors.ink2).clickable { nav.open(PulseRoute) }.padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("home_pulse_strip"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(parts.joinToString(" · "), style = LumenType.label, color = LumenColors.paper, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = LumenColors.paper3, modifier = Modifier.size(18.dp))
                }
            }
        }
        coverRow("on_repeat", "On repeat", "Last 30 days", state.onRepeat) { i -> player.play(state.onRepeat, i, PlayContext.HOME, "on_repeat", "On repeat") }
        coverRow("this_hour", "For this hour", "What you play around now", state.forThisHour) { i -> player.play(state.forThisHour, i, PlayContext.HOME, "this_hour", "For this hour") }
        if (state.playlists.isNotEmpty()) {
            item(key = "playlists_h") { SectionHeader("Your playlists", Modifier.padding(top = 12.dp)) }
            item(key = "playlists") {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.playlists, key = { it.id }) { list ->
                        Column(Modifier.width(140.dp).clickable { nav.open(PlaylistRoute(list.id)) }) {
                            PlaylistCover(list.id, list.name, list.hasCustomCover, list.updatedAt, state.covers[list.id].orEmpty(), Modifier.size(140.dp), radius = Radii.card)
                            Spacer(Modifier.height(6.dp))
                            Text(list.name, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (state.artists.isNotEmpty()) {
            item(key = "artists_h") { SectionHeader("Artists you love", Modifier.padding(top = 12.dp)) }
            item(key = "artists") {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.artists, key = { it.id }) { artist ->
                        Column(Modifier.width(100.dp).clickable { nav.open(ArtistRoute(artist.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            ArtistAvatar(artistThumb(artist.id, artist.hasImage), artist.name, Modifier.size(100.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(artist.name, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        coverRow("recent", "Recently added", null, state.recentlyAdded) { i -> player.play(state.recentlyAdded, i, PlayContext.HOME, "recent", "Recently added") }
        coverRow("rediscover", "Rediscover", "Loved once, quiet for 60 days", state.rediscover) { i -> player.play(state.rediscover, i, PlayContext.HOME, "rediscover", "Rediscover") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.coverRow(key: String, title: String, overline: String?, songs: List<TrackRow>, onPlay: (Int) -> Unit) {
    if (songs.isEmpty()) return
    item(key = "${key}_h") { SectionHeader(title, Modifier.padding(top = 12.dp), overline = overline) }
    item(key = key) {
        LazyRow(contentPadding = PaddingValues(horizontal = Space.side), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("home_$key")) {
            itemsIndexed(songs, key = { _, it -> it.id }) { index, track ->
                Column(Modifier.width(140.dp).clickable { onPlay(index) }) {
                    CoverImage(trackThumb(track.id, track.hasCover), track.title, Modifier.size(140.dp), radius = Radii.card, letterSize = LumenType.display.fontSize)
                    Spacer(Modifier.height(6.dp))
                    Text(track.title, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artistLine.orEmpty(), style = LumenType.numeric, color = LumenColors.paper3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(track: TrackRow, contextName: String, isPlaying: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.padding(horizontal = Space.side, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(Radii.card))
            .background(LumenColors.ink2).padding(14.dp).testTag("home_resume"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(trackThumb(track.id, track.hasCover), track.title, Modifier.size(72.dp), radius = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isPlaying) "NOW PLAYING" else "PICK UP WHERE YOU LEFT OFF", style = LumenType.overline, color = LumenColors.paper3)
            Text(track.title, style = LumenType.title, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contextName, style = LumenType.label, color = LumenColors.paper2, maxLines = 1)
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(LumenColors.paper).clickable(onClick = onToggle).testTag("home_resume_play"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Resume", tint = LumenColors.ink)
        }
    }
}
