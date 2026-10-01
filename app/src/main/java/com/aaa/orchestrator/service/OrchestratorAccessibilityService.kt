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
    private var lastBotClickTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: ""

        // 1. Virtual / Polish SIM number detection
        if (packageName.contains("softphone", ignoreCase = true) ||
            packageName.contains("telephony", ignoreCase = true)
        ) {
            try {
                val rootNode = rootInActiveWindow ?: return
                val phone = findPhoneNumberInNode(rootNode)
                if (phone != null && phone != lastDetectedPhone) {
                    lastDetectedPhone = phone
                    Timber.i("AUTOMATICALLY CAPTURED PHONE NUMBER: $phone")
                    onPhoneDetected?.invoke(phone)
                    FloatingAssistantService.updatePhone(phone)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error inspecting node hierarchy")
            }
        }

        // 2. Telegram @EHR_QUICKINCOME_BOT automated interaction
        val isTelegram = packageName.contains("org.telegram.messenger", ignoreCase = true) ||
                packageName.contains("org.telegram.plus", ignoreCase = true) ||
                packageName.contains("org.thunderdog.challegram", ignoreCase = true) ||
                packageName.contains("telegram", ignoreCase = true)

        if (isTelegram) {
            try {
                val rootNode = rootInActiveWindow ?: return
                handleTelegramBotInteraction(rootNode)
            } catch (e: Exception) {
                Timber.w(e, "Error inspecting Telegram node hierarchy")
            }
        }
    }

    private fun handleTelegramBotInteraction(rootNode: android.view.accessibility.AccessibilityNodeInfo) {
        // Step A: Find and automatically click "+ GET NUMBER" if available
        val now = System.currentTimeMillis()
        if (now - lastBotClickTime > 4000) {
            val getNumberNode = findNodeWithText(rootNode, listOf("+ GET NUMBER", "GET NUMBER", "+GET NUMBER"))
            if (getNumberNode != null && (getNumberNode.isClickable || getNumberNode.parent?.isClickable == true)) {
                val target = if (getNumberNode.isClickable) getNumberNode else getNumberNode.parent
                val clicked = target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) ?: false
                if (clicked) {
                    lastBotClickTime = now
                    Timber.i("AUTOMATICALLY CLICKED '+ GET NUMBER' IN @EHR_QUICKINCOME_BOT!")
                }
            }
        }

        // Step B: Search for generated international phone number in inline buttons or messages
        val botPhone = findBotPhoneNumberInNode(rootNode)
        if (botPhone != null && botPhone != lastDetectedPhone) {
            lastDetectedPhone = botPhone
            Timber.i("AUTOMATICALLY CAPTURED PHONE NUMBER FROM @EHR_QUICKINCOME_BOT: $botPhone")
            onPhoneDetected?.invoke(botPhone)
            FloatingAssistantService.updatePhone(botPhone)

            // Automatically switch back to AAA-X Orchestrator
            FloatingAssistantService.bringOrchestratorToFront(this)
        }
    }

    private fun findNodeWithText(
        node: android.view.accessibility.AccessibilityNodeInfo?,
        targets: List<String>
    ): android.view.accessibility.AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        for (target in targets) {
            if (text.contains(target, ignoreCase = true) || desc.contains(target, ignoreCase = true)) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = findNodeWithText(node.getChild(i), targets)
            if (found != null) return found
        }
        return null
    }

    private fun findBotPhoneNumberInNode(node: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val phone = extractBotPhoneNumber(text)
        if (phone != null) return phone

        val desc = node.contentDescription?.toString() ?: ""
        val phoneFromDesc = extractBotPhoneNumber(desc)
        if (phoneFromDesc != null) return phoneFromDesc

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findBotPhoneNumberInNode(child)
            if (found != null) return found
        }
        return null
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

        fun extractBotPhoneNumber(text: String): String? {
            if (text.isBlank()) return null
            // Matches international format: +[country_code][number], e.g. +2348091267977 or +48459074091
            val match = Regex("\\+([0-9]{9,15})").find(text) ?: return null
            return match.value
        }

        fun extractPolishPhone(text: String): String? {
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
    }
}
