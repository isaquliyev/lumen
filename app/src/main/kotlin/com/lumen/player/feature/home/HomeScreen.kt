package com.lumen.player.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Search
import com.lumen.player.feature.focus.FocusPill
import com.lumen.player.feature.focus.LocalFocusActions
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lumen.player.core.design.EmptyState
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.Space
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun greeting(hour: Int = LocalTime.now().hour) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
fun HomeHeader(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.side, end = 8.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                style = LumenType.overline,
                color = LumenColors.paper3,
            )
            Text(greeting(), style = LumenType.display, color = LumenColors.paper)
        }
        val focusActions = LocalFocusActions.current
        IconButton(onClick = { focusActions.sheetOpen.value = true }, modifier = Modifier.testTag("home_focus")) {
            Icon(Icons.Rounded.CenterFocusStrong, "Artist Focus", tint = LumenColors.paper)
        }
        IconButton(onClick = onOpenSearch, modifier = Modifier.testTag("home_search")) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = LumenColors.paper)
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("home_settings")) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = LumenColors.paper)
        }
    }
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onOpenSearch: () -> Unit, onOpenAdd: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HomeHeader(onOpenSearch, onOpenSettings)
        FocusPill(Modifier.padding(start = Space.side, top = 8.dp))
        EmptyState(
            serifLine = "Your library is quiet.",
            body = "Songs you add live on this phone, in your Lumen folder.",
            action = "Add your first song",
            onAction = onOpenAdd,
            modifier = Modifier.padding(top = 80.dp),
        )
    }
}
