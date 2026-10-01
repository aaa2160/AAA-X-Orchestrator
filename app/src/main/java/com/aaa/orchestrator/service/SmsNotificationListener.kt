package com.aaa.orchestrator.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber

/**
 * Listens for incoming push notifications from Cloud SMS and Telegram relays.
 * Automatically parses verification codes using regex.
 */
class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val title = extras.getCharSequence("android.title")?.toString() ?: ""

        // Check if notification is from Telegram, SMS, or messaging services
        val isTargetApp = packageName.contains("softphone", ignoreCase = true) ||
                packageName.contains("org.telegram.messenger", ignoreCase = true) ||
                packageName.contains("org.telegram.plus", ignoreCase = true) ||
                packageName.contains("org.thunderdog.challegram", ignoreCase = true) ||
                packageName.contains("telegram", ignoreCase = true) ||
                packageName.contains("sms", ignoreCase = true) ||
                packageName.contains("mobi", ignoreCase = true) ||
                packageName.contains("messaging", ignoreCase = true) ||
                title.contains("EHR", ignoreCase = true) ||
                title.contains("Quick", ignoreCase = true) ||
                title.contains("Telegram", ignoreCase = true) ||
                title.contains("OTP", ignoreCase = true) ||
                text.contains("EHR", ignoreCase = true) ||
                text.contains("Twitter", ignoreCase = true) ||
                text.contains("code", ignoreCase = true)

        if (isTargetApp) {
            Timber.i("Incoming notification from $packageName: $title - $text")
            val code = extractOtp(text) ?: extractOtp(title)
            if (code != null) {
                Timber.i("EXTRACTED SMS / TELEGRAM OTP CODE: $code")
                onOtpReceived?.invoke(code)
                FloatingAssistantService.updateOtp(code)
            }
        }
    }

    companion object {
        var onOtpReceived: ((String) -> Unit)? = null

        fun extractOtp(content: String): String? {
            if (content.isBlank()) return null

            // 1. Direct 6-digit contiguous code
            val directMatch = Regex("\\b\\d{6}\\b").find(content)?.value
            if (directMatch != null) return directMatch

            // 2. 3-3 split codes common in SMS aggregators (e.g., "123 456" or "123-456")
            val splitMatch = Regex("\\b\\d{3}[-\\s]\\d{3}\\b").find(content)?.value
            if (splitMatch != null) {
                return splitMatch.replace("-", "").replace(" ", "")
            }

            // 3. Keyword-prefixed OTPs (Polish "kod", "hasło", English "code", "pin", "verification")
            val prefixRegex = Regex("(?i)(?:kod|hasło|code|pin|weryfikacyjny|verification)[:\\s]+([0-9]{4,8})")
            val prefixMatch = prefixRegex.find(content)?.groupValues?.get(1)
            if (prefixMatch != null) return prefixMatch

            // 4. Twitter / X specific format: "G-123456" or standard digit block
            val xMatch = Regex("(?i)G-(\\d{6})").find(content)?.groupValues?.get(1)
            if (xMatch != null) return xMatch

            return null
        }
    }
}
