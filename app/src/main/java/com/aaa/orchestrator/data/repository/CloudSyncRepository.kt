package com.aaa.orchestrator.data.repository

import com.aaa.orchestrator.data.model.AccountRecord
import com.aaa.orchestrator.data.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

/**
 * Coordinates multi-cloud fanout:
 * 1. Google Sheets direct API appending
 * 2. Telegram Backup Channel (-1003932377927) batch reports (every 30-50 accounts)
 * 3. Supabase Realtime telemetry
 */
class CloudSyncRepository(
    private val client: OkHttpClient = OkHttpClient()
) {

    private val batchBuffer = mutableListOf<AccountRecord>()
    private var totalSyncedAccounts = 0

    suspend fun syncAccount(account: AccountRecord): SyncStatus = withContext(Dispatchers.IO) {
        batchBuffer.add(account)
        totalSyncedAccounts++

        Timber.i("Account buffered for sync: ${account.username} (Buffer size: ${batchBuffer.size})")

        // Sync immediately to Cloudflare D1 Serverless Database
        com.aaa.orchestrator.engine.CloudIntegrationEngine.syncAccountToCloudflare(account)

        // Sync immediately to Supabase Realtime Database
        com.aaa.orchestrator.engine.CloudIntegrationEngine.syncAccountToSupabase(account)

        // Sync immediately to Firebase Realtime Database
        com.aaa.orchestrator.engine.CloudIntegrationEngine.syncAccountToFirebase(account)

        // Sync immediately to Render Cloud Orchestrator backend
        dispatchRenderCloudSync(account)

        // Trigger Telegram channel batch report every 40 accounts (30-50 range)
        if (batchBuffer.size >= BATCH_DISPATCH_THRESHOLD) {
            val success = dispatchTelegramBatch(batchBuffer.toList())
            if (success) {
                batchBuffer.clear()
                return@withContext SyncStatus.FULLY_SYNCED
            }
        }

        return@withContext SyncStatus.FULLY_SYNCED
    }

    private fun dispatchRenderCloudSync(account: AccountRecord) {
        try {
            val renderPayload = """
                {
                    "username": ${escapeJson(account.username)},
                    "password": ${escapeJson(account.password)},
                    "phoneNumber": ${escapeJson(account.phoneNumberUsed)},
                    "twoFaSecret": ${escapeJson(account.twoFactorSecret)},
                    "cookies": ${escapeJson(account.cookies)}
                }
            """.trimIndent()

            val req = Request.Builder()
                .url("$RENDER_CLOUD_ENDPOINT/api/accounts")
                .post(renderPayload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                Timber.i("Synced account ${account.username} to Render Cloud: ${resp.code}")
            }
        } catch (e: Exception) {
            Timber.w("Failed to sync account to Render Cloud: ${e.message}")
        }
    }

    suspend fun fetchCloudActivePhone(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$RENDER_CLOUD_ENDPOINT/api/phone")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val regex = Regex("\"active_phone\"\\s*:\\s*\"([^\"]+)\"")
                    return@withContext regex.find(body)?.groupValues?.get(1)
                }
            }
        } catch (e: Exception) {
            Timber.w("Error fetching active phone from Render: ${e.message}")
        }
        return@withContext null
    }

    suspend fun fetchCloudLatestOtp(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$RENDER_CLOUD_ENDPOINT/api/otp")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val regex = Regex("\"latest_otp\"\\s*:\\s*\"([^\"]+)\"")
                    return@withContext regex.find(body)?.groupValues?.get(1)
                }
            }
        } catch (e: Exception) {
            Timber.w("Error fetching OTP from Render: ${e.message}")
        }
        return@withContext null
    }

    suspend fun requestNewPhoneFromBot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val emptyBody = "{}".toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$RENDER_CLOUD_ENDPOINT/api/phone/request")
                .post(emptyBody)
                .build()
            client.newCall(req).execute().use { resp ->
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            Timber.w("Error requesting new phone from cloud: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Posts batch text block to Telegram channel -1003932377927 using the standard format.
     */
    private suspend fun dispatchTelegramBatch(accounts: List<AccountRecord>): Boolean = withContext(Dispatchers.IO) {
        try {
            val chunks = accounts.chunked(15)
            var allSucceeded = true

            for ((index, chunk) in chunks.withIndex()) {
                val sb = StringBuilder()
                val partLabel = if (chunks.size > 1) " (Part ${index + 1}/${chunks.size})" else ""
                sb.append("*AAA X-Orchestrator Batch Report*$partLabel\n")
                sb.append("Batch Size: ${chunk.size} accounts (Total: ${accounts.size})\n")
                sb.append("Timestamp: ${System.currentTimeMillis()}\n\n")
                sb.append("```\n")
                for (acc in chunk) {
                    sb.append(acc.toDelimitedLine(":")).append("\n")
                }
                sb.append("```\n")

                val jsonBody = """
                    {
                        "chat_id": "$TELEGRAM_CHANNEL_ID",
                        "text": ${escapeJson(sb.toString())},
                        "parse_mode": "Markdown"
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val success = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Timber.i("Telegram batch chunk ${index + 1}/${chunks.size} dispatched successfully")
                        true
                    } else {
                        Timber.e("Telegram batch chunk ${index + 1} failed: ${response.code} - ${response.body?.string()}")
                        false
                    }
                }
                if (!success) allSucceeded = false
            }

            return@withContext allSucceeded
        } catch (e: Exception) {
            Timber.e(e, "Error sending Telegram batch")
            return@withContext false
        }
    }

    private fun escapeJson(str: String): String {
        return "\"" + str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }

    companion object {
        const val TELEGRAM_BOT_TOKEN = "8923854813:AAGZwm1YAdi9QxwIGor4f0nFZduwBUZOvoM"
        const val TELEGRAM_CHANNEL_ID = "-1003932377927"
        const val RENDER_CLOUD_ENDPOINT = "https://aaa-x-cloud-worker.onrender.com"
        const val BATCH_DISPATCH_THRESHOLD = 40
    }
}
