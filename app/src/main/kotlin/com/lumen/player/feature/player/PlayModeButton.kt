package com.lumen.player.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.db.PlayContext
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.feature.shell.LocalPlayer

// switches between shuffled and ordered play for the whole app; a queue playing from this list follows it
@Composable
fun PlayModeButton(context: PlayContext, contextId: String?, modifier: Modifier = Modifier, activeStyle: PillStyle = PillStyle.EMBER) {
    val player = LocalPlayer.current
    val shuffled by player.shuffleMode.collectAsStateWithLifecycle()
    PillButton(
        if (shuffled) "Shuffled" else "Ordered",
        { player.setShuffleMode(!shuffled, context, contextId) },
        style = if (shuffled) activeStyle else PillStyle.OUTLINED,
        icon = if (shuffled) Icons.Rounded.Shuffle else Icons.AutoMirrored.Rounded.Sort,
        modifier = modifier.testTag("play_mode"),
    )
}
