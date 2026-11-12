package com.offlinevoicecommander.app.macro

enum class StepType(val label: String, val needsArg: Boolean, val argHint: String) {
    OPEN_APP("Open app", true, "App name, e.g. Facebook"),
    WAIT("Wait (seconds)", true, "Seconds, e.g. 5"),
    CLICK_TEXT("Click visible text", true, "Text on screen, e.g. Reels"),
    START_SCROLL("Start auto-scroll", false, ""),
    STOP_SCROLL("Stop auto-scroll", false, ""),
    NEXT_VIDEO("Next video", false, ""),
    PREVIOUS_VIDEO("Previous video", false, ""),
    SCROLL_DOWN("Scroll down", false, ""),
    SCROLL_UP("Scroll up", false, ""),
    LONG_PRESS_CENTER("Long press screen center", false, ""),
    GO_BACK("Go back", false, "")
}

data class MacroStep(val type: StepType, val arg: String = "") {
    fun describe(): String = if (type.needsArg) "${type.label}: $arg" else type.label
}

data class Macro(
    val id: String,
    val name: String,
    val trigger: String,
    val steps: List<MacroStep>
)
