package com.aaa.orchestrator.data.repository

import com.aaa.orchestrator.data.model.TelephonySlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Manages real Telegram Bot (@EHR_QUICKINCOME_BOT) telephony numbers.
 * Strictly avoids synthetic or fake numbers:
 * - Only holds real numbers received from the Telegram Bot or user input.
 * - Tracks account usage (up to 6 accounts per real bot number).
 * - Explicitly indicates when a fresh number must be requested from the Telegram Bot.
 */
class TelephonyPoolRepository {

    private val _slots = MutableStateFlow<List<TelephonySlot>>(emptyList())
    val slots: StateFlow<List<TelephonySlot>> = _slots.asStateFlow()

    private var activeSlotIndex: Int = 1
    private var totalNumbersInCurrentSession: Int = 0

    /**
     * Retrieves the currently active number slot that has capacity (< 6 accounts).
     * Returns null if no real Telegram Bot number has been provided yet.
     */
    fun getActiveSlot(): TelephonySlot? {
        val currentList = _slots.value
        if (currentList.isEmpty()) return null

        val current = currentList.find { it.slotIndex == activeSlotIndex && !it.isExhausted }
        if (current != null) return current

        val nextAvailable = currentList.find { !it.isExhausted }
        if (nextAvailable != null) {
            activeSlotIndex = nextAvailable.slotIndex
            return nextAvailable
        }

        return null
    }

    /**
     * Registers a genuine phone number provided by @EHR_QUICKINCOME_BOT.
     */
    fun setSlotPhoneNumber(slotIndex: Int = 1, newPhoneNumber: String) {
        if (newPhoneNumber.isBlank()) return
        val currentList = _slots.value.toMutableList()
        val index = currentList.indexOfFirst { it.slotIndex == slotIndex }
        if (index != -1) {
            currentList[index] = currentList[index].copy(
                phoneNumber = newPhoneNumber,
                accountsCreated = 0,
                isReserved = true
            )
        } else {
            totalNumbersInCurrentSession++
            currentList.add(
                TelephonySlot(
                    slotIndex = slotIndex,
                    phoneNumber = newPhoneNumber,
                    accountsCreated = 0,
                    isReserved = true
                )
            )
        }
        _slots.value = currentList
        if (currentList.none { it.slotIndex == activeSlotIndex && !it.isExhausted }) {
            activeSlotIndex = slotIndex
        }
        Timber.i("Real Telegram Bot phone registered: $newPhoneNumber in slot #$slotIndex")
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
            Timber.i("Telegram Bot Number (${slot.phoneNumber}) usage: ${updated.accountsCreated}/6")
            return updated.isExhausted
        }
        return false
    }

    /**
     * Replaces an exhausted slot with a new number from Telegram Bot.
     */
    fun replaceExhaustedSlot(slotIndex: Int, newPhoneNumber: String): Boolean {
        if (newPhoneNumber.isBlank()) return false
        val currentList = _slots.value.toMutableList()
        val index = currentList.indexOfFirst { it.slotIndex == slotIndex }
        totalNumbersInCurrentSession++
        val newSlot = TelephonySlot(
            slotIndex = slotIndex,
            phoneNumber = newPhoneNumber,
            accountsCreated = 0,
            isReserved = true
        )
        if (index != -1) {
            currentList[index] = newSlot
        } else {
            currentList.add(newSlot)
        }
        _slots.value = currentList
        activeSlotIndex = slotIndex
        Timber.i("Active Telegram Bot number rotated to: $newPhoneNumber (Session total: $totalNumbersInCurrentSession)")
        return totalNumbersInCurrentSession >= TelephonySlot.MAX_NUMBERS_PER_GMAIL_SESSION
    }

    fun resetGmailSession() {
        totalNumbersInCurrentSession = 0
    }

    fun getSlotSummary(): String {
        val active = getActiveSlot()
        return if (active == null || active.phoneNumber.isBlank()) {
            "Awaiting Telegram Bot Number"
        } else {
            "TG Bot: ${active.phoneNumber} (${active.accountsCreated}/${TelephonySlot.MAX_ACCOUNTS_PER_NUMBER} used)"
        }
    }
}
