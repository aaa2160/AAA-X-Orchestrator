package com.aaa.orchestrator.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import timber.log.Timber

/**
 * Utility to launch installed native Android applications on the device,
 * specifically the real 2nr (Drugi Numer) Android app.
 */
object AppLauncher {

    // Package candidates for 2nr on Android:
    // 1. pl.rs.sip.softphone (Original 2nr - Drugi Numer)
    // 2. pl.m2nr (2nr - Drugi Numer version 2)
    // 3. com.moveit.two_nr (2nr Premium)
    private val TWO_NR_PACKAGES = listOf(
        "pl.rs.sip.softphone",
        "pl.m2nr",
        "com.moveit.two_nr"
    )

    /**
     * Launches the installed 2nr Android application or opens its Google Play Store listing.
     */
    fun open2nrApp(context: Context) {
        val pm = context.packageManager
        for (pkg in TWO_NR_PACKAGES) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(context, "Opening 2nr app...", Toast.LENGTH_SHORT).show()
                Timber.i("Launched installed 2nr app: $pkg")
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
}
