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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Node inspection logic for 2nr and browser fields
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
    }
}
