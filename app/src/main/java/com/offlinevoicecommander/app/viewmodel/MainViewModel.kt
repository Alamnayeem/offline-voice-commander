package com.offlinevoicecommander.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.offlinevoicecommander.app.command.CommandExecutor
import com.offlinevoicecommander.app.macro.Macro
import com.offlinevoicecommander.app.macro.MacroRepository
import com.offlinevoicecommander.app.service.FloatingService
import com.offlinevoicecommander.app.util.PermissionStatus
import com.offlinevoicecommander.app.util.Permissions
import com.offlinevoicecommander.app.voice.ModelImporter
import com.offlinevoicecommander.app.voice.VoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val macros: StateFlow<List<Macro>> = MacroRepository.macros

    private val _permissions = MutableStateFlow(Permissions.check(application))
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = Permissions.check(getApplication())
    }

    fun importModel(uri: Uri) = ModelImporter.start(getApplication(), uri)

    fun startCommandCenter() = FloatingService.start(getApplication())

    fun stopCommandCenter() = FloatingService.stop(getApplication())

    fun startListeningFromApp() = VoiceEngine.startListening(getApplication(), once = false)

    fun saveMacro(macro: Macro) = MacroRepository.save(macro)

    fun deleteMacro(id: String) = MacroRepository.delete(id)

    fun runMacro(macro: Macro) = CommandExecutor.runMacroNow(macro)
}
