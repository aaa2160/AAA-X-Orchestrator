package com.aaa.orchestrator

import com.aaa.orchestrator.engine.OfflineVisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfflineVisionEngineTest {

    @Test
    fun testExtractPolishPhoneNumberWithCountryCode() {
        val ocrOutput = "Active Cloud Virtual SIM: +48 459 074 091 status ready"
        val phone = OfflineVisionEngine.extractPolishPhoneNumber(ocrOutput)
        assertNotNull(phone)
        assertEquals("+48459074091", phone)
    }

    @Test
    fun testExtractPolishPhoneNumberWithoutCountryCode() {
        val ocrOutput = "Numer: 459-074-092 ważny przez 3 dni"
        val phone = OfflineVisionEngine.extractPolishPhoneNumber(ocrOutput)
        assertNotNull(phone)
        assertEquals("+48459074092", phone)
    }

    @Test
    fun testExtractInternationalPhoneNumber() {
        val ocrOutput = "Telegram Bot: +2348091267977 active for Twitter"
        val phone = OfflineVisionEngine.extractInternationalPhoneNumber(ocrOutput)
        assertNotNull(phone)
        assertEquals("+2348091267977", phone)
    }

    @Test
    fun testExtractPolishPhoneNumberStartingWith48Prefix() {
        val ocrOutput = "Numer: 489-123-456 ważny"
        val phone = OfflineVisionEngine.extractPolishPhoneNumber(ocrOutput)
        assertNotNull(phone)
        assertEquals("+48489123456", phone)
    }
}
