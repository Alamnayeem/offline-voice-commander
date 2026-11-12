package com.offlinevoicecommander.app.macro

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores macros as a JSON file in the app's private storage (no cloud, no database library). */
object MacroRepository {
    private const val FILE_NAME = "macros.json"

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        file = f
        if (!f.exists()) {
            _macros.value = defaultMacros()
            persist()
            return
        }
        _macros.value = try {
            parse(f.readText())
        } catch (e: Exception) {
            defaultMacros()
        }
    }

    @Synchronized
    fun save(macro: Macro) {
        val list = _macros.value.toMutableList()
        val index = list.indexOfFirst { it.id == macro.id }
        if (index >= 0) list[index] = macro else list.add(macro)
        _macros.value = list
        persist()
    }

    @Synchronized
    fun delete(id: String) {
        _macros.value = _macros.value.filterNot { it.id == id }
        persist()
    }

    private fun defaultMacros(): List<Macro> = listOf(
        Macro(
            id = "default-facebook-mode",
            name = "Facebook Mode",
            trigger = "start facebook mode",
            steps = listOf(
                MacroStep(StepType.OPEN_APP, "Facebook"),
                MacroStep(StepType.WAIT, "6"),
                MacroStep(StepType.CLICK_TEXT, "Reels"),
                MacroStep(StepType.WAIT, "3"),
                MacroStep(StepType.START_SCROLL)
            )
        )
    )

    private fun persist() {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(toJson(_macros.value))
            if (!tmp.renameTo(f)) {
                f.writeText(toJson(_macros.value))
                tmp.delete()
            }
        } catch (e: Exception) {
            // Keep the in-memory list; the next successful save will persist everything.
        }
    }

    private fun toJson(list: List<Macro>): String {
        val arr = JSONArray()
        for (m in list) {
            val steps = JSONArray()
            for (s in m.steps) {
                steps.put(JSONObject().put("type", s.type.name).put("arg", s.arg))
            }
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("trigger", m.trigger)
                    .put("steps", steps)
            )
        }
        return arr.toString(2)
    }

    private fun parse(text: String): List<Macro> {
        val arr = JSONArray(text)
        val out = mutableListOf<Macro>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sa = o.getJSONArray("steps")
            val steps = mutableListOf<MacroStep>()
            for (j in 0 until sa.length()) {
                val so = sa.getJSONObject(j)
                val type = runCatching { StepType.valueOf(so.getString("type")) }.getOrNull() ?: continue
                steps.add(MacroStep(type, so.optString("arg", "")))
            }
            out.add(Macro(o.getString("id"), o.getString("name"), o.optString("trigger", ""), steps))
        }
        return out
    }
}
