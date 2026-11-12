package com.offlinevoicecommander.app.state

import kotlinx.coroutines.flow.MutableStateFlow

enum class VoiceState { NO_MODEL, LOADING, READY, LISTENING, ERROR }

/** Small in-memory state shared between the UI, the overlay service and the engines. */
object AppState {
    val voiceState = MutableStateFlow(VoiceState.NO_MODEL)
    val modelInstalled = MutableStateFlow(false)
    val lastCommand = MutableStateFlow("-")
    val lastResult = MutableStateFlow("")
    val scrolling = MutableStateFlow(false)
    val overlayRunning = MutableStateFlow(false)
    val importing = MutableStateFlow(false)
    val importMessage = MutableStateFlow("")
}
