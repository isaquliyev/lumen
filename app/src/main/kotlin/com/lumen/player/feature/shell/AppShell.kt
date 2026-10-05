package com.lumen.player.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lumen.player.core.TabRequest
import com.lumen.player.core.design.LocalAccent
import kotlinx.coroutines.launch
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.Durations
import com.lumen.player.core.design.lumenTween
import com.lumen.player.feature.player.MiniPlayer
import com.lumen.player.feature.album.AlbumScreen
import com.lumen.player.feature.artist.ArtistScreen
import com.lumen.player.feature.focus.FocusSheet
import com.lumen.player.feature.focus.FocusViewModel
import com.lumen.player.feature.focus.LocalFocusActions
import com.lumen.player.feature.library.TrackMenuHost
import com.lumen.player.feature.search.SearchScreen
import com.lumen.player.feature.playlist.AddSongsScreen
import com.lumen.player.feature.playlist.AddToPlaylistHost
import com.lumen.player.feature.playlist.PlaylistScreen
import com.lumen.player.feature.playlist.SmartPlaylistScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.lumen.player.feature.player.NowPlaying
import com.lumen.player.feature.player.PlayerViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.design.LumenType
import com.lumen.player.feature.add.AddScreen
import com.lumen.player.feature.home.HomeScreen
import com.lumen.player.feature.library.LibraryScreen
import com.lumen.player.feature.pulse.PulseScreen
import com.lumen.player.feature.settings.SettingsScreen

// space that scrolling content leaves at the bottom for the nav bar and mini player
val LocalBottomSpace = staticCompositionLocalOf { 0.dp }

private enum class Tab(val label: String, val icon: ImageVector, val route: Any) {
    HOME("Home", Icons.Rounded.Home, HomeRoute),
    LIBRARY("Library", Icons.Rounded.LibraryMusic, LibraryRoute),
    PULSE("Pulse", Icons.Rounded.GraphicEq, PulseRoute),
    ADD("Add", Icons.Rounded.Add, AddRoute),
}

fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// the app's player for every screen (play, play next, like)
val LocalPlayer = staticCompositionLocalOf<PlayerViewModel> { error("PlayerViewModel not provided") }

@Composable
fun AppShell(
    viewModel: ShellViewModel = hiltViewModel(),
    player: PlayerViewModel = hiltViewModel(),
    focus: FocusViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bottomSpace: Dp = navInset + 65.dp + if (nowPlaying != null) 72.dp else 0.dp
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.messages.collect { message ->
            launch {
                snackbars.currentSnackbarData?.dismiss()
                val result = snackbars.showSnackbar(message.text, message.action, duration = if (message.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.tabs.collect { tab ->
            expanded = false
            navController.openTab(
                when (tab) {
                    TabRequest.HOME -> HomeRoute
                    TabRequest.LIBRARY -> LibraryRoute
                    TabRequest.PULSE -> PulseRoute
                    TabRequest.ADD -> AddRoute
                }
            )
            viewModel.events.consumeTab()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.expandPlayer.collect {
            expanded = true
            viewModel.events.consumePlayer()
        }
    }
    BackHandler(enabled = expanded) { expanded = false }

    val navigator = remember(navController) {
        object : Navigator {
            override fun open(route: Any) {
                expanded = false
                when (route) {
                    HomeRoute, LibraryRoute, PulseRoute, AddRoute -> navController.openTab(route)
                    else -> navController.navigate(route) { launchSingleTop = true }
                }
            }

            override fun back() {
                navController.popBackStack()
            }
        }
    }

    CompositionLocalProvider(
        LocalBottomSpace provides bottomSpace,
        LocalPlayer provides player,
        LocalNav provides navigator,
        LocalFocusActions provides focus,
    ) {
      AddToPlaylistHost {
      TrackMenuHost {
        Box(Modifier.fillMaxSize().background(LumenColors.ink)) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(lumenTween(Durations.MEDIUM)) },
                exitTransition = { fadeOut(lumenTween(Durations.SHORT)) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenSearch = { navController.navigate(SearchRoute) },
                        onOpenAdd = { navController.openTab(AddRoute) },
                    )
                }
                composable<LibraryRoute> { LibraryScreen() }
                composable<PulseRoute> { PulseScreen() }
                composable<AddRoute> { AddScreen(onPlayTrack = { id -> player.playIds(listOf(id)) }) }
                composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
                composable<SearchRoute> { SearchScreen() }
                composable<ArtistRoute> { ArtistScreen() }
                composable<AlbumRoute> { AlbumScreen() }
                composable<PlaylistRoute> { PlaylistScreen() }
                composable<SmartPlaylistRoute> { SmartPlaylistScreen() }
                composable<AddSongsRoute> { AddSongsScreen() }
            }
            Column(Modifier.align(Alignment.BottomCenter)) {
                nowPlaying?.let { track ->
                    MiniPlayer(track, player, onExpand = { expanded = true }, modifier = Modifier.padding(bottom = 8.dp))
                }
                BottomNav(navController)
            }
            SnackbarHost(
                snackbars,
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace + 8.dp),
            ) { data ->
                Snackbar(
                    data,
                    containerColor = LumenColors.ink3,
                    contentColor = LumenColors.paper,
                    actionColor = LocalAccent.current.accent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded && nowPlaying != null,
                enter = slideInVertically(lumenTween(Durations.LONG)) { it },
                exit = slideOutVertically(lumenTween(Durations.MEDIUM)) { it },
            ) {
                nowPlaying?.let { NowPlaying(it, player, onCollapse = { expanded = false }) }
            }
            FocusSheet()
        }
        }
      }
    }
}


@Composable
private fun BottomNav(navController: NavHostController, modifier: Modifier = Modifier) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    Column(modifier.fillMaxWidth().background(LumenColors.ink)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LumenColors.line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val active = destination?.hasRoute(tab.route::class) == true
                val color = if (active) LumenColors.paper else LumenColors.paper3
                Column(
                    Modifier
                        .weight(1f)
                        .testTag("nav_${tab.name.lowercase()}")
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            navController.openTab(tab.route)
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (active) LumenColors.ember else LumenColors.ink))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = LumenType.overline, color = color)
                }
            }
        }
    }
}

fun bottomPadding(space: Dp) = PaddingValues(bottom = space + 24.dp)
