package com.lumen.player.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.ScreenTitle
import com.lumen.player.core.design.SegmentedTabs

@Composable
fun LibraryScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp)) {
        ScreenTitle("Library")
        Spacer(Modifier.height(12.dp))
        SegmentedTabs(listOf("Songs", "Playlists", "Artists", "Albums"), tab, { tab = it })
        EmptyState("Nothing here yet.", "Songs you download will show up here.")
    }
}
