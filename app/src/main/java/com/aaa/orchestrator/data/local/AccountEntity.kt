package com.aaa.orchestrator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aaa.orchestrator.data.model.AccountRecord
import com.aaa.orchestrator.data.model.SyncStatus

/**
 * Room database entity storing accounts locally before and after cloud sync.
 * Guarantees zero dropped records even if Google Sheets rate limits or network drops.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val password: String,
    val twoFactorSecret: String,
    val cookies: String,
    val phoneNumberUsed: String,
    val createdAt: Long,
    val syncStatus: String
) {
    fun toDomain(): AccountRecord {
        return AccountRecord(
            id = id,
            username = username,
            password = password,
            twoFactorSecret = twoFactorSecret,
            cookies = cookies,
            phoneNumberUsed = phoneNumberUsed,
            createdAt = createdAt,
            syncStatus = try {
                SyncStatus.valueOf(syncStatus)
            } catch (e: Exception) {
                SyncStatus.PENDING_SYNC
            }
        )
    }

    companion object {
        fun fromDomain(record: AccountRecord): AccountEntity {
            return AccountEntity(
                id = record.id,
                username = record.username,
                password = record.password,
                twoFactorSecret = record.twoFactorSecret,
                cookies = record.cookies,
                phoneNumberUsed = record.phoneNumberUsed,
                createdAt = record.createdAt,
                syncStatus = record.syncStatus.name
            )
        }
    }
}
