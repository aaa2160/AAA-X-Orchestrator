package com.aaa.orchestrator.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.aaa.orchestrator.MainActivity
import com.aaa.orchestrator.OrchestratorApp
import com.aaa.orchestrator.R
import timber.log.Timber

/**
 * Foreground Service running with a PARTIAL_WAKE_LOCK to neutralize
 * Samsung One UI background killing policies.
 */
class OrchestratorForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "Autonomous Workflow Active"
        val notification = buildNotification(statusText)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AAAX:OrchestratorWakeLock"
            ).apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety lease
            }
            Timber.i("Partial WakeLock acquired successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire WakeLock")
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, OrchestratorApp.CHANNEL_ID_FOREGROUND)
            .setContentTitle("AAA X-Orchestrator")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        Timber.i("Foreground service stopped and WakeLock released.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        fun start(context: Context, status: String = "Starting...") {
            val intent = Intent(context, OrchestratorForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS_TEXT, status)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OrchestratorForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
