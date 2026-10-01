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
 * Utility to launch installed native Android applications on the device,
 * specifically the real 2nr (Drugi Numer) Android app.
 *
 * Automatically attaches the Macroify-style Floating Companion Overlay
 * and supports Samsung Multi-Window / Split-Screen launch modes.
 */
object AppLauncher {

    // Known package candidates for 2nr on Android:
    // 1. pl.rs.sip.softphone (Original 2nr - Drugi Numer by Mobile Formis)
    // 2. pl.m2nr (2nr - Drugi Numer version 2)
    // 3. com.moveit.two_nr (2nr Premium)
    private val KNOWN_TWO_NR_PACKAGES = listOf(
        "pl.rs.sip.softphone",
        "pl.m2nr",
        "com.moveit.two_nr"
    )

    /**
     * Resolves the installed 2nr package name either from known candidates
     * or by inspecting installed application labels.
     */
    fun resolve2nrPackage(context: Context): String? {
        val pm = context.packageManager

        // 1. Check known candidates
        for (pkg in KNOWN_TWO_NR_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) { }
        }

        // 2. Fallback search through installed packages for '2nr' or 'drugi numer'
        try {
            val installedApps = pm.getInstalledApplications(0)
            for (app in installedApps) {
                val pkgName = app.packageName.lowercase()
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (pkgName.contains("2nr") || pkgName.contains("two_nr") ||
                    label == "2nr" || label.contains("drugi numer")
                ) {
                    return app.packageName
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error searching installed applications")
        }

        return null
    }

    /**
     * Checks whether 2nr is installed on the device.
     */
    fun is2nrInstalled(context: Context): Boolean {
        return resolve2nrPackage(context) != null
    }

    /**
     * Launches the installed 2nr Android application.
     * Automatically activates the Floating Companion Overlay if permitted.
     */
    fun open2nrApp(context: Context, launchOverlay: Boolean = true) {
        val pm = context.packageManager
        val resolvedPackage = resolve2nrPackage(context)

        if (resolvedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(resolvedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Start Floating Companion Overlay so user has instant controls on top of 2nr
                if (launchOverlay && canDrawOverlays(context)) {
                    FloatingAssistantService.start(context)
                }

                context.startActivity(launchIntent)
                Toast.makeText(context, "Opening 2nr app with Floating Companion...", Toast.LENGTH_SHORT).show()
                Timber.i("Launched installed 2nr app: $resolvedPackage")
                return
            }
        }

        // If not installed, open 2nr page on Google Play Store
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=pl.rs.sip.softphone")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(marketIntent)
            Toast.makeText(context, "2nr app not found. Opening Google Play Store...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val webPlayIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=pl.rs.sip.softphone")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(webPlayIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Please install 2nr (pl.rs.sip.softphone) from Google Play Store", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Launches 2nr in Samsung One UI Multi-Window / Split-Screen mode side-by-side
     * with AAA-X Orchestrator.
     */
    fun open2nrInSplitScreen(context: Context) {
        val pm = context.packageManager
        val resolvedPackage = resolve2nrPackage(context)

        if (resolvedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(resolvedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
                context.startActivity(launchIntent)
                Toast.makeText(context, "Opening 2nr in Split-Screen mode...", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Fallback to standard launch
        open2nrApp(context)
    }

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
