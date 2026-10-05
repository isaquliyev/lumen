package com.lumen.player.feature.recap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lumen.player.core.AppEvents
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.design.ArtistAvatar
import com.lumen.player.core.design.CountUpText
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.GlowBackground
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LocalPaths
import com.lumen.player.core.design.LocalReduceMotion
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.accentFor
import com.lumen.player.core.design.trackCover
import com.lumen.player.core.design.trackThumb
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.core.util.CoverTone
import com.lumen.player.core.util.formatCount
import com.lumen.player.data.Focus
import com.lumen.player.data.PulseData
import com.lumen.player.data.PulseRepository
import com.lumen.player.data.RangeKind
import com.lumen.player.feature.shell.LocalNav
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.RecapRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RecapViewModel @Inject constructor(
    handle: SavedStateHandle,
    repository: PulseRepository,
    private val paths: LumenPaths,
    private val events: AppEvents,
) : ViewModel() {
    private val route = handle.toRoute<RecapRoute>()
    val yearMode = route.month == 0
    val data = MutableStateFlow<PulseData?>(null)
    val title: String = if (yearMode) route.year.toString()
    else LocalDate.of(route.year, route.month, 1).format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
    val fileTag: String = if (yearMode) "${route.year}" else "%d-%02d".format(route.year, route.month)

    init {
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault())
            val offset = if (yearMode) today.year - route.year
            else ChronoUnit.MONTHS.between(LocalDate.of(route.year, route.month, 1), today.withDayOfMonth(1)).toInt()
            data.value = repository.build(if (yearMode) RangeKind.YEAR else RangeKind.MONTH, offset.coerceAtLeast(0), Focus(emptySet()), false)
        }
    }

    // saves the summary card to Lumen/exports and opens the share sheet
    fun share(context: Context, bitmap: Bitmap) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            paths.exportsDir.mkdirs()
            File(paths.exportsDir, "recap-$fileTag.png").also { target -> target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your recap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        events.show("Saved to Lumen/exports")
    }
}

private const val SLIDE_MS = 6_000L

@Composable
fun RecapScreen(viewModel: RecapViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val d = data
    Box(Modifier.fillMaxSize().background(LumenColors.ink0)) {
        if (d == null) return@Box
        if (!d.enough) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Not enough listening in ${viewModel.title}.", style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.Close, "Close", tint = LumenColors.paper) }
            }
            return@Box
        }
        val top = d.topSongs.firstOrNull()
        val accent = remember(top?.row?.id) {
            accentFor(top?.row?.toneHue?.let { CoverTone(it, top.row.toneSat ?: 0.5f) })
        }
        CompositionLocalProvider(LocalAccent provides accent) { Stories(d, viewModel) }
    }
}

@Composable
private fun Stories(d: PulseData, viewModel: RecapViewModel) {
    val nav = LocalNav.current
    val slides = remember(d) { buildSlides(d) }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    val reduceMotion = LocalReduceMotion.current
    val accent = LocalAccent.current

    LaunchedEffect(index, paused) {
        if (paused) return@LaunchedEffect
        while (elapsed < 1f) {
            delay(50)
            elapsed += 50f / SLIDE_MS
        }
        if (index < slides.lastIndex) {
            index++
            elapsed = 0f
        }
    }

    GlowBackground(accent.glow, Modifier.fillMaxSize(), centerY = 0.45f, radiusFactor = 1.1f, breathing = !reduceMotion) {
        Box(
            Modifier.fillMaxSize().pointerInput(slides.size) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { offset ->
                        if (offset.x > size.width / 3) {
                            if (index < slides.lastIndex) { index++; elapsed = 0f }
                        } else if (index > 0) { index--; elapsed = 0f }
                    },
                )
            }.testTag("recap_stories"),
        ) {
            AnimatedContent(index, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "slide", modifier = Modifier.fillMaxSize()) { i ->
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), contentAlignment = Alignment.Center) {
                    when (slides[i]) {
                        Slide.INTRO -> Center("Your ${viewModel.title}", "in music", big = true)
                        Slide.MINUTES -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CountUpText(d.totalMs / 60_000, LumenType.displayHero, LumenColors.paper)
                            Text("minutes of music", style = LumenType.title, color = LumenColors.paper2)
                            Text(formatCount(d.plays, "play"), style = LumenType.body, color = LumenColors.paper3)
                        }
                        Slide.TOP_SONG -> TopSong(d)
                        Slide.TOP_ARTIST -> TopArtist(d)
                        Slide.TOP_FIVE -> TopFive(d)
                        Slide.CLOCK -> Center("You're a %02d:00 listener".format(d.peakHour ?: 0), "That's when your music lives.")
                        Slide.PERSONALITY -> Center(d.personality?.main?.name ?: "Balanced Listener", d.personality?.main?.line.orEmpty(), big = true)
                        Slide.STREAK -> Center("${d.streaks.longest}-day streak", "Your longest run of days with music.")
                        Slide.DISCOVERY -> Center("${d.firstListens} first listens", "${(d.newShare * 100).toInt()} % of your time went to songs new to you.")
                        Slide.SUMMARY -> Summary(d, viewModel)
                    }
                }
            }
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> elapsed.coerceIn(0f, 1f)
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(LumenColors.paper.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(fill).height(3.dp).background(LumenColors.paper))
                    }
                }
            }
            IconButton(onClick = { nav.back() }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp).testTag("recap_close")) {
                Icon(Icons.Rounded.Close, "Close", tint = LumenColors.paper)
            }
        }
    }
}

private enum class Slide { INTRO, MINUTES, TOP_SONG, TOP_ARTIST, TOP_FIVE, CLOCK, PERSONALITY, STREAK, DISCOVERY, SUMMARY }

private fun buildSlides(d: PulseData) = buildList {
    add(Slide.INTRO)
    add(Slide.MINUTES)
    if (d.topSongs.isNotEmpty()) add(Slide.TOP_SONG)
    if (d.topArtists.isNotEmpty()) add(Slide.TOP_ARTIST)
    if (d.topSongs.size >= 2) add(Slide.TOP_FIVE)
    if (d.peakHour != null) add(Slide.CLOCK)
    if (d.personality != null) add(Slide.PERSONALITY)
    if (d.streaks.longest > 1) add(Slide.STREAK)
    if (d.firstListens > 0) add(Slide.DISCOVERY)
    add(Slide.SUMMARY)
}

@Composable
private fun Center(title: String, subtitle: String, big: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = if (big) LumenType.displayHero else LumenType.displayLarge, color = LumenColors.paper, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, style = LumenType.title, color = LumenColors.paper2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TopSong(d: PulseData) {
    val song = d.topSongs.first()
    val player = LocalPlayer.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("YOUR TOP SONG", style = LumenType.overline, color = LumenColors.paper2)
        Spacer(Modifier.height(16.dp))
        CoverImage(trackCover(song.row.id, song.row.hasCover), song.row.title, Modifier.fillMaxWidth(0.8f).aspectRatio(1f), radius = Radii.card)
        Spacer(Modifier.height(20.dp))
        Text(song.row.title, style = LumenType.display, color = LumenColors.paper, textAlign = TextAlign.Center)
        Text("${song.row.artistLine.orEmpty()} · ${formatCount(song.plays, "play")}", style = LumenType.label, color = LumenColors.paper2)
        Spacer(Modifier.height(16.dp))
        IconButton(
            onClick = { player.play(listOf(song.row), 0, PlayContext.RECAP, null, "Recap") },
            modifier = Modifier.size(56.dp).clip(CircleShape).background(LumenColors.paper).testTag("recap_play"),
        ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = LumenColors.ink) }
    }
}

@Composable
private fun TopArtist(d: PulseData) {
    val artist = d.topArtists.first()
    val paths = LocalPaths.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("YOUR TOP ARTIST", style = LumenType.overline, color = LumenColors.paper2)
        Spacer(Modifier.height(16.dp))
        ArtistAvatar(if (artist.hasImage) paths.artistImage(artist.id) else null, artist.name, Modifier.fillMaxWidth(0.75f).aspectRatio(1f))
        Spacer(Modifier.height(20.dp))
        Text(artist.name, style = LumenType.displayLarge, color = LumenColors.paper, textAlign = TextAlign.Center)
        Text("${artist.listenedMs / 60_000} minutes together", style = LumenType.title, color = LumenColors.paper2)
    }
}

@Composable
private fun TopFive(d: PulseData) {
    Column(Modifier.fillMaxWidth()) {
        Text("YOUR TOP SONGS", style = LumenType.overline, color = LumenColors.paper2)
        Spacer(Modifier.height(16.dp))
        d.topSongs.take(5).forEachIndexed { i, song ->
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = LumenType.display, color = LocalAccent.current.accent, modifier = Modifier.width(40.dp))
                CoverImage(trackThumb(song.row.id, song.row.hasCover), song.row.title, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(song.row.title, style = LumenType.title, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.row.artistLine.orEmpty(), style = LumenType.label, color = LumenColors.paper2, maxLines = 1)
                }
            }
        }
    }
}

// the shareable card: drawn into a graphics layer so it can be saved as an image
@Composable
private fun Summary(d: PulseData, viewModel: RecapViewModel) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accent = LocalAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .clip(RoundedCornerShape(Radii.card))
                .background(LumenColors.ink2)
                .padding(24.dp),
        ) {
            Text("LUMEN · ${viewModel.title.uppercase()}", style = LumenType.overline, color = accent.accent)
            Spacer(Modifier.height(8.dp))
            Text("${d.totalMs / 60_000} minutes", style = LumenType.displayLarge, color = LumenColors.paper)
            Spacer(Modifier.height(16.dp))
            Text("TOP SONGS", style = LumenType.overline, color = LumenColors.paper3)
            d.topSongs.take(5).forEachIndexed { i, s ->
                Text("${i + 1}. ${s.row.title}", style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Text("TOP ARTISTS", style = LumenType.overline, color = LumenColors.paper3)
            d.topArtists.take(3).forEachIndexed { i, a -> Text("${i + 1}. ${a.name}", style = LumenType.body, color = LumenColors.paper) }
            Spacer(Modifier.height(12.dp))
            d.personality?.let { Text(it.main.name, style = LumenType.title, color = accent.accent) }
        }
        Spacer(Modifier.height(20.dp))
        IconButton(
            onClick = { scope.launch { viewModel.share(context, layer.toImageBitmap().asAndroidBitmap()) } },
            modifier = Modifier.size(56.dp).clip(CircleShape).background(LumenColors.paper).testTag("recap_share"),
        ) { Icon(Icons.Rounded.Share, "Share", tint = LumenColors.ink) }
    }
}
