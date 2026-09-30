package com.aaa.orchestrator.engine

import java.security.SecureRandom
import java.util.Calendar

/**
 * Deterministic, clean password synthesizer modeled after the YouTube workflow:
 * - 10-character alphanumeric length
 * - Contains uppercase, lowercase, and numeric digits
 * - Ends in current day of month (e.g., '27')
 * - Strictly excludes symbols (@, #, :, ;) to prevent buyer spreadsheet delimiter corruption.
 */
object PasswordSynthesizer {

    private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ" // Omitted confusing I, O
    private const val LOWERCASE = "abcdefghjkmnpqrstuvwxyz"  // Omitted confusing l
    private const val DIGITS = "23456789"
    private val ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS
    private val random = SecureRandom()

    fun generatePassword(calendar: Calendar = Calendar.getInstance()): String {
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val daySuffix = dayOfMonth.toString().padStart(2, '0') // e.g. "27" or "05"

        // Required prefix length: 10 - 2 = 8 characters
        val prefixLength = 8
        val chars = CharArray(prefixLength)

        // Ensure at least one uppercase, one lowercase, one digit
        chars[0] = UPPERCASE[random.nextInt(UPPERCASE.length)]
        chars[1] = LOWERCASE[random.nextInt(LOWERCASE.length)]
        chars[2] = DIGITS[random.nextInt(DIGITS.length)]

        for (i in 3 until prefixLength) {
            chars[i] = ALL_CHARS[random.nextInt(ALL_CHARS.length)]
        }

        // Shuffle prefix characters
        for (i in chars.indices) {
            val swapIndex = random.nextInt(chars.size)
            val temp = chars[i]
            chars[i] = chars[swapIndex]
            chars[swapIndex] = temp
        }

        return String(chars) + daySuffix
    }
}
