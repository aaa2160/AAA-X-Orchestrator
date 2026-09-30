package com.aaa.orchestrator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.aaa.orchestrator.data.local.AppDatabase
import timber.log.Timber

/**
 * AAA X-Orchestrator Application entry point.
 * Initializes logging, encrypted local persistence, and foreground notification channels.
 */
class OrchestratorApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()
        Timber.i("AAA X-Orchestrator initialized successfully.")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "Workflow Orchestrator HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps automation active and displays live execution state"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_FOREGROUND = "aaa_orchestrator_foreground"
        lateinit var instance: OrchestratorApp
            private set
    }
}
