package com.offlinevoicecommander.app.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.offlinevoicecommander.app.service.VoiceAccessibilityService

data class PermissionStatus(
    val microphone: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val notifications: Boolean = true
)

object Permissions {
    fun check(context: Context): PermissionStatus = PermissionStatus(
        microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        overlay = Settings.canDrawOverlays(context),
        accessibility = isAccessibilityEnabled(context),
        notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    )

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == VoiceAccessibilityService::class.java.name
        }
    }
}
