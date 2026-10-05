package com.lumen.player.feature.pulse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.ScreenTitle

@Composable
fun PulseScreen() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        ScreenTitle("Pulse")
        EmptyState("Not enough listening yet.", "Play some music and your stats will appear here, live.")
    }
}
