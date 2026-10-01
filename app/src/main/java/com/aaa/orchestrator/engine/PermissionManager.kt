package com.aaa.orchestrator.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber

/**
 * Models permission status and one-tap redirect actions matching the Macroify permission model.
 */
data class PermissionStatus(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onGrant: (Context) -> Unit
)

/**
 * Manages system permissions, services, and battery exemption checks.
 * Provides 1-tap intents to grant each permission directly in Android system settings.
 */
object PermissionManager {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(context.packageName)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        return flat.contains(context.packageName)
    }

    fun isOverlayGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun getAllPermissions(context: Context): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                title = "Accessibility Service",
                description = "Enables hardware Volume-Down emergency kill-switch and automation gestures",
                isGranted = isAccessibilityEnabled(context),
                onGrant = { ctx ->
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Cannot open accessibility settings")
                    }
                }
            ),
            PermissionStatus(
                title = "Cloud SMS Notification Listener",
                description = "Intercepts incoming push notifications and SMS to auto-capture OTP verification codes",
                isGranted = isNotificationListenerEnabled(context),
                onGrant = { ctx ->
                    try {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Cannot open notification listener settings")
                    }
                }
            ),
            PermissionStatus(
                title = "Camera (KYC / Face Verification)",
                description = "Permits camera access for Twitter identity checks and KYC selfie verification",
                isGranted = context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                onGrant = { ctx ->
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Cannot open app permission settings")
                    }
                }
            ),
            PermissionStatus(
                title = "Display Over Other Apps (Overlay)",
                description = "Permits workflow HUD and floating status controls to draw over other apps",
                isGranted = isOverlayGranted(context),
                onGrant = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Cannot open overlay settings")
                        }
                    }
                }
            ),
            PermissionStatus(
                title = "Ignore Battery Optimization",
                description = "Prevents Samsung One UI from killing background services and foreground keep-alive",
                isGranted = isBatteryOptimizationIgnored(context),
                onGrant = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            Timber.e(e, "Cannot open battery optimization settings")
                        }
                    }
                }
            ),
            PermissionStatus(
                title = "Foreground Notifications",
                description = "Displays ongoing foreground service HUD and live execution status",
                isGranted = isNotificationsEnabled(context),
                onGrant = { ctx ->
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Cannot open notification settings")
                    }
                }
            )
        )
    }
}
