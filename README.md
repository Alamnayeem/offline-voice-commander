# Offline Voice Commander

Android app (Kotlin, Jetpack Compose, Material 3, MVVM) that lets you control your phone by voice,
with **offline** speech recognition (Vosk), a draggable floating command panel, an Accessibility
service for taps/swipes/scrolling, and a custom macro engine.

**Build status: NOT build-verified.** The project was written without access to Gradle, the Android SDK
or the internet, so it has never been compiled. The first GitHub Actions run may show a compile error
(see "If the build fails" below).

## Build the APK with GitHub (no Android Studio)

1. Create a free GitHub account and a new **empty repository** (any name, branch `main`).
2. Extract the ZIP. Open the extracted `OfflineVoiceCommander` folder and upload **its contents**
   (the folders `.github`, `app`, `gradle` and the files `build.gradle.kts`, `settings.gradle.kts`,
   `gradle.properties`, `.gitignore`, `README.md`) to the repository root:
   *Add file > Upload files*, drag the contents in, then *Commit changes*.
   - Do NOT upload the outer `OfflineVoiceCommander` folder itself. `.github/workflows/android-build.yml`
     must be at the top level of the repository, or GitHub will not run it.
   - Check that the repository shows `.github/workflows/android-build.yml`. If it is missing (some
     computers hide folders starting with a dot), use *Add file > Create new file*, type the name
     `.github/workflows/android-build.yml` and paste the file's content.
3. Open the **Actions** tab. The workflow "Build Debug APK" runs on every push to `main`
   (you can also start it with *Run workflow*). It takes about 5 to 10 minutes.
4. When it shows a green check, open the run and download **OfflineVoiceCommander-debug-apk**
   from the *Artifacts* section (a ZIP containing `app-debug.apk`).
5. Copy `app-debug.apk` to your phone and install it (allow "install unknown apps" if asked).

The Gradle wrapper files (`gradlew`, `gradle-wrapper.jar`) are intentionally not included: the
workflow installs Gradle 8.7 itself, so you do not need them.

### If the build fails
Open the failed run, open the red step "Build debug APK" and copy the first lines containing `error:`
or `e: ` and send them to Claude to get a fix.

## First-time setup on the phone

1. Open the app and follow the checklist on the Home tab: microphone, floating window,
   accessibility service, notifications (Android 13+).
2. Accessibility: Settings > Accessibility > Installed apps (or Downloaded apps) > Offline Voice
   Commander > turn on. If Android says "Restricted setting": Settings > Apps > Offline Voice
   Commander > three-dot menu > *Allow restricted settings*, then try again.
3. Speech model: download a Vosk model ZIP to your phone (for English, for example
   `vosk-model-small-en-us-0.15` from https://alphacephei.com/vosk/models) and tap *Import model*.
   The app does not download anything itself. Bangla works only if you import a compatible Vosk
   Bangla model; none is bundled and none is promised.
4. Tap *Start command center*. A floating panel appears (drag it by its title). Say commands such as
   "open YouTube", "start scrolling", "next video", "like this video", "go back", "stop".

## Features and where they live

| Feature | Files |
|---|---|
| Offline recognition (Vosk), model import | `voice/VoiceEngine.kt`, `voice/ModelImporter.kt` |
| Floating panel (mic, start/stop, last command, emergency stop, minimize) | `service/FloatingService.kt` |
| Accessibility actions | `service/VoiceAccessibilityService.kt`, `command/CommandExecutor.kt` |
| Voice command parsing | `command/CommandProcessor.kt` |
| Macros (create/edit/delete/run, saved as JSON in private storage) | `macro/*`, `ui/MacrosScreen.kt` |
| UI (Compose, Material 3) | `ui/*`, `viewmodel/MainViewModel.kt` |

To change the pause between automatic swipes, edit `SCROLL_INTERVAL_MS` in `command/CommandExecutor.kt`.

## Privacy

- No internet permission, no accounts, no ads, no analytics, no hardcoded secrets.
- Audio is processed in memory by Vosk on the device; it is never saved or uploaded.
- The microphone only runs while the command center is active (Android notification + green indicator).
- The accessibility service only performs actions requested by your voice commands/macros and does
  not record or transmit screen content.

## Limits that cannot be guaranteed (Android restrictions)

- Tapping, "like", and "click <text>" only work on elements an app exposes to accessibility. Some apps
  or screens hide them or block automation (banking/secure screens). App updates can break a macro.
- "Like this video" looks for a button labelled "Like"; apps with other labels will not match.
- Swipes are simulated at fixed screen positions; they work in most feeds but not everywhere.
- Opening apps from the background can be restricted on newer Android versions. The visible floating
  panel normally allows it, but this is not guaranteed on every device.
- On many phones, battery optimization or manufacturer "app killers" may stop the service; exclude the
  app from battery optimization if that happens.
- Android 13+ may restrict the accessibility setting for apps installed outside Google Play.
- Recognition accuracy depends on the Vosk model; small models are fast but make mistakes.
