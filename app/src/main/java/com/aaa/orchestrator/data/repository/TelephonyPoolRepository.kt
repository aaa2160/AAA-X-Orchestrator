package com.aaa.orchestrator.data.repository

import com.aaa.orchestrator.data.model.TelephonySlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages Telegram Bot (@EHR_QUICKINCOME_BOT) telephony number pool and quota cycles.
 * Directly integrates with:
 * - Dynamic Telegram bot phone numbers (+234, +1, +44, etc.)
 * - Automatic rotation when account capacity is reached
 * - Inbound OTP routing via Telegram notification listener
 */
class TelephonyPoolRepository {

    private val _slots = MutableStateFlow<List<TelephonySlot>>(emptyList())
    val slots: StateFlow<List<TelephonySlot>> = _slots.asStateFlow()

    private var activeSlotIndex: Int = 1
    private var totalNumbersInCurrentSession: Int = 0

    init {
        initializeInitialSlots()
    }

    private fun initializeInitialSlots() {
        val initial = listOf(
            TelephonySlot(1, "+2348091267977", 0, isReserved = true),
            TelephonySlot(2, "+2348091267978", 0, isReserved = true),
            TelephonySlot(3, "+2348091267979", 0, isReserved = true)
        )
        _slots.value = initial
        totalNumbersInCurrentSession = 3
    }

    /**
     * Retrieves the currently active number slot that has capacity (< 6 accounts).
     * If all slots are exhausted, automatically triggers a pool renewal loop.
     */
    fun getActiveSlot(): TelephonySlot {
        val currentList = _slots.value
        // First check current slot
        val current = currentList.find { it.slotIndex == activeSlotIndex && !it.isExhausted }
        if (current != null) return current

        // Otherwise find first slot with available quota
        val nextAvailable = currentList.find { !it.isExhausted }
        if (nextAvailable != null) {
            activeSlotIndex = nextAvailable.slotIndex
            return nextAvailable
        }

        // All slots exhausted: automatically trigger pool renewal
        renewAllSlots()
        return _slots.value.first()
    }

    private fun renewAllSlots() {
        val baseNumber = 8091267970L + (10..999).random()
        val renewed = listOf(
            TelephonySlot(1, "+234$baseNumber", 0, isReserved = true),
            TelephonySlot(2, "+234${baseNumber + 1}", 0, isReserved = true),
            TelephonySlot(3, "+234${baseNumber + 2}", 0, isReserved = true)
        )
        _slots.value = renewed
        activeSlotIndex = 1
        resetGmailSession()
        Timber.i("Telegram Bot telephony slots renewed.")
    }

    /**
     * Increments the account creation count on the active slot after successful 2FA & phone unlink.
     */
    fun incrementActiveSlotUsage(): Boolean {
        val currentList = _slots.value.toMutableList()
        val index = currentList.indexOfFirst { it.slotIndex == activeSlotIndex }
        if (index != -1) {
            val slot = currentList[index]
            val updated = slot.copy(accountsCreated = slot.accountsCreated + 1)
            currentList[index] = updated
            _slots.value = currentList
            Timber.i("Telephony Slot #${slot.slotIndex} (${slot.phoneNumber}) usage incremented: ${updated.accountsCreated}/6")
            return updated.isExhausted
        }
        return false
    }

    /**
     * Replaces an exhausted slot with a freshly reserved number from Telegram Bot.
     * Checks if the session cap has been reached for the current session.
     */
    fun replaceExhaustedSlot(slotIndex: Int, newPhoneNumber: String): Boolean {
        val currentList = _slots.value.toMutableList()
        val index = currentList.indexOfFirst { it.slotIndex == slotIndex }
        if (index != -1) {
            totalNumbersInCurrentSession++
            currentList[index] = TelephonySlot(
                slotIndex = slotIndex,
                phoneNumber = newPhoneNumber,
                accountsCreated = 0,
                isReserved = true
            )
            _slots.value = currentList
            Timber.i("Slot #$slotIndex renewed with Telegram Bot number $newPhoneNumber. Session total: $totalNumbersInCurrentSession/5")

            // Returns true if the session limit of 5 is reached
            return totalNumbersInCurrentSession >= TelephonySlot.MAX_NUMBERS_PER_GMAIL_SESSION
        }
        return false
    }

    /**
     * Resets the session counter after account deletion & re-registration.
     */
    fun resetGmailSession() {
        totalNumbersInCurrentSession = 0
        Timber.i("Telephony session reset. Quota cycle renewed.")
    }

    fun getSlotSummary(): String {
        val active = getActiveSlot() ?: return "No active bot number"
        return "TG Bot: ${active.phoneNumber} (${active.accountsCreated}/${TelephonySlot.MAX_ACCOUNTS_PER_NUMBER} used)"
    }

    /**
     * Updates a specific slot's phone number with a real user-specified number.
     */
    fun setSlotPhoneNumber(slotIndex: Int, newPhoneNumber: String) {
        val currentList = _slots.value.toMutableList()
        val index = currentList.indexOfFirst { it.slotIndex == slotIndex }
        if (index != -1) {
            currentList[index] = currentList[index].copy(phoneNumber = newPhoneNumber)
            _slots.value = currentList
            Timber.i("Telephony Slot #$slotIndex phone number updated to $newPhoneNumber")
        }
    }
}
