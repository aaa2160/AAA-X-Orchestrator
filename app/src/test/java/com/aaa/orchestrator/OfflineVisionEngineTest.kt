package com.aaa.orchestrator

import com.aaa.orchestrator.engine.OfflineVisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfflineVisionEngineTest {

    @Test
    fun testExtractPolishPhoneNumberWithCountryCode() {
        val ocrOutput = "Active 2nr Virtual SIM: +48 459 074 091 status ready"
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
}
