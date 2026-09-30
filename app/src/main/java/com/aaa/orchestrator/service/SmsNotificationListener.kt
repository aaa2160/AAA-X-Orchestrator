package com.aaa.orchestrator.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber

/**
 * Listens for incoming push notifications from 2nr / Polish SMS relays.
 * Automatically parses 6-digit verification codes using regex.
 */
class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val title = extras.getCharSequence("android.title")?.toString() ?: ""

        // Check if notification is from 2nr or messaging app
        if (packageName.contains("nr") || packageName.contains("sms") || packageName.contains("mobi")) {
            Timber.i("Incoming notification from $packageName: $title - $text")
            val code = extractSixDigitOtp(text) ?: extractSixDigitOtp(title)
            if (code != null) {
                Timber.i("EXTRACTED SMS OTP CODE: $code")
                onOtpReceived?.invoke(code)
            }
        }
    }

    private fun extractSixDigitOtp(content: String): String? {
        val regex = Regex("\\b\\d{6}\\b")
        return regex.find(content)?.value
    }

    companion object {
        var onOtpReceived: ((String) -> Unit)? = null
    }
}
