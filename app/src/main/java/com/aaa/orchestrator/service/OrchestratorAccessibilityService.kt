package com.aaa.orchestrator.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android AccessibilityService providing system-wide gesture automation
 * and context-aware Hardware Volume-Down Emergency Kill Switch interception.
 */
class OrchestratorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("OrchestratorAccessibilityService connected and ready.")
    }

    private var lastDetectedPhone: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.contains("pl.rs.sip.softphone", ignoreCase = true) ||
            packageName.contains("m2nr", ignoreCase = true) ||
            packageName.contains("two_nr", ignoreCase = true) ||
            packageName.contains("2nr", ignoreCase = true) ||
            packageName.contains("softphone", ignoreCase = true)
        ) {
            try {
                val rootNode = rootInActiveWindow ?: return
                val phone = findPhoneNumberInNode(rootNode)
                if (phone != null && phone != lastDetectedPhone) {
                    lastDetectedPhone = phone
                    Timber.i("AUTOMATICALLY CAPTURED PHONE NUMBER FROM 2NR: $phone")
                    onPhoneDetected?.invoke(phone)
                    FloatingAssistantService.updatePhone(phone)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error inspecting 2nr node hierarchy")
            }
        }
    }

    private fun findPhoneNumberInNode(node: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val phone = extractPolishPhone(text)
        if (phone != null) return phone

        val desc = node.contentDescription?.toString() ?: ""
        val phoneFromDesc = extractPolishPhone(desc)
        if (phoneFromDesc != null) return phoneFromDesc

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findPhoneNumberInNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun extractPolishPhone(text: String): String? {
        if (text.isBlank()) return null
        val regex = Regex("(\\+48[\\s-]?)?([4-9]\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{3})")
        val match = regex.find(text) ?: return null
        val rawDigits = match.value.filter { it.isDigit() }
        return if (rawDigits.startsWith("48") && rawDigits.length == 11) {
            "+$rawDigits"
        } else if (rawDigits.length == 9) {
            "+48$rawDigits"
        } else if (rawDigits.length == 11) {
            "+$rawDigits"
        } else null
    }

    override fun onInterrupt() {
        Timber.w("Accessibility service interrupted.")
    }

    /**
     * Context-aware hardware key event interceptor.
     * When automation is active: Consumes Volume-Down, halts in < 1ms, delivers double vibration.
     * When automation is idle: Passes event to system for normal audio volume control.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            if (isAutomationRunning.get()) {
                Timber.e("HARDWARE VOLUME-DOWN DETECTED: Intercepting for Emergency Kill Switch!")
                triggerDoubleHapticPulse()
                onKillSwitchTriggered?.invoke()
                return true // Consume event
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * Dispatches an automated programmatic tap at the specified screen coordinates.
     */
    fun dispatchTap(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Timber.w("Dispatched tap cancelled at ($x, $y)")
            }
        }, null)
    }

    private fun triggerDoubleHapticPulse() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 100, 200)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        } catch (e: Exception) {
            Timber.e(e, "Haptic pulse failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: OrchestratorAccessibilityService? = null
            private set

        val isAutomationRunning = AtomicBoolean(false)
        var onKillSwitchTriggered: (() -> Unit)? = null
        var onPhoneDetected: ((String) -> Unit)? = null
    }
}
