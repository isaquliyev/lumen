package com.lumen.player.feature.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.lumen.player.core.design.LumenColors
import com.lumen.player.core.design.LumenType
import com.lumen.player.core.design.PillButton
import com.lumen.player.core.design.PillStyle
import com.lumen.player.core.design.Space
import com.lumen.player.data.StorageMode

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(state.step) {
        if (state.step == OnboardingStep.STORAGE && Environment.isExternalStorageManager()) {
            viewModel.chooseStorage(StorageMode.SHARED)
        }
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.next()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LumenColors.ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding",
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> Page(
                    hero = "Lumen",
                    body = "Your music. On your phone. Nowhere else.",
                ) {
                    PillButton("Continue", viewModel::next, Modifier.fillMaxWidth().testTag("onboarding_continue"))
                }

                OnboardingStep.STORAGE -> Page(
                    title = "One folder for everything",
                    body = "Lumen keeps your songs, covers, lyrics and data in one folder on your phone. " +
                        "It stays there even if you reinstall the app, and you can copy it to a computer.",
                    extra = { FolderTree() },
                ) {
                    PillButton("Allow access", {
                        if (Environment.isExternalStorageManager()) viewModel.chooseStorage(StorageMode.SHARED)
                        else openAllFilesAccess(context)
                    }, Modifier.fillMaxWidth().testTag("onboarding_allow_storage"))
                    Spacer(Modifier.height(10.dp))
                    PillButton(
                        "Use private storage instead",
                        { viewModel.chooseStorage(StorageMode.PRIVATE) },
                        Modifier.fillMaxWidth().testTag("onboarding_private_storage"),
                        style = PillStyle.OUTLINED,
                    )
                }

                OnboardingStep.RESTORE -> Page(
                    title = "Welcome back",
                    body = "We found a backup in your Lumen folder: ${state.restoreSummary}.",
                ) {
                    RestoreActions(viewModel, state)
                }

                OnboardingStep.NOTIFICATIONS -> Page(
                    title = "Stay in control",
                    body = "Notifications show playback controls and download progress.",
                ) {
                    PillButton("Allow notifications", {
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else viewModel.next()
                    }, Modifier.fillMaxWidth().testTag("onboarding_allow_notifications"))
                    Spacer(Modifier.height(10.dp))
                    PillButton("Not now", viewModel::next, Modifier.fillMaxWidth().testTag("onboarding_skip"), style = PillStyle.OUTLINED)
                }

                OnboardingStep.BACKGROUND -> Page(
                    title = "Keep the music playing",
                    body = "Your phone may stop Lumen in the background. Turn on Autostart for Lumen, " +
                        "and set Battery saver to No restrictions.",
                ) {
                    PillButton("Allow background playback", { requestIgnoreBatteryOptimizations(context) },
                        Modifier.fillMaxWidth().testTag("onboarding_battery"), style = PillStyle.OUTLINED)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Open Autostart settings", { openMiuiAutostart(context) },
                        Modifier.fillMaxWidth().testTag("onboarding_autostart"), style = PillStyle.OUTLINED)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Continue", viewModel::next, Modifier.fillMaxWidth().testTag("onboarding_continue"))
                }

                OnboardingStep.DONE -> Page(
                    title = "You're all set",
                    body = if (state.lowOnSpace) "Your phone has less than 1 GB free. Songs need space, so free some up soon."
                    else "Your Lumen folder is ready. Let's add some music.",
                ) {
                    PillButton("Start listening", viewModel::finish, Modifier.fillMaxWidth().testTag("onboarding_finish"))
                }
            }
        }
    }
}

@Composable
private fun RestoreActions(viewModel: OnboardingViewModel, state: OnboardingState) {
    if (state.restoreError != null) {
        Text(state.restoreError, style = LumenType.label, color = LumenColors.danger)
        Spacer(Modifier.height(12.dp))
    }
    PillButton(
        if (state.restoring) "Restoring…" else "Restore your library",
        viewModel::restore,
        Modifier.fillMaxWidth().testTag("onboarding_restore"),
        enabled = !state.restoring,
    )
    Spacer(Modifier.height(10.dp))
    PillButton("Start fresh", viewModel::next, Modifier.fillMaxWidth().testTag("onboarding_fresh"), style = PillStyle.OUTLINED)
}

@Composable
private fun Page(
    hero: String? = null,
    title: String? = null,
    body: String,
    extra: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Space.side, vertical = 24.dp)) {
        Spacer(Modifier.weight(1f))
        if (hero != null) Text(hero, style = LumenType.displayHero, color = LumenColors.paper)
        if (title != null) Text(title, style = LumenType.displayLarge, color = LumenColors.paper)
        Spacer(Modifier.height(16.dp))
        Text(body, style = LumenType.body, color = LumenColors.paper2)
        Spacer(Modifier.height(24.dp))
        extra()
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun FolderTree() {
    val lines = listOf(
        "Lumen/" to "",
        "  audio/" to "your songs",
        "  covers/" to "album art",
        "  artists/" to "artist photos",
        "  lyrics/" to "synced lyrics",
        "  playlists/" to "playlist covers",
        "  data/" to "library backup",
        "  exports/" to "shared recaps",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LumenColors.ink2)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { (name, note) ->
            Row {
                Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(if (note.isEmpty()) LumenColors.ember else LumenColors.paper3))
                Spacer(Modifier.width(10.dp))
                Text(name, style = LumenType.numeric, color = LumenColors.paper, modifier = Modifier.width(110.dp))
                Text(note, style = LumenType.label, color = LumenColors.paper3)
            }
        }
    }
}

private fun openAllFilesAccess(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
}

private fun openMiuiAutostart(context: Context) {
    val intent = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    )
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            }
        }
}
