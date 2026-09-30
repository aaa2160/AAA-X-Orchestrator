package com.aaa.orchestrator.engine

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * High-performance, pure Kotlin/Java implementation of RFC 6238 Time-Based One-Time Password (TOTP)
 * using HMAC-SHA1 algorithm.
 * Operates entirely in-memory with sub-millisecond execution time and zero external dependencies.
 */
object TotpGenerator {

    private const val TIME_STEP_SECONDS = 30L
    private const val DIGITS = 6
    private const val HMAC_ALGORITHM = "HmacSHA1"

    /**
     * Generates a 6-digit TOTP code for the specified Base32 secret at the given Unix epoch timestamp.
     */
    fun generateCurrentCode(base32Secret: String, timestampMs: Long = System.currentTimeMillis()): String {
        val cleanSecret = base32Secret.replace(" ", "").uppercase()
        if (cleanSecret.isEmpty()) return "000000"
        val keyBytes = decodeBase32(cleanSecret)
        if (keyBytes.isEmpty()) return "000000"
        val timeIndex = timestampMs / 1000L / TIME_STEP_SECONDS
        return try {
            generateCodeForTimeIndex(keyBytes, timeIndex)
        } catch (e: Exception) {
            "000000"
        }
    }

    /**
     * Returns the remaining seconds in the current 30-second TOTP interval (0 to 30).
     */
    fun getRemainingSeconds(timestampMs: Long = System.currentTimeMillis()): Int {
        val currentSeconds = (timestampMs / 1000L) % TIME_STEP_SECONDS
        return (TIME_STEP_SECONDS - currentSeconds).toInt()
    }

    /**
     * Returns progress ratio between 0.0f and 1.0f for live UI countdown ring.
     */
    fun getRemainingProgress(timestampMs: Long = System.currentTimeMillis()): Float {
        return getRemainingSeconds(timestampMs) / TIME_STEP_SECONDS.toFloat()
    }

    private fun generateCodeForTimeIndex(keyBytes: ByteArray, timeIndex: Long): String {
        val data = ByteBuffer.allocate(8).putLong(timeIndex).array()
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, HMAC_ALGORITHM))
        val hash = mac.doFinal(data)

        // Dynamic truncation as specified in RFC 4226 Section 5.4
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000
        return otp.toString().padStart(DIGITS, '0')
    }

    /**
     * Decodes standard RFC 4648 Base32 alphabet (A-Z, 2-7).
     */
    fun decodeBase32(input: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = input.trim().replace("=", "")
        var buffer = 0
        var bitsLeft = 0
        val output = ArrayList<Byte>()

        for (c in clean) {
            val valIndex = base32Chars.indexOf(c)
            if (valIndex < 0) continue

            buffer = (buffer shl 5) or (valIndex and 31)
            bitsLeft += 5

            if (bitsLeft >= 8) {
                val byteVal = (buffer shr (bitsLeft - 8)) and 0xFF
                output.add(byteVal.toByte())
                bitsLeft -= 8
            }
        }

        val result = ByteArray(output.size)
        for (i in output.indices) {
            result[i] = output[i]
        }
        return result
    }
}
