package com.aaa.orchestrator

import com.aaa.orchestrator.engine.CookieParser
import org.junit.Assert.*
import org.junit.Test

class CookieParserTest {

    @Test
    fun testValidTwitterSessionCheck() {
        val validCookie = "guest_id=123; auth_token=test_token_abc; ct0=csrf_token_xyz"
        val invalidCookie = "guest_id=123; session_id=abc"

        assertTrue(CookieParser.hasValidTwitterSession(validCookie))
        assertFalse(CookieParser.hasValidTwitterSession(invalidCookie))
    }

    @Test
    fun testSanitizeForExport() {
        val dirtyCookie = "auth_token=abc;\nct0=xyz;\r\n"
        val sanitized = CookieParser.sanitizeForExport(dirtyCookie)
        assertFalse(sanitized.contains("\n"))
        assertFalse(sanitized.contains("\r"))
        assertEquals("auth_token=abc;ct0=xyz;", sanitized)
    }

    @Test
    fun testJsonAntiDetectFormatStructure() {
        val cookieString = "auth_token=abc12345; ct0=xyz67890"
        val json = CookieParser.toJsonAntiDetectFormat(cookieString)

        assertTrue(json.contains("\"name\": \"auth_token\""))
        assertTrue(json.contains("\"value\": \"abc12345\""))
        assertTrue(json.contains("\"name\": \"ct0\""))
        assertTrue(json.contains("\"domain\": \".x.com\""))
    }
}
