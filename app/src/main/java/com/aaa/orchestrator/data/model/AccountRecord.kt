package com.aaa.orchestrator.data.model

/**
 * Domain model representing a fully provisioned Twitter/X account with credentials,
 * 2FA cryptographic secret, and browser session cookies.
 */
data class AccountRecord(
    val id: Long = 0,
    val username: String,
    val password: String,
    val twoFactorSecret: String,
    val cookies: String,
    val phoneNumberUsed: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
) {
    /**
     * Standard buyer multi-column line format: Username: | Password: | 2FA: | Cookies
     */
    fun toDelimitedLine(delimiter: String = ":"): String {
        return "$username$delimiter$password$delimiter$twoFactorSecret$delimiter$cookies"
    }

    /**
     * Clean Google Sheets row columns matching the video method.
     */
    fun toSheetRow(): List<String> {
        return listOf(
            "$username:",
            "$password:",
            "$twoFactorSecret:",
            cookies
        )
    }
}

enum class SyncStatus {
    PENDING_SYNC,
    SYNCED_SHEETS,
    SYNCED_TELEGRAM,
    FULLY_SYNCED,
    FAILED
}
