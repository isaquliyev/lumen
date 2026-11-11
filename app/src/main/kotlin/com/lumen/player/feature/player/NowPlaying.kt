package com.lumen.player.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.lumen.player.core.db.LyricsKind
import com.lumen.player.core.db.TrackRow
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.DefaultAccent
import com.lumen.player.core.design.GlowBackground
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LocalPaths
import com.lumen.player.core.design.LocalReduceMotion
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.ProgressScrubber
import com.lumen.player.core.design.sheetSpring
import com.lumen.player.core.util.activeLineIndex
import com.lumen.player.core.util.formatClock
import com.lumen.player.feature.focus.LocalFocusActions
import com.lumen.player.feature.library.LocalAddToPlaylist
import com.lumen.player.feature.library.LocalTrackMenu
import com.lumen.player.feature.shell.ArtistRoute
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.playback.SleepState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// the expanded player as a sheet over the app: drag it down (or press back) to collapse
@Composable
fun PlayerSheet(expanded: Boolean, viewModel: PlayerViewModel, onCollapse: () -> Unit) {
    val track by viewModel.nowPlaying.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = with(LocalDensity.current) { maxHeight.toPx() }
        val offset = remember { Animatable(height) }
        val scope = rememberCoroutineScope()
        val visible = expanded && track != null
        LaunchedEffect(visible) { offset.animateTo(if (visible) 0f else height, sheetSpring()) }
        val current = track
        if (current != null && offset.value < height - 1f) {
            Box(
                Modifier
                    .offset { IntOffset(0, offset.value.roundToInt()) }
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offset.value > height * 0.22f) onCollapse() else scope.launch { offset.animateTo(0f, sheetSpring()) }
                            },
                        ) { change, dy ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + dy).coerceIn(0f, height)) }
                        }
                    },
            ) {
                NowPlaying(current, viewModel, onCollapse)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlaying(track: TrackRow, viewModel: PlayerViewModel, onCollapse: () -> Unit) {
    val accent = viewModel.accent.collectAsStateWithLifecycle().value ?: DefaultAccent
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatMode.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val context by viewModel.context.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val sleep by viewModel.sleep.collectAsStateWithLifecycle()
    val hasSynced = lyrics?.kind == LyricsKind.SYNCED && lyrics?.parsed?.synced == true
    val position by remember(hasSynced) { viewModel.position(if (hasSynced) 100 else 250) }.collectAsStateWithLifecycle(0L)
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val menu = LocalTrackMenu.current
    val addToPlaylist = LocalAddToPlaylist.current

    CompositionLocalProvider(LocalAccent provides accent) {
        Box(Modifier.fillMaxSize().background(LumenColors.ink0).clickable(enabled = false) {}) {
            GlowBackground(accent.glow, Modifier.fillMaxSize(), centerY = 0.32f, radiusFactor = 1.0f, breathing = isPlaying)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse, modifier = Modifier.testTag("player_collapse")) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = LumenColors.paper)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PLAYING FROM", style = LumenType.overline, color = LumenColors.paper3)
                        Text(context.name, style = LumenType.label, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { shareOpen = true }, modifier = Modifier.testTag("player_share")) {
                        Icon(Icons.Rounded.Share, "Share to Instagram Story", tint = LumenColors.paper)
                    }
                    IconButton(onClick = { menu.open(track) }, modifier = Modifier.testTag("player_menu")) {
                        Icon(Icons.Rounded.MoreHoriz, "More", tint = LumenColors.paper)
                    }
                }
                Spacer(Modifier.weight(1f))
                CoverPager(viewModel)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = if (track.title.length > 26) LumenType.display.copy(fontSize = 24.sp, lineHeight = 30.sp) else LumenType.display,
                            color = LumenColors.paper,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("player_title"),
                        )
                        FlowRow {
                            artists.forEachIndexed { i, artist ->
                                ArtistName(artist.name + if (i < artists.lastIndex) ", " else "", artist.artistId)
                            }
                            if (artists.isEmpty()) Text(track.artistLine.orEmpty(), style = LumenType.label, color = LumenColors.paper2)
                        }
                    }
                    LikeButton(track.likedAt != null) { viewModel.setLiked(track.id, track.likedAt == null) }
                }
                Spacer(Modifier.height(18.dp))
                val progress = if (duration > 0) position.toFloat() / duration else 0f
                ProgressScrubber(
                    progress,
                    onSeek = { viewModel.connection.seekTo((it * duration).toLong()) },
                    onDragging = { scrubbing = it },
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = scrubbing?.let { (it * duration).toLong() } ?: position
                    Text(formatClock(shown), style = LumenType.numeric, color = LumenColors.paper3)
                    Spacer(Modifier.weight(1f))
                    Text("-" + formatClock(duration - shown), style = LumenType.numeric, color = LumenColors.paper3)
                }
                Spacer(Modifier.height(12.dp))
                Controls(isPlaying, repeat, shuffle, viewModel) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
                Spacer(Modifier.height(16.dp))
                LyricsPeek(lyrics?.parsed?.lines, hasSynced, position + track.lyricsOffsetMs, lyrics?.kind) { lyricsOpen = true }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    if (hasSynced) BottomAction(Icons.Rounded.Lyrics, "Lyrics", "player_lyrics") { lyricsOpen = true }
                    BottomAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", "player_queue") { queueOpen = true }
                    BottomAction(Icons.Rounded.Bedtime, sleepLabel(sleep), "player_sleep", active = sleep != SleepState.Off) { sleepOpen = true }
                    BottomAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add", "player_add") { addToPlaylist(listOf(track.id)) }
                }
            }
            AnimatedVisibility(
                visible = lyricsOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                LyricsScreen(track, lyrics, viewModel, onClose = { lyricsOpen = false })
            }
        }
        if (queueOpen) QueueSheet(viewModel, onDismiss = { queueOpen = false })
        if (sleepOpen) SleepSheet(viewModel, sleep, onDismiss = { sleepOpen = false })
        if (shareOpen) {
            val lines = if (hasSynced) lyrics?.parsed?.lines.orEmpty().map { it.text.trim() }.filter { it.isNotEmpty() }.distinct() else emptyList()
            StoryShareSheet(track, lines, onDismiss = { shareOpen = false })
        }
    }
}

@Composable
private fun sleepLabel(sleep: SleepState): String {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep) {
        while (sleep is SleepState.At) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    return when (sleep) {
        SleepState.Off -> "Sleep"
        SleepState.EndOfSong -> "End of song"
        is SleepState.At -> formatClock((sleep.endsAt - now).coerceAtLeast(0))
    }
}

@Composable
private fun CoverPager(viewModel: PlayerViewModel) {
    val queue by viewModel.queueUi.collectAsStateWithLifecycle()
    val paths = LocalPaths.current
    val entries = queue.entries
    if (entries.isEmpty()) return
    val pager = rememberPagerState(initialPage = queue.currentIndex.coerceIn(0, entries.lastIndex)) { entries.size }
    val latest by rememberUpdatedState(queue)
    LaunchedEffect(queue.currentIndex) {
        val target = queue.currentIndex
        if (target in entries.indices && pager.currentPage != target) {
            if (kotlin.math.abs(pager.currentPage - target) == 1) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page ->
            if (page != latest.currentIndex && page in latest.entries.indices) viewModel.connection.seekToIndex(page)
        }
    }
    HorizontalPager(pager, modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("cover_pager"), pageSpacing = 16.dp, beyondViewportPageCount = 1) { page ->
        val row = entries[page].row
        CoverImage(
            if (row?.hasCover == true) paths.cover(row.id) else null,
            row?.title ?: "",
            Modifier.fillMaxSize(),
            radius = Radii.card,
            letterSize = LumenType.displayHero.fontSize,
        )
    }
}

@Composable
private fun ArtistName(label: String, artistId: String) {
    var open by remember { mutableStateOf(false) }
    val nav = LocalNav.current
    val focus = LocalFocusActions.current
    Box {
        Text(label, style = LumenType.label, color = LumenColors.paper2, modifier = Modifier.clickable { open = true }.testTag("player_artist_$artistId"))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = LumenColors.ink2) {
            val name = label.removeSuffix(", ")
            DropdownMenuItem(text = { Text("Open $name", color = LumenColors.paper) }, onClick = { open = false; nav.open(ArtistRoute(artistId)) })
            DropdownMenuItem(text = { Text("Focus on $name", color = LumenColors.paper) }, onClick = { open = false; focus.only(artistId) })
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, onToggle: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (popped && !reduceMotion) 1.25f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "like",
        finishedListener = { popped = false })
    IconButton(onClick = { if (!liked) popped = true; onToggle() }, modifier = Modifier.testTag("player_like")) {
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (liked) "Unlike" else "Like",
            tint = if (liked) LocalAccent.current.accent else LumenColors.paper,
            modifier = Modifier.scale(scale),
        )
    }
}

@Composable
private fun Controls(isPlaying: Boolean, repeat: Int, shuffle: Boolean, viewModel: PlayerViewModel, tick: () -> Unit) {
    val accent = LocalAccent.current.accent
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.connection.toggleShuffle() }, modifier = Modifier.testTag("player_shuffle")) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) accent else LumenColors.paper2)
        }
        IconButton(onClick = { viewModel.connection.previous() }, modifier = Modifier.size(56.dp).testTag("player_previous")) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = LumenColors.paper, modifier = Modifier.size(36.dp))
        }
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(LumenColors.paper).clickable { tick(); viewModel.connection.playPause() }.testTag("player_play_pause"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = LumenColors.ink, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { viewModel.connection.next() }, modifier = Modifier.size(56.dp).testTag("player_next")) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = LumenColors.paper, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { viewModel.connection.cycleRepeat() }, modifier = Modifier.testTag("player_repeat")) {
            Icon(
                if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (repeat == Player.REPEAT_MODE_OFF) LumenColors.paper2 else accent,
            )
        }
    }
}

@Composable
private fun LyricsPeek(lines: List<com.lumen.player.core.util.LyricLine>?, synced: Boolean, positionMs: Long, kind: LyricsKind?, onOpen: () -> Unit) {
    if (lines.isNullOrEmpty() || (kind != LyricsKind.SYNCED && kind != LyricsKind.PLAIN)) return
    val text = if (synced) {
        val index = activeLineIndex(lines, positionMs)
        lines.getOrNull(index)?.text?.ifBlank { "♪" } ?: "♪"
    } else lines.first().text
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(LumenColors.ink2.copy(alpha = 0.9f))
            .clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp).testTag("lyrics_peek"),
    ) {
        AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "peek") { line ->
            Text(line, style = LumenType.body.copy(fontSize = 17.sp), color = LumenColors.paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, active: Boolean = false, onClick: () -> Unit) {
    val color = if (active) LocalAccent.current.accent else LumenColors.paper2
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag(tag).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = LumenType.numeric, color = color)
    }
}
