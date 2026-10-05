package com.lumen.player.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UiMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

enum class TabRequest { HOME, LIBRARY, PULSE, ADD }

// app-wide one-off events: snackbars and "open this tab"
@Singleton
class AppEvents @Inject constructor() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UiMessage> = _messages

    private val _tabs = MutableSharedFlow<TabRequest>(replay = 1, extraBufferCapacity = 4)
    val tabs: SharedFlow<TabRequest> = _tabs

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, action, onAction))
    }

    fun open(tab: TabRequest) {
        _tabs.tryEmit(tab)
    }

    fun consumeTab() = _tabs.resetReplayCache()

    private val _expandPlayer = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val expandPlayer: SharedFlow<Unit> = _expandPlayer

    fun openPlayer() {
        _expandPlayer.tryEmit(Unit)
    }

    fun consumePlayer() = _expandPlayer.resetReplayCache()
}
