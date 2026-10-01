package com.aaa.orchestrator

import com.aaa.orchestrator.service.OrchestratorAccessibilityService
import com.aaa.orchestrator.service.SmsNotificationListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests validating automated integration with Telegram's @EHR_QUICKINCOME_BOT,
 * covering phone number extraction from bot buttons and OTP interception from Telegram notifications.
 */
class TelegramBotIntegrationTest {

    @Test
    fun testExtractBotPhoneNumberWithFlagEmoji() {
        // Exact format from @EHR_QUICKINCOME_BOT button (as seen in screenshot media_1790825884190.jpg)
        val buttonText = "🇳🇬 +2348091267977"
        val phone = OrchestratorAccessibilityService.extractBotPhoneNumber(buttonText)
        assertNotNull(phone)
        assertEquals("+2348091267977", phone)
    }

    @Test
    fun testExtractBotPhoneNumberStandardFormats() {
        val nigerianPhone = OrchestratorAccessibilityService.extractBotPhoneNumber("+2348091267977")
        assertEquals("+2348091267977", nigerianPhone)

        val polishPhone = OrchestratorAccessibilityService.extractBotPhoneNumber("🇵🇱 +48459074091")
        assertEquals("+48459074091", polishPhone)

        val usPhone = OrchestratorAccessibilityService.extractBotPhoneNumber("🇺🇸 +12025550192")
        assertEquals("+12025550192", usPhone)
    }

    @Test
    fun testExtractBotPhoneNumberEmptyOrInvalid() {
        assertNull(OrchestratorAccessibilityService.extractBotPhoneNumber(""))
        assertNull(OrchestratorAccessibilityService.extractBotPhoneNumber("𝕏 Twitter"))
        assertNull(OrchestratorAccessibilityService.extractBotPhoneNumber("🆙 Change Number"))
        assertNull(OrchestratorAccessibilityService.extractBotPhoneNumber("+ GET NUMBER"))
    }

    @Test
    fun testExtractTelegramBotOtpNotification() {
        // Notification from @EHR_QUICKINCOME_BOT
        val notificationText = "EHR ⚡ QUICK INCOME: Your Twitter confirmation code is 591024."
        val otp = SmsNotificationListener.extractOtp(notificationText)
        assertNotNull(otp)
        assertEquals("591024", otp)
    }

    @Test
    fun testExtractOtpGroupNotification() {
        // Notification from the companion OTP group
        val groupMessage = "🎁 OTP Group ↗: 𝕏 Twitter code: 739104 for +2348091267977"
        val otp = SmsNotificationListener.extractOtp(groupMessage)
        assertNotNull(otp)
        assertEquals("739104", otp)
    }

    @Test
    fun testExtractTwitterCodePrefix() {
        val msg = "Your X code is G-402918"
        val otp = SmsNotificationListener.extractOtp(msg)
        assertNotNull(otp)
        assertEquals("402918", otp)
    }

    @Test
    fun testAccountProfileGenerator() {
        val fullName = com.aaa.orchestrator.engine.AccountProfileGenerator.generateFullName()
        assertNotNull(fullName)
        assert(fullName.contains(" ")) { "Expected full name with first and last name, got $fullName" }

        val birthDate = com.aaa.orchestrator.engine.AccountProfileGenerator.generateBirthDate()
        assert(birthDate.month in 1..12)
        assert(birthDate.day in 1..28)
        assert(birthDate.year in 1993..2001)
    }

    @Test
    fun testTwitterAutoPilotScriptGeneration() {
        val script = com.aaa.orchestrator.engine.TwitterAutoPilot.buildAutoPilotScript(
            name = "John Doe",
            phone = "+2348091390514",
            birthMonth = 5,
            birthDay = 14,
            birthYear = 1997,
            password = "SecurePassword27",
            otp = "849201"
        )
        assertNotNull(script)
        assert(script.contains("+2348091390514"))
        assert(script.contains("849201"))
        assert(script.contains("reportFaceVerification"))
    }
}
