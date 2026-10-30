package com.lumen.player.feature.add

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.DownloadJobEntity
import com.lumen.player.core.db.JobStatus
import com.lumen.player.core.design.CoverImage
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Radii
import com.lumen.player.core.design.ScreenTitle
import com.lumen.player.core.design.SectionHeader
import com.lumen.player.core.design.Space
import com.lumen.player.core.design.trackThumb
import com.lumen.player.core.util.formatClock
import com.lumen.player.download.CandidateUi
import com.lumen.player.download.DownloadRequest
import com.lumen.player.download.Stage
import com.lumen.player.download.decodeCandidates
import com.lumen.player.download.decodeRequest
import com.lumen.player.download.youtubeVideoId
import com.lumen.player.download.sources.DzTrack
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.LocalPlayer
import com.lumen.player.feature.shell.bottomPadding

@Composable
fun AddScreen(onPlayTrack: (String) -> Unit = {}, viewModel: AddViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val inLibrary by viewModel.inLibrary.collectAsStateWithLifecycle()
    val album by viewModel.album.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var choosing by remember { mutableStateOf<DownloadJobEntity?>(null) }

    val active = jobs.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
    val recent = jobs.filter { it.status != JobStatus.QUEUED && it.status != JobStatus.RUNNING }
    // Deezer id -> its download job, so a song being downloaded shows a spinner instead of "+"
    val downloading = remember(active) {
        active.mapNotNull { job ->
            val request = runCatching { decodeRequest(job.request) }.getOrNull()
            (request as? DownloadRequest.DeezerPick)?.let { it.deezerId to job }
        }.toMap()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                ScreenTitle("Add music")
                Spacer(Modifier.height(16.dp))
                SearchField(query, { viewModel.query.value = it }, onDone = { focus.clearFocus() })
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(horizontal = Space.side), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        "Paste link",
                        onClick = { viewModel.paste(readClipboard(context)) },
                        style = PillStyle.OUTLINED,
                        icon = Icons.Rounded.ContentPaste,
                        modifier = Modifier.testTag("add_paste"),
                    )
                    Spacer(Modifier.width(16.dp))
                    if (!ready) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = LumenColors.paper3, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Preparing downloader…", style = LumenType.label, color = LumenColors.paper3)
                    }
                }
            }
        }

        if (results.loading) item { Text("Searching…", style = LumenType.label, color = LumenColors.paper3, modifier = Modifier.padding(Space.side)) }
        results.error?.let { error -> item { Text(error, style = LumenType.label, color = LumenColors.danger, modifier = Modifier.padding(Space.side)) } }

        if (results.albums.isNotEmpty()) {
            item { SectionHeader("Albums", Modifier.padding(top = 12.dp)) }
            items(results.albums, key = { "album_${it.id}" }) { album ->
                ResultRow(
                    cover = album.coverMedium,
                    title = album.title,
                    subtitle = listOfNotNull("Album", album.artist?.name, album.trackCount?.let { "$it songs" }).joinToString(" · "),
                    tag = "album_${album.id}",
                    onClick = { viewModel.openAlbum(album) },
                    trailing = {
                        Row(
                            Modifier.clip(CircleShape).clickable { viewModel.addAlbum(album) }.padding(horizontal = 10.dp, vertical = 8.dp).testTag("album_download_${album.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Download, "Download album", tint = LumenColors.paper, modifier = Modifier.size(20.dp))
                            album.trackCount?.let { Text(" $it", style = LumenType.numeric, color = LumenColors.paper2) }
                        }
                    },
                )
            }
        }
        if (results.tracks.isNotEmpty()) {
            item { SectionHeader("Songs", Modifier.padding(top = 12.dp)) }
            items(results.tracks, key = { "track_${it.id}" }) { track ->
                TrackResult(
                    track,
                    cover = track.album?.coverMedium,
                    subtitle = listOfNotNull(track.artist.name, track.album?.title).joinToString(" · "),
                    trackId = inLibrary[track.id],
                    job = downloading[track.id],
                    onAdd = { viewModel.add(track) },
                    onCancel = viewModel::cancel,
                    onPlay = onPlayTrack,
                )
            }
        }

        if (active.isNotEmpty()) {
            item { SectionHeader("Downloading", Modifier.padding(top = 20.dp)) }
            items(active, key = { "job_${it.id}" }) { job -> ActiveJob(job, onCancel = { viewModel.cancel(job.id) }) }
        }
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("Recent", Modifier.padding(top = 20.dp), action = "Clear finished", onAction = { viewModel.clearFinished() })
            }
            items(recent, key = { "job_${it.id}" }) { job ->
                RecentJob(
                    job,
                    onPlay = { job.trackId?.let(onPlayTrack) },
                    onRetry = { viewModel.retry(job.id) },
                    onChoose = { choosing = job },
                    onRemove = { viewModel.remove(job.id) },
                )
            }
        }
    }

    album?.let { sheet ->
        AlbumSheetView(
            sheet,
            inLibrary = inLibrary,
            downloading = downloading,
            onDismiss = viewModel::closeAlbum,
            onAdd = { track -> viewModel.add(track, sheet.album.coverMedium) },
            onAddAll = { viewModel.addAll(sheet.tracks, sheet.album.coverMedium, downloading.keys) },
            onCancel = viewModel::cancel,
            onPlay = onPlayTrack,
        )
    }

    choosing?.let { job ->
        ChooseAudioSheet(
            candidates = decodeCandidates(job.candidates),
            onDismiss = { choosing = null },
            onPick = { videoId ->
                viewModel.choose(job.id, videoId)
                choosing = null
            },
        )
    }
}

// a box for a YouTube link; "Use" sends the link's video id
@Composable
private fun LinkField(onPick: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val videoId = remember(text) { youtubeVideoId(text) }
    val context = LocalContext.current
    val accent = LocalAccent.current.accent
    Row(
        Modifier
            .padding(horizontal = Space.side)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(LumenColors.ink3)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Paste a YouTube link", style = LumenType.body, color = LumenColors.paper3)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = LumenType.body.copy(color = LumenColors.paper),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { videoId?.let(onPick) }),
                modifier = Modifier.fillMaxWidth().testTag("audio_link"),
            )
        }
        if (videoId != null) {
            Text(
                "Use",
                style = LumenType.body,
                color = accent,
                modifier = Modifier.clip(CircleShape).clickable { onPick(videoId) }.padding(horizontal = 14.dp, vertical = 10.dp).testTag("audio_link_use"),
            )
        } else {
            IconButton(onClick = { readClipboard(context)?.let { text = it } }, modifier = Modifier.testTag("audio_link_paste")) {
                Icon(Icons.Rounded.ContentPaste, "Paste", tint = LumenColors.paper2)
            }
        }
    }
    if (text.isNotBlank() && videoId == null) {
        Text(
            "That isn't a YouTube video link",
            style = LumenType.label,
            color = LumenColors.danger,
            modifier = Modifier.padding(horizontal = Space.side + 16.dp, vertical = 4.dp),
        )
    }
}

private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = Space.side)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(LumenColors.ink2)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = LumenColors.paper3)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Songs, artists, albums", style = LumenType.body, color = LumenColors.paper3)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = LumenType.body.copy(color = LumenColors.paper),
                cursorBrush = SolidColor(LocalAccent.current.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth().testTag("add_search"),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Clear", tint = LumenColors.paper3, modifier = Modifier.clip(CircleShape).clickable { onChange("") }.padding(4.dp))
        }
    }
}

@Composable
private fun ResultRow(
    cover: Any?,
    title: String,
    subtitle: String,
    meta: String? = null,
    tag: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .height(64.dp)
            .padding(start = Space.side, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(cover, title, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = LumenType.label, color = LumenColors.paper2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (meta != null) Text(meta, style = LumenType.numeric, color = LumenColors.paper3, modifier = Modifier.padding(horizontal = 4.dp))
        trailing()
    }
}

// a Deezer song: tap to download it, tap again once it is in the library to play it
@Composable
private fun TrackResult(
    track: DzTrack,
    cover: String?,
    subtitle: String,
    trackId: String?,
    job: DownloadJobEntity?,
    onAdd: () -> Unit,
    onCancel: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val player = LocalPlayer.current
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val current = trackId != null && nowPlaying?.id == trackId
    // the song already loaded in the player pauses and resumes instead of starting over
    fun playOrToggle(id: String) = if (current) player.connection.playPause() else onPlay(id)
    ResultRow(
        cover = cover,
        title = track.title,
        subtitle = subtitle,
        meta = formatClock(track.duration * 1000L),
        tag = "result_${track.id}",
        onClick = {
            when {
                trackId != null -> playOrToggle(trackId)
                job == null -> onAdd()
            }
        },
        trailing = {
            when {
                job != null -> {
                    // tapping the spinner stops the download
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable { onCancel(job.id) }.testTag("adding_${track.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        val accent = LocalAccent.current.accent
                        if (job.progress > 0) {
                            CircularProgressIndicator(
                                progress = { job.progress / 100f },
                                modifier = Modifier.size(22.dp),
                                color = accent,
                                trackColor = LumenColors.ink3,
                                strokeWidth = 2.5.dp,
                                gapSize = 0.dp,
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.5.dp)
                        }
                        Icon(Icons.Rounded.Stop, "Stop download", tint = LumenColors.paper, modifier = Modifier.size(12.dp))
                    }
                }
                trackId != null -> IconButton(onClick = { playOrToggle(trackId) }, modifier = Modifier.testTag("play_${track.id}")) {
                    if (current && isPlaying) Icon(Icons.Rounded.Pause, "Pause", tint = LocalAccent.current.accent)
                    else Icon(Icons.Rounded.PlayArrow, "Play", tint = LocalAccent.current.accent)
                }
                else -> IconButton(onClick = onAdd, modifier = Modifier.testTag("add_${track.id}")) {
                    Icon(Icons.Rounded.Add, "Add", tint = LumenColors.paper)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSheetView(
    sheet: AlbumSheet,
    inLibrary: Map<Long, String>,
    downloading: Map<Long, DownloadJobEntity>,
    onDismiss: () -> Unit,
    onAdd: (DzTrack) -> Unit,
    onAddAll: () -> Unit,
    onCancel: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val album = sheet.album
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LumenColors.ink2,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
    ) {
        LazyColumn(Modifier.testTag("album_sheet"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.padding(horizontal = Space.side).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(album.coverMedium, album.title, Modifier.size(88.dp), radius = Radii.card)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, style = LumenType.title, color = LumenColors.paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(album.artist?.name, album.releaseDate?.take(4), album.trackCount?.let { "$it songs" }).joinToString(" · "),
                            style = LumenType.label,
                            color = LumenColors.paper2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        PillButton(
                            "Download all",
                            onAddAll,
                            icon = Icons.Rounded.Download,
                            enabled = sheet.tracks.isNotEmpty(),
                            modifier = Modifier.testTag("album_download_all"),
                        )
                    }
                }
            }
            // placeholder rows while loading keep the sheet at its final height, so it opens in one move
            if (sheet.loading) items(album.trackCount ?: 6) { PlaceholderRow() }
            sheet.error?.let { error -> item { Text(error, style = LumenType.label, color = LumenColors.danger, modifier = Modifier.padding(Space.side)) } }
            items(sheet.tracks, key = { "album_track_${it.id}" }) { track ->
                TrackResult(
                    track,
                    cover = album.coverMedium,
                    subtitle = track.artist.name,
                    trackId = inLibrary[track.id],
                    job = downloading[track.id],
                    onAdd = { onAdd(track) },
                    onCancel = onCancel,
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderRow() {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = Space.side, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radii.listCover)).background(LumenColors.ink3))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(Modifier.width(160.dp).height(12.dp).clip(CircleShape).background(LumenColors.ink3))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(90.dp).height(10.dp).clip(CircleShape).background(LumenColors.ink3))
        }
    }
}

@Composable
private fun ActiveJob(job: DownloadJobEntity, onCancel: () -> Unit) {
    val accent = LocalAccent.current.accent
    Row(
        Modifier
            .padding(horizontal = Space.side, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(LumenColors.ink2)
            .padding(12.dp)
            .testTag("job_${job.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(job.coverUrl, job.title, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(job.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Stage.label(job.stage), style = LumenType.label, color = LumenColors.paper2)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { job.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = accent,
                trackColor = LumenColors.ink3,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.testTag("cancel_${job.id}")) {
            Icon(Icons.Rounded.Close, "Cancel", tint = LumenColors.paper2)
        }
    }
}

@Composable
private fun RecentJob(job: DownloadJobEntity, onPlay: () -> Unit, onRetry: () -> Unit, onChoose: () -> Unit, onRemove: () -> Unit) {
    val subtitle = when (job.status) {
        JobStatus.DONE -> job.error ?: job.artist
        JobStatus.CANCELLED -> "Cancelled"
        else -> job.error ?: job.artist
    }
    val color = when (job.status) {
        JobStatus.FAILED -> LumenColors.danger
        JobStatus.NEEDS_CHOICE -> LocalAccent.current.accent
        else -> LumenColors.paper2
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = job.status == JobStatus.DONE && job.trackId != null, onClick = onPlay)
            .testTag("recent_${job.id}")
            .height(64.dp)
            .padding(start = Space.side, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = job.trackId?.let { trackThumb(it, true) } ?: job.coverUrl
        CoverImage(cover, job.title, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(job.title, style = LumenType.body, color = LumenColors.paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = LumenType.label, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (job.status) {
            JobStatus.FAILED, JobStatus.CANCELLED -> PillButton("Retry", onRetry, style = PillStyle.OUTLINED, modifier = Modifier.testTag("retry_${job.id}"))
            JobStatus.NEEDS_CHOICE -> {
                PillButton("Choose audio", onChoose, style = PillStyle.EMBER, modifier = Modifier.testTag("choose_${job.id}"))
                IconButton(onClick = onRemove, modifier = Modifier.testTag("remove_${job.id}")) {
                    Icon(Icons.Rounded.Close, "Remove", tint = LumenColors.paper3)
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// candidates == null while YouTube is still being searched; a pasted YouTube link can be used instead
fun ChooseAudioSheet(candidates: List<CandidateUi>?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LumenColors.ink2,
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
    ) {
        Text("Choose the right audio", style = LumenType.title, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side))
        Spacer(Modifier.height(12.dp))
        LinkField(onPick)
        Spacer(Modifier.height(8.dp))
        Column(
            // room for 6 results from the start, so the sheet does not grow (and move rows under a tap) when they load
            Modifier.heightIn(min = 86.dp * 6).verticalScroll(rememberScrollState()).imePadding().padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                candidates == null -> Text("Searching YouTube…", style = LumenType.label, color = LumenColors.paper3, modifier = Modifier.padding(Space.side))
                candidates.isEmpty() -> Text("No results. Paste a link above.", style = LumenType.label, color = LumenColors.paper3, modifier = Modifier.padding(Space.side))
            }
            candidates.orEmpty().forEach { candidate ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(candidate.videoId) }
                        .testTag("candidate_${candidate.videoId}")
                        .padding(horizontal = Space.side, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(candidate.thumbnail, candidate.title, Modifier.width(96.dp).height(54.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(candidate.title, style = LumenType.body, color = LumenColors.paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(candidate.channel, candidate.durationSec?.let { formatClock(it * 1000L) }).joinToString(" · "),
                            style = LumenType.label,
                            color = LumenColors.paper2,
                        )
                    }
                }
            }
        }
    }
}
