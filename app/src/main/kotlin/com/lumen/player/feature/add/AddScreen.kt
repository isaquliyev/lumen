package com.lumen.player.feature.add

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
fun AddScreen() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        ScreenTitle("Add music")
        EmptyState("Find a song.", "Search and downloads arrive in the next step.")
    }
}
