package com.lumen.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.BuildConfig
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.SegmentedTabs
import com.lumen.player.core.design.Space
import com.lumen.player.core.util.formatBytes
import com.lumen.player.data.StorageMode
import com.lumen.player.data.ThemeMode
import com.lumen.player.feature.library.ConfirmDialog
import com.lumen.player.feature.pulse.HowItsCounted
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.bottomPadding
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackup.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val free by viewModel.free.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val version by viewModel.ytdlpVersion.collectAsStateWithLifecycle()
    var restoring by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var resetStep by remember { mutableIntStateOf(0) }
    var infoOpen by remember { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp).testTag("settings_back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LumenColors.paper)
                }
                Text("Settings", style = LumenType.displayLarge, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side))
                busy?.let { Text(it, style = LumenType.label, color = LocalAccent.current.accent, modifier = Modifier.padding(horizontal = Space.side, vertical = 4.dp)) }
            }
        }

        item { SettingsSection("Storage") }
        item { InfoRow("Library folder", viewModel.paths.root.absolutePath) }
        if (settings.storageMode == StorageMode.PRIVATE) {
            item { InfoRow("Private storage", "Your library will be deleted if you uninstall Lumen.", warning = true) }
        }
        item {
            val used = usage.sumOf { it.bytes }
            InfoRow(
                "Space used: ${formatBytes(used)} · free: ${formatBytes(free)}",
                usage.filter { it.files > 0 }.joinToString(" · ") { "${it.name} ${formatBytes(it.bytes)}" }.ifEmpty { null },
                tag = "settings_usage",
            )
        }
        item {
            SwitchRow("Hide songs from other apps", "Keeps Lumen's audio out of Gallery and other players.", settings.hideFromOtherApps, "settings_nomedia") {
                viewModel.setHidden(it)
            }
        }
        item {
            InfoRow(
                "Back up now",
                lastBackup?.let { "Last backup: ${dateFormat.format(Date(it))}" } ?: "Copies your library data to Lumen/data",
                tag = "settings_backup",
                onClick = { viewModel.backUpNow() },
            )
        }
        item { InfoRow("Restore from backup", "Replace the library data with a backup from Lumen/data", tag = "settings_restore", onClick = { restoring = true }) }
        item {
            InfoRow("Check library", "Finds missing songs and leftover files", tag = "settings_check", onClick = { viewModel.checkLibrary() })
        }
        check?.takeIf { !it.clean }?.let { result ->
            item {
                InfoRow(
                    "Fix ${result.missing.size} missing · ${result.orphans.size} orphan",
                    (result.missing.map { it.title } + result.orphans.map { it.name }).take(6).joinToString(", "),
                    warning = true,
                    tag = "settings_fix",
                    onClick = { viewModel.fixLibrary() },
                )
            }
        }

        item { SettingsSection("Playback") }
        item { SwitchRow("Volume normalisation", "Makes loud songs quieter and quiet songs louder, so all songs play at a similar volume.", settings.normalize, "settings_normalize") { viewModel.setNormalize(it) } }
        item { SwitchRow("Stereo audio", "Off = mono: the same sound in both ears.", settings.stereo, "settings_stereo") { viewModel.setStereo(it) } }
        item { SwitchRow("Resume on launch", "Bring back the last queue, paused.", settings.resumeOnLaunch, "settings_resume") { viewModel.setResumeOnLaunch(it) } }

        item { SettingsSection("Downloads") }
        item { SwitchRow("Wi-Fi only", "Wait for Wi-Fi before downloading.", settings.wifiOnly, "settings_wifi") { viewModel.setWifiOnly(it) } }
        item { InfoRow("Update downloader", "yt-dlp ${version ?: "…"}", tag = "settings_ytdlp", onClick = { viewModel.updateYtDlp() }) }

        item { SettingsSection("Listening data") }
        item { InfoRow("How it's counted", tag = "settings_how", onClick = { infoOpen = true }) }
        item { InfoRow("Reset statistics", "Deletes every play. Songs and playlists stay.", warning = true, tag = "settings_reset", onClick = { resetStep = 1 }) }

        item { SettingsSection("Appearance") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.side, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", style = LumenType.body, color = LumenColors.paper, modifier = Modifier.weight(1f))
                SegmentedTabs(listOf("System", "Light", "Dark"), settings.theme.ordinal, { viewModel.setTheme(ThemeMode.entries[it]) })
            }
        }
        item {
            SwitchRow("Reduce motion", "Turns off ambient and staggered animations.", settings.reduceMotion, "settings_reduce_motion") {
                viewModel.setReduceMotion(it)
            }
        }

        if (BuildConfig.DEBUG) {
            item { SettingsSection("Debug") }
            item { InfoRow("Seed 1,500 fake songs", tag = "debug_seed_songs", onClick = { viewModel.seedSongs() }) }
            item { InfoRow("Seed 1 year of fake plays", tag = "debug_seed_plays", onClick = { viewModel.seedPlays() }) }
            item { InfoRow("Remove seeded data", tag = "debug_remove_seed", onClick = { viewModel.removeSeeded() }) }
            item { InfoRow("Export logs", "Saves a log file to Lumen/exports", tag = "debug_export_logs", onClick = { viewModel.exportLogs() }) }
        }
    }

    if (restoring) {
        AlertDialog(
            onDismissRequest = { restoring = false },
            containerColor = LumenColors.ink2,
            title = { Text("Restore from backup", style = LumenType.title, color = LumenColors.paper) },
            text = {
                Column {
                    val files = viewModel.backups()
                    if (files.isEmpty()) Text("No backups yet.", color = LumenColors.paper2)
                    files.forEach { file ->
                        Text(
                            "${if (file.name == "lumen.db") "Latest" else "Previous"} · ${dateFormat.format(Date(file.lastModified()))}",
                            style = LumenType.body,
                            color = LumenColors.paper,
                            modifier = Modifier.fillMaxWidth().clickable { restoreFile = file; restoring = false }.padding(vertical = 12.dp).testTag("restore_${file.name}"),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restoring = false }) { Text("Cancel", color = LumenColors.paper2) } },
        )
    }
    restoreFile?.let { file ->
        ConfirmDialog(
            "Restore this backup?",
            "Your current library data is replaced and Lumen restarts. Song files stay where they are.",
            "Restore",
            onDismiss = { restoreFile = null },
        ) {
            restoreFile = null
            viewModel.restore(file)
        }
    }
    when (resetStep) {
        1 -> ConfirmDialog("Reset statistics?", "Every play is deleted. Pulse starts from zero.", "Continue", onDismiss = { resetStep = 0 }) { resetStep = 2 }
        2 -> ConfirmDialog("Are you sure?", "This can't be undone.", "Reset", onDismiss = { resetStep = 0 }) {
            resetStep = 0
            viewModel.resetStats()
        }
    }
    if (infoOpen) HowItsCounted { infoOpen = false }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = LumenType.overline,
        color = LumenColors.paper3,
        modifier = Modifier.padding(start = Space.side, end = Space.side, top = 32.dp, bottom = 8.dp),
    )
}

@Composable
fun InfoRow(title: String, body: String? = null, warning: Boolean = false, tag: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.side, vertical = 12.dp),
    ) {
        Text(title, style = LumenType.body, color = if (warning) LumenColors.danger else LumenColors.paper)
        if (body != null) {
            Spacer(Modifier.height(2.dp))
            Text(body, style = LumenType.label, color = LumenColors.paper2)
        }
    }
}

@Composable
fun SwitchRow(title: String, body: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current.accent
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable { onChange(!checked) }
            .padding(horizontal = Space.side, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LumenType.body, color = LumenColors.paper)
            if (body != null) Text(body, style = LumenType.label, color = LumenColors.paper2)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LumenColors.ink,
                checkedTrackColor = accent,
                uncheckedThumbColor = LumenColors.paper3,
                uncheckedTrackColor = LumenColors.ink3,
                uncheckedBorderColor = LumenColors.line,
            ),
        )
    }
}
