package com.offlinevoicecommander.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offlinevoicecommander.app.state.AppState
import com.offlinevoicecommander.app.state.VoiceState
import com.offlinevoicecommander.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val perms by vm.permissions.collectAsState()
    val voiceState by AppState.voiceState.collectAsState()
    val modelInstalled by AppState.modelInstalled.collectAsState()
    val overlayRunning by AppState.overlayRunning.collectAsState()
    val importing by AppState.importing.collectAsState()
    val importMessage by AppState.importMessage.collectAsState()
    val lastCommand by AppState.lastCommand.collectAsState()
    val lastResult by AppState.lastResult.collectAsState()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            AppState.lastResult.value = "Could not open settings: ${e.message}"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Offline Voice Commander", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Control your phone by voice. Speech recognition runs fully on this device.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(voiceStateText(voiceState, overlayRunning))
                Text("Last heard: $lastCommand")
                if (lastResult.isNotBlank()) Text(lastResult, style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Setup checklist", style = MaterialTheme.typography.titleMedium)

        PermissionCard(
            title = "1. Microphone",
            granted = perms.microphone,
            description = "Needed to hear your voice commands. Audio stays on this device.",
            buttonLabel = "Grant microphone"
        ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

        PermissionCard(
            title = "2. Floating window (draw over other apps)",
            granted = perms.overlay,
            description = "Needed to show the draggable command panel on top of other apps.",
            buttonLabel = "Open overlay settings"
        ) {
            openSettings(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        }

        PermissionCard(
            title = "3. Accessibility service",
            granted = perms.accessibility,
            description = "Needed to tap, swipe, scroll and long press for you. In Settings, open " +
                "Installed apps (or Downloaded apps) and turn on Offline Voice Commander. " +
                "If Android says the setting is restricted: Settings > Apps > Offline Voice Commander > " +
                "three-dot menu > Allow restricted settings, then try again.",
            buttonLabel = "Open accessibility settings"
        ) { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = "4. Notifications (recommended)",
                granted = perms.notifications,
                description = "Android shows a notification while the microphone is active. " +
                    "Without this permission the notification may be hidden, but the app still works.",
                buttonLabel = "Allow notifications"
            ) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("5. Offline speech model", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (modelInstalled) "Model installed" else "No model imported yet",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Download a Vosk model ZIP (for English, for example vosk-model-small-en-us-0.15 from " +
                        "alphacephei.com/vosk/models) to your phone, then import it here. " +
                        "No model is bundled and nothing is downloaded by this app. Bangla works only if you " +
                        "import a compatible Vosk Bangla model; none is included.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (importMessage.isNotBlank()) Text(importMessage, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = !importing,
                        onClick = {
                            modelPicker.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                            )
                        }
                    ) { Text(if (modelInstalled) "Replace model (.zip)" else "Import model (.zip)") }
                    if (importing) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        val canStart = perms.microphone && perms.overlay && modelInstalled
        if (overlayRunning) {
            OutlinedButton(onClick = { vm.stopCommandCenter() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop command center")
            }
        } else {
            Button(
                onClick = { vm.startCommandCenter() },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start command center") }
            if (!canStart) {
                Text(
                    "Complete steps 1, 2 and 5 to start. Step 3 is needed for actions such as tap and scroll.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (!perms.accessibility) {
            Text(
                "Accessibility is off: voice recognition will work, but opening apps is the only action available.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (granted) "Status: enabled" else "Status: not enabled", style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!granted) OutlinedButton(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

private fun voiceStateText(state: VoiceState, overlayRunning: Boolean): String {
    val base = when (state) {
        VoiceState.NO_MODEL -> "No speech model imported"
        VoiceState.LOADING -> "Loading speech model..."
        VoiceState.READY -> "Ready (microphone off)"
        VoiceState.LISTENING -> "Microphone ON - listening"
        VoiceState.ERROR -> "Error (see message below)"
    }
    return if (overlayRunning) "$base - floating panel visible" else base
}
