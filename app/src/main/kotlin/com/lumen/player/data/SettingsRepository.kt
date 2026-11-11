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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val onboardingDone: Boolean = false,
    val storageMode: StorageMode? = null,
    val reduceMotion: Boolean = false,
    val normalize: Boolean = true,
    val resumeOnLaunch: Boolean = true,
    val wifiOnly: Boolean = false,
    val hideFromOtherApps: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val stereo: Boolean = true,
    val playlistGrid: Boolean = false,
    val songSort: String? = null,
    val artistSort: String? = null,
    val libraryTab: Int = 0,
    val songsShuffle: Boolean = false,
    val pulseRange: String? = null,
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
        val hideFromOtherApps = booleanPreferencesKey("hide_from_other_apps")
        val theme = stringPreferencesKey("theme")
        val stereo = booleanPreferencesKey("stereo")
        val playlistGrid = booleanPreferencesKey("playlist_grid")
        val songSort = stringPreferencesKey("song_sort")
        val artistSort = stringPreferencesKey("artist_sort")
        val libraryTab = intPreferencesKey("library_tab")
        val songsShuffle = booleanPreferencesKey("songs_shuffle")
        val pulseRange = stringPreferencesKey("pulse_range")
        fun playlistSort(id: String) = stringPreferencesKey("playlist_sort_$id")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            storageMode = p[Keys.storageMode]?.let { StorageMode.valueOf(it) },
            reduceMotion = p[Keys.reduceMotion] ?: false,
            normalize = p[Keys.normalize] ?: true,
            resumeOnLaunch = p[Keys.resumeOnLaunch] ?: true,
            wifiOnly = p[Keys.wifiOnly] ?: false,
            hideFromOtherApps = p[Keys.hideFromOtherApps] ?: true,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            stereo = p[Keys.stereo] ?: true,
            playlistGrid = p[Keys.playlistGrid] ?: false,
            songSort = p[Keys.songSort],
            artistSort = p[Keys.artistSort],
            libraryTab = p[Keys.libraryTab] ?: 0,
            songsShuffle = p[Keys.songsShuffle] ?: false,
            pulseRange = p[Keys.pulseRange],
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingDone(value: Boolean) = store.edit { it[Keys.onboardingDone] = value }
    suspend fun setStorageMode(mode: StorageMode) = store.edit { it[Keys.storageMode] = mode.name }
    suspend fun setReduceMotion(value: Boolean) = store.edit { it[Keys.reduceMotion] = value }
    suspend fun setNormalize(value: Boolean) = store.edit { it[Keys.normalize] = value }
    suspend fun setResumeOnLaunch(value: Boolean) = store.edit { it[Keys.resumeOnLaunch] = value }
    suspend fun setWifiOnly(value: Boolean) = store.edit { it[Keys.wifiOnly] = value }
    suspend fun setHideFromOtherApps(value: Boolean) = store.edit { it[Keys.hideFromOtherApps] = value }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setStereo(value: Boolean) = store.edit { it[Keys.stereo] = value }
    suspend fun setPlaylistGrid(value: Boolean) = store.edit { it[Keys.playlistGrid] = value }
    suspend fun setSongSort(value: String) = store.edit { it[Keys.songSort] = value }
    suspend fun setArtistSort(value: String) = store.edit { it[Keys.artistSort] = value }
    suspend fun setLibraryTab(value: Int) = store.edit { it[Keys.libraryTab] = value }
    suspend fun setSongsShuffle(value: Boolean) = store.edit { it[Keys.songsShuffle] = value }
    suspend fun setPulseRange(value: String) = store.edit { it[Keys.pulseRange] = value }

    fun playlistSort(id: String): Flow<String?> = store.data.map { it[Keys.playlistSort(id)] }
    suspend fun setPlaylistSort(id: String, value: String) = store.edit { it[Keys.playlistSort(id)] = value }
}
