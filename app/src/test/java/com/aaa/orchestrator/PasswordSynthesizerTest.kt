package com.aaa.orchestrator

import com.aaa.orchestrator.engine.PasswordSynthesizer
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PasswordSynthesizerTest {

    @Test
    fun testPasswordLengthAndComposition() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 27)
        }
        val password = PasswordSynthesizer.generatePassword(calendar)

        assertEquals("Password must be exactly 10 characters", 10, password.length)
        assertTrue("Password must end in the specified day of month", password.endsWith("27"))
        assertTrue("Password must contain at least one uppercase letter", password.any { it.isUpperCase() })
        assertTrue("Password must contain at least one lowercase letter", password.any { it.isLowerCase() })
        assertTrue("Password must contain numeric digits", password.any { it.isDigit() })

        // Ensure no symbols
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        assertFalse("Password must not contain special symbols", hasSymbol)
    }

    @Test
    fun testMultipleUniquePasswords() {
        val passwords = (1..50).map { PasswordSynthesizer.generatePassword() }
        assertEquals(50, passwords.size)
        // High entropy: distinct passwords
        assertTrue(passwords.toSet().size > 45)
    }
}
