package com.lumen.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.design.LocalPaths
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenTheme
import com.lumen.player.core.storage.LumenPaths
import com.lumen.player.data.ThemeMode
import com.lumen.player.feature.onboarding.OnboardingScreen
import com.lumen.player.feature.shell.AppShell
import com.lumen.player.playback.EXTRA_OPEN_PLAYER
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var paths: LumenPaths

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings?.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
                else -> true
            }
            LumenColors.light = !dark
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LumenTheme(reduceMotion = settings?.reduceMotion ?: false) {
                CompositionLocalProvider(LocalPaths provides paths) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LumenColors.ink)
                            .semantics { testTagsAsResourceId = true },
                    ) {
                        val current = settings
                        when {
                            current == null -> Unit
                            !current.onboardingDone -> OnboardingScreen()
                            else -> AppShell()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) viewModel.openPlayer()
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::handleShared)
        }
    }
}
