package com.offlinevoicecommander.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.offlinevoicecommander.app.command.CommandExecutor
import com.offlinevoicecommander.app.macro.MacroRepository
import com.offlinevoicecommander.app.ui.MainScreen
import com.offlinevoicecommander.app.ui.OfflineVoiceCommanderTheme
import com.offlinevoicecommander.app.viewmodel.MainViewModel
import com.offlinevoicecommander.app.voice.VoiceEngine

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommandExecutor.init(this)
        MacroRepository.init(this)
        VoiceEngine.refreshModelState(this)
        setContent {
            OfflineVoiceCommanderTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}
