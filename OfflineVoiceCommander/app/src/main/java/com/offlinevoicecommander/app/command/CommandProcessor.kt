package com.offlinevoicecommander.app.command

import com.offlinevoicecommander.app.macro.Macro
import com.offlinevoicecommander.app.macro.MacroRepository
import com.offlinevoicecommander.app.state.AppState

sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class ClickText(val text: String) : Command()
    data class RunMacro(val macro: Macro) : Command()
    object StartScroll : Command()
    object StopScroll : Command()
    object NextVideo : Command()
    object PreviousVideo : Command()
    object Like : Command()
    object Back : Command()
    object ScrollUp : Command()
    object ScrollDown : Command()
    object SwipeUp : Command()
    object SwipeDown : Command()
    object LongPress : Command()
    object StopAll : Command()
}

/** Turns recognized text into a [Command] using simple keyword matching. */
object CommandProcessor {

    fun handle(rawText: String) {
        val command = parse(rawText, MacroRepository.macros.value)
        if (command == null) {
            AppState.lastResult.value = "Not understood: \"$rawText\""
            return
        }
        CommandExecutor.execute(command)
    }

    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun parse(raw: String, macros: List<Macro>): Command? {
        val t = normalize(raw)
        if (t.isEmpty()) return null

        // Macros win over built-in commands.
        macros.firstOrNull { it.trigger.isNotBlank() && normalize(it.trigger) == t }
            ?.let { return Command.RunMacro(it) }
        macros.firstOrNull { it.trigger.isNotBlank() && t.contains(normalize(it.trigger)) }
            ?.let { return Command.RunMacro(it) }

        return when {
            t.contains("stop scroll") -> Command.StopScroll
            t == "stop" || t == "cancel" || t.contains("stop all") ||
                t.contains("stop everything") || t.contains("emergency") -> Command.StopAll
            t.contains("start scroll") || t.contains("begin scroll") || t == "auto scroll" -> Command.StartScroll
            t.contains("next video") || t == "next" -> Command.NextVideo
            t.contains("previous video") || t.contains("last video") || t == "previous" -> Command.PreviousVideo
            (t.contains("like") && t.contains("video")) || t == "like" -> Command.Like
            t.contains("go back") || t == "back" -> Command.Back
            t.contains("scroll up") -> Command.ScrollUp
            t.contains("scroll down") -> Command.ScrollDown
            t.contains("swipe up") -> Command.SwipeUp
            t.contains("swipe down") -> Command.SwipeDown
            t.contains("long press") -> Command.LongPress
            t.startsWith("open ") && t.length > 5 -> Command.OpenApp(t.removePrefix("open ").trim())
            t.startsWith("click ") && t.length > 6 -> Command.ClickText(t.removePrefix("click ").trim())
            t.startsWith("tap ") && t.length > 4 -> Command.ClickText(t.removePrefix("tap ").trim())
            else -> null
        }
    }
}
