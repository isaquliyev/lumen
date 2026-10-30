package com.lumen.player.feature.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.design.ArtistAvatar
import com.lumen.player.core.design.BarRow
import com.lumen.player.core.design.CalendarHeatmap
import com.lumen.player.core.design.CountUpText
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.HBarList
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.RadialClock
import com.lumen.player.core.design.ScreenTitle
import com.lumen.player.core.design.SegmentedTabs
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.Sparkline
import com.lumen.player.core.design.TrackRowItem
import com.lumen.player.core.design.artistThumb
import com.lumen.player.core.design.trackThumb
import com.lumen.player.core.util.formatBytes
import com.lumen.player.core.util.formatCount
import com.lumen.player.core.util.formatLength
import com.lumen.player.data.MIN_PULSE_MS
import com.lumen.player.data.PulseData
import com.lumen.player.data.RangeKind
import com.lumen.player.feature.focus.FocusPill
import com.lumen.player.feature.shell.AlbumRoute
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.bottomPadding
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PulseScreen(viewModel: PulseViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val scoped by viewModel.scoped.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    var allSongs by remember { mutableStateOf(false) }
    val nav = LocalNav.current
    val player = LocalPlayer.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                ScreenTitle("Pulse")
                FocusPill(Modifier.padding(start = Space.side, top = 4.dp))
                Spacer(Modifier.height(12.dp))
                SegmentedTabs(RangeKind.entries.map { it.label }, kind.ordinal, { viewModel.setKind(RangeKind.entries[it]) })
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::older, enabled = kind != RangeKind.ALL, modifier = Modifier.testTag("pulse_older")) {
                        Icon(Icons.Rounded.ChevronLeft, "Earlier", tint = if (kind != RangeKind.ALL) LumenColors.paper else LumenColors.paper3)
                    }
                    Text(data?.period?.label.orEmpty(), style = LumenType.body, color = LumenColors.paper, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::newer, enabled = offset > 0, modifier = Modifier.testTag("pulse_newer")) {
                        Icon(Icons.Rounded.ChevronRight, "Later", tint = if (offset > 0) LumenColors.paper else LumenColors.paper3)
                    }
                }
                if (focus.active) {
                    SegmentedTabs(listOf("Everything", "Focus"), if (scoped) 1 else 0, { viewModel.scoped.value = it == 1 }, Modifier.padding(bottom = 8.dp))
                }
            }
        }
        val d = data ?: return@LazyColumn
        d.focusCard?.let { card ->
            item { FocusCardView(card) }
        }
        if (!d.enough) {
            item { NotEnough(d.totalMs) }
            return@LazyColumn
        }
        item { Hero(d) }
        if (d.topSongs.isNotEmpty()) {
            item { Section("Top songs", if (d.topSongs.size > 5) (if (allSongs) "Show less" else "See all") else null) { allSongs = !allSongs } }
            val songs = if (allSongs) d.topSongs else d.topSongs.take(5)
            itemsIndexed(songs, key = { _, it -> "top_${it.row.id}" }) { index, ranked ->
                TrackRowItem(
                    ranked.row,
                    onClick = { player.play(songs.map { it.row }, index, PlayContext.QUEUE, null, "Pulse · top songs") },
                    leading = { Text("${index + 1}", style = LumenType.numeric, color = LumenColors.paper3, modifier = Modifier.width(26.dp)) },
                    trailing = { Text(formatLength(ranked.listenedMs), style = LumenType.numeric, color = LumenColors.paper2) },
                )
            }
        }
        if (d.topArtists.isNotEmpty()) {
            item { Section("Top artists") }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(d.topArtists, key = { it.id }) { artist ->
                        Column(Modifier.width(96.dp).clickable { nav.open(ArtistRoute(artist.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            ArtistAvatar(artistThumb(artist.id, artist.hasImage), artist.name, Modifier.size(96.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(artist.name, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatLength(artist.listenedMs), style = LumenType.numeric, color = LumenColors.paper3)
                        }
                    }
                }
            }
        }
        if (d.topAlbums.isNotEmpty()) {
            item { Section("Top albums") }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(d.topAlbums, key = { it.id }) { album ->
                        Column(Modifier.width(120.dp).clickable { nav.open(AlbumRoute(album.id)) }) {
                            CoverImage(album.coverTrackId?.let { trackThumb(it, true) }, album.title, Modifier.size(120.dp), radius = Radii.card)
                            Spacer(Modifier.height(6.dp))
                            Text(album.title, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.artist.orEmpty(), style = LumenType.numeric, color = LumenColors.paper3, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (d.genres.isNotEmpty()) {
            item { Section("Top genres") }
            item { HBarList(d.genres.map { it.name to it.fraction }, Modifier.padding(horizontal = Space.side)) }
        }
        item { Section("Listening clock") }
        item {
            RadialClock(d.hours, LocalTime.now().hour, Modifier.padding(horizontal = 40.dp))
        }
        item { Section("Week rhythm") }
        item { BarRow(d.weekdays, listOf("M", "T", "W", "T", "F", "S", "S"), Modifier.padding(horizontal = Space.side)) }
        if (kind != RangeKind.TODAY && kind != RangeKind.WEEK) {
            item { Section("Calendar") }
            item { CalendarHeatmap(d.period.days, d.days, yearMode = kind != RangeKind.MONTH, modifier = Modifier.padding(horizontal = Space.side)) }
        }
        item { Section("Streaks") }
        item {
            StatRow(
                "Current" to formatCount(d.streaks.current, "day"),
                "Longest" to formatCount(d.streaks.longest, "day"),
            )
        }
        item { Section("Discovery") }
        item {
            StatRow(
                "Songs added" to "%,d".format(d.songsAdded),
                "First listens" to "%,d".format(d.firstListens),
                "New to you" to "${(d.newShare * 100).toInt()} %",
            )
        }
        item { Section("Habits") }
        item {
            StatRow(
                "Skip rate" to "${(d.skipRate * 100).toInt()} %",
                "Completed" to "${(d.completionRate * 100).toInt()} %",
                "Avg session" to formatLength(d.sessions.averageMs),
            )
        }
        item {
            StatRow(
                "Longest session" to formatLength(d.sessions.longestMs),
                "Most active day" to (d.mostActiveDay?.let { it.day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) } ?: "–"),
            )
        }
        item { Section("Library") }
        item {
            StatRow(
                "Songs" to "%,d".format(d.librarySongs),
                "Hours" to "%,d".format(d.libraryMs / 3_600_000),
                "On disk" to formatBytes(d.libraryBytes),
            )
        }
        if (d.growth.size > 1) item { Sparkline(d.growth.map { it.second.toFloat() }, Modifier.padding(horizontal = Space.side)) }
    }
}

@Composable
private fun Section(title: String, action: String? = null, overline: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.padding(top = 28.dp)) {
        Box(Modifier.padding(horizontal = Space.side).fillMaxWidth().height(1.dp).background(LumenColors.line))
        com.lumen.player.core.design.SectionHeader(title, overline = overline, action = action, onAction = onAction, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Hero(d: PulseData) {
    Column(Modifier.padding(horizontal = Space.side, vertical = 8.dp)) {
        CountUpText(d.totalMs / 60_000, LumenType.displayHero, LumenColors.paper, Modifier.testTag("pulse_minutes"))
        Text("minutes listened", style = LumenType.label, color = LumenColors.paper2)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatCount(d.plays, "play"), style = LumenType.body, color = LumenColors.paper)
            d.changePct?.let { change ->
                Spacer(Modifier.width(12.dp))
                Text(
                    (if (change >= 0) "▲ " else "▼ ") + "${kotlin.math.abs(change)} %",
                    style = LumenType.numeric,
                    color = if (change >= 0) LumenColors.positive else LumenColors.danger,
                )
            }
        }
    }
}

@Composable
private fun NotEnough(totalMs: Long) {
    val accent = LocalAccent.current.accent
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Not enough listening yet", style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("${totalMs / 60_000} of ${MIN_PULSE_MS / 60_000} minutes", style = LumenType.label, color = LumenColors.paper2)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (totalMs.toFloat() / MIN_PULSE_MS).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = accent, trackColor = LumenColors.ink3, drawStopIndicator = {}, gapSize = 0.dp,
        )
    }
}

@Composable
private fun FocusCardView(card: com.lumen.player.data.FocusCard) {
    val accent = LocalAccent.current.accent
    Column(
        Modifier.padding(horizontal = Space.side, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(Radii.card))
            .background(accent.copy(alpha = 0.10f)).padding(16.dp),
    ) {
        Text("FOCUS · ${card.names.uppercase()}", style = LumenType.overline, color = accent)
        Spacer(Modifier.height(6.dp))
        Text("${card.listenedMs / 60_000} min · ${formatCount(card.plays, "play")}", style = LumenType.title, color = LumenColors.paper)
        card.topSong?.let { Text("Top song: ${it.title}", style = LumenType.label, color = LumenColors.paper2) }
        card.firstListen?.let {
            val date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            Text("First listened ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}", style = LumenType.label, color = LumenColors.paper2)
        }
    }
}

@Composable
private fun StatRow(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.side, vertical = 6.dp)) {
        stats.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = LumenType.title, color = LumenColors.paper)
                Text(label, style = LumenType.label, color = LumenColors.paper2)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItsCounted(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LumenColors.ink2, shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.side).padding(bottom = 24.dp)) {
            Text("How it's counted", style = LumenType.display, color = LumenColors.paper)
            Spacer(Modifier.height(12.dp))
            listOf(
                "Listening time" to "Real time the music was playing. Pauses and seeking don't count.",
                "A play" to "A listen of 30 seconds or more (or half of a song shorter than a minute).",
                "Skip" to "Moving on before 30 seconds and before half of the song.",
                "Completed" to "The song played to its end (or repeated).",
                "Session" to "Listening with gaps shorter than 15 minutes.",
                "First listen" to "The first play of a song, ever.",
                "Periods" to "Use your phone's time zone. Weeks start on your locale's first day.",
            ).forEach { (term, text) ->
                Text(term, style = LumenType.title, color = LumenColors.paper, modifier = Modifier.padding(top = 10.dp))
                Text(text, style = LumenType.body, color = LumenColors.paper2)
            }
        }
    }
}
