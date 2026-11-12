package com.offlinevoicecommander.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CommandsScreen() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Voice commands", style = MaterialTheme.typography.headlineSmall)
        Section(
            "Open apps",
            "\"Open Facebook\", \"Open YouTube\", \"Open WhatsApp\", \"Open Chrome\". " +
                "\"Open <name>\" works for any installed app whose name you say clearly."
        )
        Section(
            "Scrolling and videos",
            "\"Start scrolling\" (swipes every 5 seconds), \"Stop scrolling\", \"Scroll up\", " +
                "\"Scroll down\", \"Next video\", \"Previous video\", \"Swipe up\", \"Swipe down\"."
        )
        Section(
            "Tapping",
            "\"Like this video\", \"Click <text on screen>\" or \"Tap <text on screen>\" (for example " +
                "\"click reels\"), \"Long press\" (center of the screen)."
        )
        Section("Navigation", "\"Go back\".")
        Section(
            "Stopping",
            "\"Stop\", \"Stop all\" or \"Emergency stop\" cancel every running macro and scrolling. " +
                "The red EMERGENCY STOP button on the floating panel also turns the microphone off."
        )
        Section(
            "Macros",
            "Say the trigger phrase of any macro you created, for example \"start facebook mode\"."
        )
        Section(
            "Good to know",
            "Commands are matched by keywords in English. Recognition quality depends on the speech " +
                "model you imported (small models make more mistakes). Actions that tap or scroll " +
                "depend on the target app exposing accessible buttons; they may fail in some apps or " +
                "screens (banking and secure screens block them on purpose)."
        )
    }
}

@Composable
fun PrivacyScreen() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Privacy and limits", style = MaterialTheme.typography.headlineSmall)
        Section(
            "Your voice stays on this phone",
            "Speech is recognized offline by Vosk using a model stored on your device. Audio is kept " +
                "in memory only while listening, is never saved to a file and is never uploaded. " +
                "The app has no internet permission, no accounts, no ads and no analytics."
        )
        Section(
            "No hidden recording",
            "The microphone is used only after you start the command center. While it is active, " +
                "Android shows a notification and the floating panel shows a green indicator. " +
                "Stop, Emergency Stop or closing the command center turns the microphone off."
        )
        Section(
            "Why each permission is needed",
            "Microphone: hear your commands. Draw over other apps: show the floating panel. " +
                "Accessibility: perform taps, swipes, scrolls, long presses and Back for you. " +
                "Foreground service and notifications: Android requires them while the microphone " +
                "runs in the background. You enable the overlay and accessibility permissions yourself."
        )
        Section(
            "About the accessibility service",
            "It runs only the actions requested by your own voice commands or macros. It does not " +
                "collect, store or send what is on your screen. You can turn it off at any time in " +
                "Android Settings > Accessibility."
        )
        Section(
            "What cannot be guaranteed",
            "Android and other apps may block automation: some apps hide their buttons from " +
                "accessibility, change their layout in updates, or ignore simulated gestures. " +
                "Opening an app from the background can be refused by newer Android versions. " +
                "Accessibility settings can be restricted for apps installed outside Google Play " +
                "(Allow restricted settings). Bangla recognition needs a compatible offline Bangla " +
                "model, which is not included."
        )
    }
}
