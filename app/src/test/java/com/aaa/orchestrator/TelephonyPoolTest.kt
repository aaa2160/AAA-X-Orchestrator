package com.aaa.orchestrator

import com.aaa.orchestrator.data.repository.TelephonyPoolRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TelephonyPoolTest {

    private lateinit var repo: TelephonyPoolRepository

    @Before
    fun setUp() {
        repo = TelephonyPoolRepository()
    }

    @Test
    fun testInitialPoolHasThreeSlots() {
        val slots = repo.slots.value
        assertEquals(3, slots.size)
        assertEquals("+48459074091", slots[0].phoneNumber)
        assertEquals(0, slots[0].accountsCreated)
    }

    @Test
    fun testSlotUsageIncrementAndExhaustionAtThree() {
        val active = repo.getActiveSlot()
        assertNotNull(active)
        assertEquals(1, active!!.slotIndex)

        // Increment 1
        var isExhausted = repo.incrementActiveSlotUsage()
        assertFalse(isExhausted)

        // Increment 2
        isExhausted = repo.incrementActiveSlotUsage()
        assertFalse(isExhausted)

        // Increment 3 -> Reaches limit
        isExhausted = repo.incrementActiveSlotUsage()
        assertTrue("Slot must be exhausted after 3 accounts", isExhausted)

        // Next active slot should automatically shift to slot 2
        val nextActive = repo.getActiveSlot()
        assertNotNull(nextActive)
        assertEquals(2, nextActive!!.slotIndex)
    }

    @Test
    fun testGmailSessionQuotaReachedAtFiveNumbers() {
        // Initial setup has 3 numbers
        val reached1 = repo.replaceExhaustedSlot(1, "+48459074094") // Total: 4
        assertFalse(reached1)

        val reached2 = repo.replaceExhaustedSlot(2, "+48459074095") // Total: 5
        assertTrue("5 numbers reached, session limit triggered", reached2)
    }
}
