package com.aaa.orchestrator.data.repository

import com.aaa.orchestrator.data.model.TelephonySlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages the 3-slot virtual Polish (+48) number pool and quota cycles.
 * Directly implements the workflow:
 * - 3 active number slots simultaneously
 * - 3 accounts created per number (via immediate 2FA + phone unlinking)
 * - Number pool auto-rotation on quota exhaustion.
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
            TelephonySlot(1, "+48459074091", 0, isReserved = true),
            TelephonySlot(2, "+48459074092", 0, isReserved = true),
            TelephonySlot(3, "+48459074093", 0, isReserved = true)
        )
        _slots.value = initial
        totalNumbersInCurrentSession = 3
    }

    /**
     * Retrieves the currently active number slot that has capacity (< 3 accounts).
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
        val baseNumber = 459074090L + (10..999).random()
        val renewed = listOf(
            TelephonySlot(1, "+48$baseNumber", 0, isReserved = true),
            TelephonySlot(2, "+48${baseNumber + 1}", 0, isReserved = true),
            TelephonySlot(3, "+48${baseNumber + 2}", 0, isReserved = true)
        )
        _slots.value = renewed
        activeSlotIndex = 1
        resetGmailSession()
        Timber.i("All 3 telephony slots auto-renewed with new Polish number pool.")
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
            Timber.i("Telephony Slot #${slot.slotIndex} (${slot.phoneNumber}) usage incremented: ${updated.accountsCreated}/3")
            return updated.isExhausted
        }
        return false
    }

    /**
     * Replaces an exhausted slot with a freshly reserved number.
     * Checks if the 5-number cap has been reached for the current session.
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
            Timber.i("Slot #$slotIndex renewed with $newPhoneNumber. Session total: $totalNumbersInCurrentSession/5")

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
        Timber.i("Telephony session reset. Infinite quota cycle renewed.")
    }

    fun getSlotSummary(): String {
        val active = getActiveSlot() ?: return "All slots exhausted"
        return "Slot ${active.slotIndex}/3: ${active.phoneNumber} (${active.accountsCreated}/3 used)"
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
