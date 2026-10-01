package com.aaa.orchestrator.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.aaa.orchestrator.MainActivity
import timber.log.Timber

/**
 * System notification and haptic dispatcher for Twitter/X Face Verification challenges.
 * Immediately alerts the operator with high-priority audio-visual and haptic cues
 * so they can complete the selfie/face check directly on screen without automation failure.
 */
object FaceVerificationNotifier {

    private const val CHANNEL_ID = "face_verification_alerts"
    private const val CHANNEL_NAME = "Security & Identity Challenges"
    private const val NOTIFICATION_ID = 9001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority notifications when Face Verification or Arkose Captcha is detected."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 400)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showFaceVerificationAlert(context: Context, detail: String = "Complete on-screen identity check") {
        createNotificationChannel(context)

        // Haptic feedback alert
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 250, 100, 250, 100, 400),
                        intArrayOf(0, 255, 0, 255, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (e: Exception) {
            Timber.w(e, "Haptic vibration failed")
        }

        // Tap notification to open AAA-X directly to the verification page
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("nav_screen", "browser")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Face Verification Required")
            .setContentText("Twitter requested identity verification. $detail")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Twitter has presented an on-screen identity verification challenge. Tap to complete the selfie/face check now so registration can resume."
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
        Timber.w("FACE VERIFICATION ALERT POSTED TO SYSTEM NOTIFICATION SHADE")
    }

    fun dismissAlert(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
