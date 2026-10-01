package com.aaa.orchestrator.data.model

/**
 * Models a Telegram Bot Telephony Number (@EHR_QUICKINCOME_BOT).
 * Automatically populated from Telegram bot inline buttons and inbound messages.
 */
data class TelephonySlot(
    val slotIndex: Int = 1,
    val phoneNumber: String, // e.g. "+2348091267977" from Telegram Bot
    var accountsCreated: Int = 0,
    var isReserved: Boolean = true,
    val reservedAt: Long = System.currentTimeMillis()
) {
    val isExhausted: Boolean
        get() = accountsCreated >= MAX_ACCOUNTS_PER_NUMBER

    val remainingAccounts: Int
        get() = (MAX_ACCOUNTS_PER_NUMBER - accountsCreated).coerceAtLeast(0)

    companion object {
        const val MAX_ACCOUNTS_PER_NUMBER = 6
        const val MAX_NUMBERS_PER_GMAIL_SESSION = 5
    }
}
