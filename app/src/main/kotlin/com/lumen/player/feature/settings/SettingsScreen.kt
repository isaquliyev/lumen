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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.lumen.player.core.design.Space
import com.lumen.player.data.StorageMode
import com.lumen.player.feature.shell.LocalBottomSpace
import com.lumen.player.feature.shell.bottomPadding

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding(LocalBottomSpace.current)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp).testTag("settings_back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = LumenColors.paper)
                }
                Text("Settings", style = LumenType.displayLarge, color = LumenColors.paper, modifier = Modifier.padding(horizontal = Space.side))
            }
        }
        item { SettingsSection("Storage") }
        item { InfoRow("Library folder", viewModel.paths.root.absolutePath) }
        if (settings.storageMode == StorageMode.PRIVATE) {
            item { InfoRow("Private storage", "Your library will be deleted if you uninstall Lumen.", warning = true) }
        }
        item { SettingsSection("Appearance") }
        item {
            SwitchRow("Reduce motion", "Turns off ambient and staggered animations.", settings.reduceMotion, "settings_reduce_motion") {
                viewModel.setReduceMotion(it)
            }
        }
        item { SettingsSection("About") }
        item { InfoRow("Lumen ${BuildConfig.VERSION_NAME}", "Personal use only. Fonts: Fraunces and Manrope (SIL Open Font License).") }
    }
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
