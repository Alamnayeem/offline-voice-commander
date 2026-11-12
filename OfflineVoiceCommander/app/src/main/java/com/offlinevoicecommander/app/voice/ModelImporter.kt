package com.offlinevoicecommander.app.voice

import android.content.Context
import android.net.Uri
import com.offlinevoicecommander.app.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Imports a Vosk model from a ZIP file the user picked. Nothing is downloaded by the app. */
object ModelImporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, uri: Uri) {
        if (AppState.importing.value) return
        val ctx = context.applicationContext
        AppState.importing.value = true
        AppState.importMessage.value = "Extracting model... (large models can take a few minutes)"
        scope.launch {
            val result = runCatching { importZip(ctx, uri) }
            AppState.importing.value = false
            result.fold(
                onSuccess = {
                    AppState.importMessage.value = "Model imported."
                    AppState.modelInstalled.value = true
                    VoiceEngine.ensureModelLoaded(ctx)
                },
                onFailure = {
                    AppState.importMessage.value = "Import failed: ${it.message}"
                }
            )
        }
    }

    private fun importZip(context: Context, uri: Uri) {
        val tmp = File(context.filesDir, "vosk-import-tmp")
        try {
            tmp.deleteRecursively()
            tmp.mkdirs()
            val tmpPath = tmp.canonicalPath
            var count = 0
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open the selected file")
            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val out = File(tmp, entry.name)
                        if (!out.canonicalPath.startsWith(tmpPath + File.separator)) {
                            throw IOException("Unsafe path in ZIP: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { zip.copyTo(it) }
                        }
                        count++
                        if (count % 20 == 0) AppState.importMessage.value = "Extracting... ($count files)"
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val root = tmp.walkTopDown().firstOrNull {
                it.isDirectory && File(it, "am").isDirectory && File(it, "conf").isDirectory
            } ?: throw IOException("This ZIP does not look like a Vosk model (no 'am' and 'conf' folders)")

            VoiceEngine.unloadModel()
            val target = VoiceEngine.modelDir(context)
            target.deleteRecursively()
            if (!root.renameTo(target)) {
                root.copyRecursively(target, overwrite = true)
            }
        } finally {
            tmp.deleteRecursively()
        }
    }
}
