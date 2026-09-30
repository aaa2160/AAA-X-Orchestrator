package com.aaa.orchestrator.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Crash-proof in-memory fallback implementation of AccountDao.
 * Activates automatically if SQLite / Room database initialization encounters device storage locks.
 */
class InMemoryAccountDao : AccountDao {

    private val idCounter = AtomicLong(1)
    private val accountsMap = ConcurrentHashMap<Long, AccountEntity>()
    private val _accountsFlow = MutableStateFlow<List<AccountEntity>>(emptyList())

    private fun updateFlow() {
        _accountsFlow.value = accountsMap.values.sortedByDescending { it.createdAt }
    }

    override suspend fun insert(account: AccountEntity): Long {
        val id = if (account.id > 0) account.id else idCounter.getAndIncrement()
        val toSave = account.copy(id = id)
        accountsMap[id] = toSave
        updateFlow()
        return id
    }

    override suspend fun update(account: AccountEntity) {
        accountsMap[account.id] = account
        updateFlow()
    }

    override fun getAllAccountsFlow(): Flow<List<AccountEntity>> {
        return _accountsFlow.asStateFlow()
    }

    override suspend fun getPendingSyncAccounts(): List<AccountEntity> {
        return accountsMap.values.filter { it.syncStatus == "PENDING_SYNC" }
            .sortedBy { it.createdAt }
    }

    override suspend fun getAccountCount(): Int {
        return accountsMap.size
    }

    override suspend fun getPendingCount(): Int {
        return accountsMap.values.count { it.syncStatus == "PENDING_SYNC" }
    }

    override suspend fun deleteById(id: Long) {
        accountsMap.remove(id)
        updateFlow()
    }
}
