package com.lumen.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenTheme
import com.lumen.player.feature.onboarding.OnboardingScreen
import com.lumen.player.feature.shell.AppShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LumenTheme(reduceMotion = settings?.reduceMotion ?: false) {
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
