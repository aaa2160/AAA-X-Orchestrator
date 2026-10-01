package com.aaa.orchestrator.data.repository

import com.aaa.orchestrator.data.local.AccountDao
import com.aaa.orchestrator.data.local.AccountEntity
import com.aaa.orchestrator.data.model.AccountRecord
import com.aaa.orchestrator.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository coordinating local Room DB persistence.
 */
class AccountRepository(private val accountDao: AccountDao) {

    val allAccounts: Flow<List<AccountRecord>> = accountDao.getAllAccountsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun saveAccount(record: AccountRecord): Long {
        val entity = AccountEntity.fromDomain(record)
        return accountDao.insert(entity)
    }

    suspend fun updateSyncStatus(id: Long, status: SyncStatus) {
        val pending = accountDao.getPendingSyncAccounts().find { it.id == id }
        if (pending != null) {
            accountDao.update(pending.copy(syncStatus = status.name))
        }
    }

    suspend fun getPendingSyncAccounts(): List<AccountRecord> {
        return accountDao.getPendingSyncAccounts().map { it.toDomain() }
    }

    suspend fun getTotalAccountCount(): Int {
        return accountDao.getAccountCount()
    }

    suspend fun getPendingCount(): Int {
        return accountDao.getPendingCount()
    }

    suspend fun deleteAccountById(id: Long) {
        accountDao.deleteById(id)
    }

    suspend fun deleteAllAccounts() {
        accountDao.deleteAll()
    }
}
