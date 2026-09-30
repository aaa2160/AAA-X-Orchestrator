package com.aaa.orchestrator.data.model

/**
 * Models a single virtual Polish (+48) number slot in 2nr.
 * The video workflow establishes:
 * 1. 2nr holds up to 3 slots simultaneously.
 * 2. Each slot creates up to 3 Twitter accounts before being deleted.
 * 3. A single Gmail session supports up to 5 total number reservations before requiring account reset.
 */
data class TelephonySlot(
    val slotIndex: Int, // 1, 2, or 3
    val phoneNumber: String, // e.g. "+48459074092"
    var accountsCreated: Int = 0, // Max 3
    var isReserved: Boolean = true,
    val reservedAt: Long = System.currentTimeMillis()
) {
    val isExhausted: Boolean
        get() = accountsCreated >= MAX_ACCOUNTS_PER_NUMBER

    val remainingAccounts: Int
        get() = (MAX_ACCOUNTS_PER_NUMBER - accountsCreated).coerceAtLeast(0)

    companion object {
        const val MAX_ACCOUNTS_PER_NUMBER = 3
        const val MAX_NUMBERS_PER_GMAIL_SESSION = 5
    }
}
