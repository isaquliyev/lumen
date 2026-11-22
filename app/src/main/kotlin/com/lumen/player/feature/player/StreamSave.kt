package com.lumen.player.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lumen.player.core.design.LocalAccent
import com.lumen.player.core.design.LumenColors

enum class StreamSave { NONE, SAVING, SAVED }

// for a streamed song: download it, a spinner while it downloads, a check once it is in the library
@Composable
fun StreamSaveButton(state: StreamSave, onSave: () -> Unit, modifier: Modifier = Modifier, tint: Color = LumenColors.paper) {
    val accent = LocalAccent.current.accent
    when (state) {
        StreamSave.NONE -> IconButton(onClick = onSave, modifier = modifier.testTag("stream_save")) {
            Icon(Icons.Rounded.Download, "Download", tint = tint)
        }
        StreamSave.SAVING -> Box(modifier.size(48.dp).testTag("stream_saving"), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
        }
        StreamSave.SAVED -> Box(modifier.size(48.dp).testTag("stream_saved"), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = accent)
        }
    }
}
