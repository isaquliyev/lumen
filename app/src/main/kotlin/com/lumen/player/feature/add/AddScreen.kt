package com.lumen.player.feature.add

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import com.lumen.player.download.Stage
import com.lumen.player.download.decodeCandidates
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.bottomPadding

@Composable
fun AddScreen(onPlayTrack: (String) -> Unit = {}, viewModel: AddViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val inLibrary by viewModel.inLibrary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var choosing by remember { mutableStateOf<DownloadJobEntity?>(null) }

    val active = jobs.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }
    val recent = jobs.filter { it.status != JobStatus.QUEUED && it.status != JobStatus.RUNNING }

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
                    subtitle = album.artist?.name.orEmpty(),
                    trailing = { PillButton("Download album${album.trackCount?.let { " ($it)" } ?: ""}", { viewModel.addAlbum(album) }, style = PillStyle.OUTLINED) },
                )
            }
        }
        if (results.tracks.isNotEmpty()) {
            item { SectionHeader("Songs", Modifier.padding(top = 12.dp)) }
            items(results.tracks, key = { "track_${it.id}" }) { track ->
                val owned = track.id in inLibrary
                ResultRow(
                    cover = track.album?.coverMedium,
                    title = track.title,
                    subtitle = listOfNotNull(track.artist.name, track.album?.title).joinToString(" · "),
                    meta = formatClock(track.duration * 1000L),
                    tag = "result_${track.id}",
                    trailing = {
                        if (owned) {
                            Icon(Icons.Rounded.Check, "In library", tint = LumenColors.positive, modifier = Modifier.padding(12.dp))
                        } else {
                            IconButton(onClick = { viewModel.add(track) }, modifier = Modifier.testTag("add_${track.id}")) {
                                Icon(Icons.Rounded.Add, "Add", tint = LumenColors.paper)
                            }
                        }
                    },
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
                )
            }
        }
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
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
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
private fun RecentJob(job: DownloadJobEntity, onPlay: () -> Unit, onRetry: () -> Unit, onChoose: () -> Unit) {
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
            JobStatus.NEEDS_CHOICE -> PillButton("Choose audio", onChoose, style = PillStyle.EMBER, modifier = Modifier.testTag("choose_${job.id}"))
            JobStatus.DONE -> Icon(Icons.Rounded.Check, null, tint = LumenColors.positive)
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseAudioSheet(candidates: List<CandidateUi>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LumenColors.ink2, shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet)) {
        Text("Choose the right audio", style = LumenType.title, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side))
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            candidates.forEach { candidate ->
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
