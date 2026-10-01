package com.aaa.orchestrator.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.aaa.orchestrator.service.FloatingAssistantService
import timber.log.Timber

/**
 * Utility to launch external applications on the device,
 * specifically Telegram (@EHR_QUICKINCOME_BOT & @My_agy_Ai_bot) and browser tools.
 *
 * Automatically attaches the Floating Companion Overlay
 * and supports Samsung Multi-Window / Split-Screen launch modes.
 */
object AppLauncher {

    /**
     * Directly launches Telegram to the official number bot (@EHR_QUICKINCOME_BOT)
     * and automatically attaches the Floating Companion Overlay.
     */
    fun openTelegramBot(context: Context, botUsername: String = "EHR_QUICKINCOME_BOT", launchOverlay: Boolean = true) {
        if (launchOverlay && canDrawOverlays(context)) {
            FloatingAssistantService.start(context)
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$botUsername")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Opening @$botUsername with Auto-Fetch...", Toast.LENGTH_SHORT).show()
            Timber.i("Directly launched Telegram bot: $botUsername")
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUsername")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Please install Telegram to use @$botUsername", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Opens Telegram with @EHR_QUICKINCOME_BOT in Samsung One UI Split-Screen mode.
     */
    fun openTelegramInSplitScreen(context: Context, botUsername: String = "EHR_QUICKINCOME_BOT") {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$botUsername")).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
            context.startActivity(intent)
            Toast.makeText(context, "Opening @$botUsername in Split-Screen...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            openTelegramBot(context, botUsername)
        }
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}
