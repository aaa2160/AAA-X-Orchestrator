package com.aaa.orchestrator

import com.aaa.orchestrator.engine.TotpGenerator
import org.junit.Assert.*
import org.junit.Test

class TotpGeneratorTest {

    @Test
    fun testBase32Decoding() {
        val decoded = TotpGenerator.decodeBase32("JBSWY3DPEHPK3PXP")
        assertEquals(10, decoded.size)
        assertEquals("Hello!", String(decoded, Charsets.ISO_8859_1).take(6))
    }

    @Test
    fun testTotpCodeGenerationLengthAndFormat() {
        val secret = "JBSWY3DPEHPK3PXP"
        val code = TotpGenerator.generateCurrentCode(secret, 1700000000000L)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testRemainingSecondsRange() {
        val remaining = TotpGenerator.getRemainingSeconds(System.currentTimeMillis())
        assertTrue("Remaining seconds should be between 1 and 30", remaining in 1..30)
    }

    @Test
    fun testRemainingProgressRange() {
        val progress = TotpGenerator.getRemainingProgress(System.currentTimeMillis())
        assertTrue("Progress should be between 0.0 and 1.0", progress in 0.0f..1.0f)
    }
}
