package com.offlinevoicecommander.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.offlinevoicecommander.app.command.CommandExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Performs gestures and clicks on behalf of the user's voice commands.
 * It only reacts to commands from this app; it does not read, store or send screen content.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: VoiceAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        CommandExecutor.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: screen events are never recorded or processed.
    }

    override fun onInterrupt() {
        CommandExecutor.stopAll()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        CommandExecutor.stopAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val description = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val dispatched = dispatchGesture(
                description,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }

    /** fingerUp = true moves content up (shows what is below / next video). */
    suspend fun swipeVertical(fingerUp: Boolean, fraction: Float): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val height = metrics.heightPixels.toFloat()
        val startY = if (fingerUp) height * (0.5f + fraction / 2f) else height * (0.5f - fraction / 2f)
        val endY = if (fingerUp) height * (0.5f - fraction / 2f) else height * (0.5f + fraction / 2f)
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        return gesture(path, 350)
    }

    suspend fun longPressCenter(): Boolean {
        val metrics = resources.displayMetrics
        val path = Path().apply { moveTo(metrics.widthPixels / 2f, metrics.heightPixels / 2f) }
        return gesture(path, 900)
    }

    /** Clicks the first visible, enabled, clickable element whose text or description contains [text]. */
    fun clickByText(text: String): Boolean = clickNode(text) { true }

    /**
     * Searches the active window for [query] and clicks the first match accepted by [matches]
     * (which receives the lower-case text or content description of the node).
     */
    fun clickNode(query: String, matches: (String) -> Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(query)
        for (node in nodes) {
            val label = (node.text ?: node.contentDescription)?.toString()?.lowercase()?.trim() ?: continue
            if (!matches(label)) continue
            var current: AccessibilityNodeInfo? = node
            var depth = 0
            while (current != null && depth < 6) {
                if (current.isClickable && current.isEnabled && current.isVisibleToUser) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
                depth++
            }
        }
        return false
    }
}
