package com.lumen.player.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class StorageMode { SHARED, PRIVATE }

data class Settings(
    val onboardingDone: Boolean = false,
    val storageMode: StorageMode? = null,
    val reduceMotion: Boolean = false,
    val normalize: Boolean = true,
    val resumeOnLaunch: Boolean = true,
    val wifiOnly: Boolean = false,
    val parallelDownloads: Int = 2,
    val preferM4a: Boolean = true,
    val hideFromOtherApps: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val storageMode = stringPreferencesKey("storage_mode")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val normalize = booleanPreferencesKey("normalize")
        val resumeOnLaunch = booleanPreferencesKey("resume_on_launch")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val parallelDownloads = intPreferencesKey("parallel_downloads")
        val preferM4a = booleanPreferencesKey("prefer_m4a")
        val hideFromOtherApps = booleanPreferencesKey("hide_from_other_apps")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            storageMode = p[Keys.storageMode]?.let { StorageMode.valueOf(it) },
            reduceMotion = p[Keys.reduceMotion] ?: false,
            normalize = p[Keys.normalize] ?: true,
            resumeOnLaunch = p[Keys.resumeOnLaunch] ?: true,
            wifiOnly = p[Keys.wifiOnly] ?: false,
            parallelDownloads = p[Keys.parallelDownloads] ?: 2,
            preferM4a = p[Keys.preferM4a] ?: true,
            hideFromOtherApps = p[Keys.hideFromOtherApps] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingDone(value: Boolean) = store.edit { it[Keys.onboardingDone] = value }
    suspend fun setStorageMode(mode: StorageMode) = store.edit { it[Keys.storageMode] = mode.name }
    suspend fun setReduceMotion(value: Boolean) = store.edit { it[Keys.reduceMotion] = value }
    suspend fun setNormalize(value: Boolean) = store.edit { it[Keys.normalize] = value }
    suspend fun setResumeOnLaunch(value: Boolean) = store.edit { it[Keys.resumeOnLaunch] = value }
    suspend fun setWifiOnly(value: Boolean) = store.edit { it[Keys.wifiOnly] = value }
    suspend fun setParallelDownloads(value: Int) = store.edit { it[Keys.parallelDownloads] = value.coerceIn(1, 3) }
    suspend fun setPreferM4a(value: Boolean) = store.edit { it[Keys.preferM4a] = value }
    suspend fun setHideFromOtherApps(value: Boolean) = store.edit { it[Keys.hideFromOtherApps] = value }
}
