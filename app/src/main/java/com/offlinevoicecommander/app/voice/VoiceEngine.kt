package com.offlinevoicecommander.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.offlinevoicecommander.app.command.CommandProcessor
import com.offlinevoicecommander.app.state.AppState
import com.offlinevoicecommander.app.state.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Offline speech recognition with Vosk. Audio is processed in memory on this device only;
 * it is never written to disk and never sent over the network.
 */
object VoiceEngine : RecognitionListener {
    private const val SAMPLE_RATE = 16000.0f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var model: Model? = null
    private var speechService: SpeechService? = null

    @Volatile
    private var listenOnce = false

    fun modelDir(context: Context): File = File(context.applicationContext.filesDir, "vosk-model")

    fun isModelInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return File(dir, "am").isDirectory && File(dir, "conf").isDirectory
    }

    /** Call when the app starts: detects an installed model and loads it in the background. */
    fun refreshModelState(context: Context) {
        val installed = isModelInstalled(context)
        AppState.modelInstalled.value = installed
        if (installed) ensureModelLoaded(context) else AppState.voiceState.value = VoiceState.NO_MODEL
    }

    fun ensureModelLoaded(context: Context, onResult: (Boolean) -> Unit = {}) {
        val ctx = context.applicationContext
        synchronized(this) {
            if (model != null) {
                if (AppState.voiceState.value != VoiceState.LISTENING) AppState.voiceState.value = VoiceState.READY
                onResult(true)
                return
            }
            if (AppState.voiceState.value == VoiceState.LOADING) return
            if (!isModelInstalled(ctx)) {
                AppState.modelInstalled.value = false
                AppState.voiceState.value = VoiceState.NO_MODEL
                onResult(false)
                return
            }
            AppState.modelInstalled.value = true
            AppState.voiceState.value = VoiceState.LOADING
        }
        scope.launch {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val loaded = Model(modelDir(ctx).absolutePath)
                synchronized(this@VoiceEngine) { model = loaded }
                AppState.voiceState.value = VoiceState.READY
                onResult(true)
            } catch (e: Throwable) {
                AppState.voiceState.value = VoiceState.ERROR
                AppState.lastResult.value = "Could not load speech model: ${e.message}"
                onResult(false)
            }
        }
    }

    /** Stops listening and releases the model (used before replacing it with a new one). */
    fun unloadModel() {
        stop()
        synchronized(this) {
            try {
                model?.close()
            } catch (e: Exception) {
                // ignore
            }
            model = null
        }
        AppState.voiceState.value = VoiceState.NO_MODEL
    }

    /** Checks the microphone permission, loads the model if needed and starts listening. */
    fun startListening(context: Context, once: Boolean = false) {
        val ctx = context.applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AppState.lastResult.value = "Microphone permission is missing. Grant it in the app."
            return
        }
        if (!isModelInstalled(ctx)) {
            AppState.voiceState.value = VoiceState.NO_MODEL
            AppState.lastResult.value = "No speech model imported. Import a Vosk model in the app."
            return
        }
        ensureModelLoaded(ctx) { ok -> if (ok) start(once) }
    }

    @Synchronized
    private fun start(once: Boolean) {
        val loaded = model ?: return
        if (speechService != null) {
            listenOnce = once
            return
        }
        try {
            val recognizer = Recognizer(loaded, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            listenOnce = once
            if (service.startListening(this)) {
                speechService = service
                AppState.voiceState.value = VoiceState.LISTENING
                AppState.lastResult.value = if (once) "Listening for one command..." else "Listening..."
            } else {
                service.shutdown()
                AppState.lastResult.value = "Could not start listening"
            }
        } catch (e: Exception) {
            AppState.voiceState.value = VoiceState.ERROR
            AppState.lastResult.value = "Microphone error: ${e.message}"
        }
    }

    @Synchronized
    fun stop() {
        val service = speechService
        speechService = null
        if (service != null) {
            try {
                service.stop()
                service.shutdown()
            } catch (e: Exception) {
                // ignore
            }
        }
        if (model != null && AppState.voiceState.value != VoiceState.ERROR) {
            AppState.voiceState.value = VoiceState.READY
        }
    }

    // ---- RecognitionListener (delivered on the main thread) ----

    override fun onPartialResult(hypothesis: String?) {
        // Not used: commands are executed only on completed phrases.
    }

    override fun onResult(hypothesis: String?) {
        val text = try {
            JSONObject(hypothesis ?: return).optString("text", "").trim()
        } catch (e: Exception) {
            return
        }
        if (text.isEmpty()) return
        AppState.lastCommand.value = text
        CommandProcessor.handle(text)
        if (listenOnce) stop()
    }

    override fun onFinalResult(hypothesis: String?) {
        // Ignored on purpose: after Stop / Emergency Stop no half-heard phrase may trigger an action.
    }

    override fun onError(exception: Exception?) {
        AppState.voiceState.value = VoiceState.ERROR
        AppState.lastResult.value = "Recognition error: ${exception?.message}"
        synchronized(this) { speechService = null }
    }

    override fun onTimeout() {
        stop()
    }
}
