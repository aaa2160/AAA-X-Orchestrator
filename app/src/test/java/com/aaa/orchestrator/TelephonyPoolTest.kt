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
    fun testInitialPoolStartsEmptyAndAcceptsRealBotNumber() {
        assertNull(repo.getActiveSlot())
        assertEquals("Awaiting Telegram Bot Number", repo.getSlotSummary())

        repo.setSlotPhoneNumber(1, "+2348091267977")
        val active = repo.getActiveSlot()
        assertNotNull(active)
        assertEquals("+2348091267977", active!!.phoneNumber)
        assertEquals(0, active.accountsCreated)
        assertTrue(repo.getSlotSummary().contains("+2348091267977"))
    }

    @Test
    fun testSlotUsageIncrementAndExhaustionAtSix() {
        repo.setSlotPhoneNumber(1, "+2348091267977")
        repo.setSlotPhoneNumber(2, "+2348091267978")

        val active = repo.getActiveSlot()
        assertNotNull(active)
        assertEquals(1, active!!.slotIndex)

        // Increment 1 to 5
        for (i in 1..5) {
            val isExhausted = repo.incrementActiveSlotUsage()
            assertFalse(isExhausted)
        }

        // Increment 6 -> Reaches limit
        val isExhausted = repo.incrementActiveSlotUsage()
        assertTrue("Slot must be exhausted after 6 accounts", isExhausted)

        // Next active slot should automatically shift to slot 2
        val nextActive = repo.getActiveSlot()
        assertNotNull(nextActive)
        assertEquals(2, nextActive!!.slotIndex)
    }

    @Test
    fun testGmailSessionQuotaReachedAtFiveNumbers() {
        repo.setSlotPhoneNumber(1, "+2348091267977")
        repo.setSlotPhoneNumber(2, "+2348091267978")
        repo.setSlotPhoneNumber(3, "+2348091267979")

        val reached1 = repo.replaceExhaustedSlot(1, "+2348091267980") // Total: 4
        assertFalse(reached1)

        val reached2 = repo.replaceExhaustedSlot(2, "+2348091267981") // Total: 5
        assertTrue("5 numbers reached, session limit triggered", reached2)
    }
}
