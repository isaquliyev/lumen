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

@Composable
fun AppShell(viewModel: ShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace: Dp = navInset + 65.dp
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.messages.collect { message ->
            launch {
                snackbars.currentSnackbarData?.dismiss()
                val result = snackbars.showSnackbar(message.text, message.action, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.tabs.collect { tab ->
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

    CompositionLocalProvider(LocalBottomSpace provides bottomSpace) {
        Box(Modifier.fillMaxSize().background(LumenColors.ink)) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize(),
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
                composable<AddRoute> { AddScreen() }
                composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
                composable<SearchRoute> { }
            }
            BottomNav(navController, Modifier.align(Alignment.BottomCenter))
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
