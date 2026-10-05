package com.lumen.player.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lumen.player.core.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

data class Focus(val ids: Set<String>) {
    val active get() = ids.isNotEmpty()
    val off get() = ids.isEmpty()
    val idList get() = ids.toList()
}

@Singleton
class FocusRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val key = stringSetPreferencesKey("focus_artist_ids")
    private val _focus = MutableStateFlow(Focus(runBlocking { store.data.first()[key] ?: emptySet() }))
    val focus: StateFlow<Focus> = _focus.asStateFlow()

    val isActive get() = _focus.value.active

    init {
        scope.launch { store.data.map { Focus(it[key] ?: emptySet()) }.collect { _focus.value = it } }
    }

    suspend fun set(ids: Set<String>) {
        _focus.value = Focus(ids)
        store.edit { it[key] = ids }
    }

    suspend fun add(id: String) = set(_focus.value.ids + id)
    suspend fun remove(id: String) = set(_focus.value.ids - id)
    suspend fun toggle(id: String) = if (id in _focus.value.ids) remove(id) else add(id)
    suspend fun only(id: String) = set(setOf(id))
    suspend fun clear() = set(emptySet())
}
