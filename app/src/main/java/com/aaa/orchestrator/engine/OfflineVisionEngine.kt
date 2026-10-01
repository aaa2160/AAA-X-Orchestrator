package com.aaa.orchestrator.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * On-device, offline Google ML Kit Text Recognition Vision Engine.
 * Operates completely offline on Samsung Galaxy A30 hardware without requiring cloud roundtrips.
 * Used for screen OCR, virtual SIM number detection, and OTP captcha parsing.
 */
object OfflineVisionEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs offline text recognition on the provided screen capture Bitmap.
     */
    suspend fun recognizeScreenText(bitmap: Bitmap): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        Timber.i("ML Kit OCR recognized ${text.length} characters offline")
                        continuation.resume(Result.success(text))
                    }
                    .addOnFailureListener { error ->
                        Timber.e(error, "ML Kit OCR failed")
                        continuation.resume(Result.failure(error))
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error processing image for OCR")
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Extracts international phone numbers from OCR screen capture (Telegram bot buttons, messages).
     */
    fun extractInternationalPhoneNumber(ocrText: String): String? {
        if (ocrText.isBlank()) return null
        val match = Regex("\\+([0-9]{9,15})").find(ocrText)
        if (match != null) return match.value

        val regex = Regex("(\\+[0-9]{1,4}[\\s-]?)?([0-9]{3}[\\s-]?[0-9]{3}[\\s-]?[0-9]{3,4})")
        val fallbackMatch = regex.find(ocrText)?.value ?: return null
        val digits = fallbackMatch.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) "+$digits" else null
    }

    /**
     * Legacy compatibility wrapper pointing to international Telegram Bot extractor.
     */
    fun extractPolishPhoneNumber(ocrText: String): String? {
        val direct = extractInternationalPhoneNumber(ocrText)
        if (direct != null) return direct

        val regex = Regex("(\\+48[\\s-]?[0-9]{3}[\\s-]?[0-9]{3}[\\s-]?[0-9]{3}|[0-9]{3}[\\s-]?[0-9]{3}[\\s-]?[0-9]{3})")
        val match = regex.find(ocrText)?.value ?: return null
        val digits = match.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 11 && digits.startsWith("48") -> "+$digits"
            digits.length == 9 -> "+48$digits"
            digits.length == 12 && digits.startsWith("0048") -> "+${digits.substring(2)}"
            else -> if (digits.length >= 9) "+48${digits.takeLast(9)}" else null
        }
    }
}
