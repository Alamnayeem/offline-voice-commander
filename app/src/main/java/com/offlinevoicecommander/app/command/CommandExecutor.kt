package com.offlinevoicecommander.app.command

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.offlinevoicecommander.app.macro.Macro
import com.offlinevoicecommander.app.macro.MacroStep
import com.offlinevoicecommander.app.macro.StepType
import com.offlinevoicecommander.app.service.VoiceAccessibilityService
import com.offlinevoicecommander.app.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Performs commands and macros. Everything runs on the main thread inside cancellable coroutines. */
object CommandExecutor {
    /** Pause between automatic swipes while "Start scrolling" is active. */
    const val SCROLL_INTERVAL_MS = 5000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var appContext: Context? = null
    private var scrollJob: Job? = null
    private var macroJob: Job? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun report(message: String) {
        AppState.lastResult.value = message
    }

    fun execute(command: Command) {
        when (command) {
            Command.StopAll -> stopAll()
            Command.StopScroll -> {
                stopScrolling()
                report("Scrolling stopped")
            }
            Command.StartScroll -> startScrolling()
            is Command.RunMacro -> runMacro(command.macro)
            else -> scope.launch { runSingle(command) }
        }
    }

    /** Immediately cancels every running macro and automatic scroll. */
    fun stopAll() {
        macroJob?.cancel()
        macroJob = null
        stopScrolling()
        report("All automated actions stopped")
    }

    fun stopScrolling() {
        scrollJob?.cancel()
        scrollJob = null
        AppState.scrolling.value = false
    }

    fun startScrolling(): Boolean {
        if (VoiceAccessibilityService.instance == null) {
            report("Accessibility service is not enabled. Enable it in the app first.")
            return false
        }
        scrollJob?.cancel()
        AppState.scrolling.value = true
        report("Auto-scroll started (say \"stop scrolling\" to end)")
        scrollJob = scope.launch {
            while (isActive) {
                delay(SCROLL_INTERVAL_MS)
                val service = VoiceAccessibilityService.instance
                if (service == null) {
                    AppState.scrolling.value = false
                    report("Auto-scroll ended: accessibility service disconnected")
                    break
                }
                service.swipeVertical(fingerUp = true, fraction = 0.6f)
            }
        }
        return true
    }

    private fun runMacro(macro: Macro) {
        macroJob?.cancel()
        macroJob = scope.launch {
            report("Running macro: ${macro.name}")
            for (step in macro.steps) {
                ensureActive()
                val ok = runStep(step)
                if (!ok && step.type == StepType.OPEN_APP) {
                    report("Macro \"${macro.name}\" stopped: could not open ${step.arg}")
                    return@launch
                }
            }
            report("Macro finished: ${macro.name}")
        }
    }

    /** Public so the macro screen can offer a "Run" test button. */
    fun runMacroNow(macro: Macro) = runMacro(macro)

    private suspend fun runStep(step: MacroStep): Boolean = when (step.type) {
        StepType.OPEN_APP -> openApp(step.arg)
        StepType.WAIT -> {
            val seconds = (step.arg.trim().toDoubleOrNull() ?: 1.0).coerceIn(0.0, 120.0)
            delay((seconds * 1000).toLong())
            true
        }
        StepType.CLICK_TEXT -> clickTextWithRetry(step.arg)
        StepType.START_SCROLL -> startScrolling()
        StepType.STOP_SCROLL -> {
            stopScrolling()
            true
        }
        StepType.NEXT_VIDEO -> withService("Next video", "Swipe failed") { it.swipeVertical(true, 0.6f) }
        StepType.PREVIOUS_VIDEO -> withService("Previous video", "Swipe failed") { it.swipeVertical(false, 0.6f) }
        StepType.SCROLL_DOWN -> withService("Scrolled down", "Scroll failed") { it.swipeVertical(true, 0.5f) }
        StepType.SCROLL_UP -> withService("Scrolled up", "Scroll failed") { it.swipeVertical(false, 0.5f) }
        StepType.LONG_PRESS_CENTER -> withService("Long press done", "Long press failed") { it.longPressCenter() }
        StepType.GO_BACK -> withService("Went back", "Back failed") {
            it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private suspend fun clickTextWithRetry(text: String): Boolean {
        repeat(4) { attempt ->
            val service = VoiceAccessibilityService.instance
            if (service == null) {
                report("Accessibility service is not enabled. Enable it in the app first.")
                return false
            }
            if (service.clickByText(text)) {
                report("Clicked \"$text\"")
                return true
            }
            if (attempt < 3) delay(1500)
        }
        report("Could not find \"$text\" on screen (continuing)")
        return false
    }

    private suspend fun runSingle(command: Command) {
        when (command) {
            is Command.OpenApp -> openApp(command.name)
            Command.NextVideo -> withService("Next video", "Swipe failed") { it.swipeVertical(true, 0.6f) }
            Command.PreviousVideo -> withService("Previous video", "Swipe failed") { it.swipeVertical(false, 0.6f) }
            Command.ScrollDown -> withService("Scrolled down", "Scroll failed") { it.swipeVertical(true, 0.5f) }
            Command.ScrollUp -> withService("Scrolled up", "Scroll failed") { it.swipeVertical(false, 0.5f) }
            Command.SwipeUp -> withService("Swiped up", "Swipe failed") { it.swipeVertical(true, 0.6f) }
            Command.SwipeDown -> withService("Swiped down", "Swipe failed") { it.swipeVertical(false, 0.6f) }
            Command.LongPress -> withService("Long press done", "Long press failed") { it.longPressCenter() }
            Command.Back -> withService("Went back", "Back failed") {
                it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            Command.Like -> withService("Liked", "Could not find a Like button on screen") {
                it.clickNode("like") { label -> label == "like" || label.startsWith("like ") }
            }
            is Command.ClickText -> withService(
                "Clicked \"${command.text}\"",
                "Could not find a clickable \"${command.text}\""
            ) { it.clickByText(command.text) }
            else -> Unit
        }
    }

    private suspend fun withService(
        okMessage: String,
        failMessage: String,
        block: suspend (VoiceAccessibilityService) -> Boolean
    ): Boolean {
        val service = VoiceAccessibilityService.instance
        if (service == null) {
            report("Accessibility service is not enabled. Enable it in the app first.")
            return false
        }
        val ok = try {
            block(service)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        report(if (ok) okMessage else failMessage)
        return ok
    }

    fun openApp(name: String): Boolean {
        val ctx = appContext
        if (ctx == null) {
            report("App is not initialized")
            return false
        }
        val intent = findLaunchIntent(ctx, name)
        if (intent == null) {
            report("App not found: $name")
            return false
        }
        return try {
            ctx.startActivity(intent)
            report("Opening $name")
            true
        } catch (e: Exception) {
            report("Could not open $name: ${e.message}")
            false
        }
    }

    private fun findLaunchIntent(ctx: Context, name: String): Intent? {
        val target = name.trim().lowercase()
        if (target.isEmpty()) return null
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labelled = pm.queryIntentActivities(query, 0).map { it to it.loadLabel(pm).toString().lowercase() }
        val match = labelled.firstOrNull { it.second == target }
            ?: labelled.firstOrNull { it.second.contains(target) }
            ?: return null
        val launch = pm.getLaunchIntentForPackage(match.first.activityInfo.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch
    }
}
