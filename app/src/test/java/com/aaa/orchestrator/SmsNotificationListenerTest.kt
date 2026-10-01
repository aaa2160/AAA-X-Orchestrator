package com.aaa.orchestrator

import com.aaa.orchestrator.service.SmsNotificationListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmsNotificationListenerTest {

    @Test
    fun testDirectSixDigitOtp() {
        val message = "Your Twitter confirmation code is 849201."
        val otp = SmsNotificationListener.extractOtp(message)
        assertNotNull(otp)
        assertEquals("849201", otp)
    }

    @Test
    fun testHyphenatedPolishOtp() {
        val message = "2nr: Twój kod weryfikacyjny to 512-394"
        val otp = SmsNotificationListener.extractOtp(message)
        assertNotNull(otp)
        assertEquals("512394", otp)
    }

    @Test
    fun testSpacedPolishOtp() {
        val message = "Kod: 981 234 ważny przez 5 minut"
        val otp = SmsNotificationListener.extractOtp(message)
        assertNotNull(otp)
        assertEquals("981234", otp)
    }

    @Test
    fun testXSpecificFormat() {
        val message = "Użyj kodu G-621890, aby potwierdzić tożsamość"
        val otp = SmsNotificationListener.extractOtp(message)
        assertNotNull(otp)
        assertEquals("621890", otp)
    }
}
