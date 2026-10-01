package com.aaa.orchestrator.data.model

/**
 * Sealed class representing all deterministic execution phases of the
 * AAA X-Orchestrator automation finite state machine.
 */
sealed class OrchestratorState(val label: String, val progress: Float) {
    object Idle : OrchestratorState("Idle - Ready", 0.0f)
    object PreflightCheck : OrchestratorState("Preflight Safety & Proxy Check", 0.12f)
    data class TargetDispatch(val url: String) : OrchestratorState("Stealth Browser Initialized", 0.25f)
    data class TelephonyLoop(val slotNumber: Int, val phoneNumber: String) : OrchestratorState("Telephony (Cloud SIM #$slotNumber: $phoneNumber)", 0.40f)
    data class EmailVerification(val email: String) : OrchestratorState("Verifying Email ($email)", 0.55f)
    object Crypto2faSetup : OrchestratorState("In-Memory RFC 6238 2FA & Phone Unlink", 0.70f)
    object SessionExtraction : OrchestratorState("Extracting Cookies & Local Cache Reset", 0.85f)
    data class MultiCloudSync(val accountIndex: Int) : OrchestratorState("Cloud Fan-Out (Google Sheets & Telegram)", 1.0f)
    data class PausedThrottled(val reason: String) : OrchestratorState("Paused: $reason", 0.0f)
    data class HaltedKillSwitch(val timestamp: Long) : OrchestratorState("Emergency Kill Switch Triggered", 0.0f)
}

/**
 * Metric snapshot for the live dashboard.
 */
data class DashboardMetrics(
    val totalCreated: Int = 0,
    val pendingSyncCount: Int = 0,
    val batteryPercent: Int = 100,
    val batteryTempCelsius: Float = 30.0f,
    val activeProxyIp: String = "Connecting...",
    val currentSlotInfo: String = "Slot 1/3 (0/3 used)",
    val isRunning: Boolean = false
)
