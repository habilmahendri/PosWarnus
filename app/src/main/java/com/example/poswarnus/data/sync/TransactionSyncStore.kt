package com.example.poswarnus.data.sync

import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity

/** What the syncer needs from Room; lets JVM tests use a fake. */
interface TransactionSyncStore {
    suspend fun pendingBatch(cashierId: String, after: SyncCursor?, limit: Int): List<PendingSale>
    suspend fun markSynced(id: String, at: Long)
    suspend fun recordFailure(id: String, error: String)
}

data class SyncCursor(val createdAt: Long, val id: String)

data class PendingSale(val header: TransactionEntity, val lines: List<TransactionLineEntity>)

class RoomTransactionSyncStore(private val dao: TransactionDao) : TransactionSyncStore {
    override suspend fun pendingBatch(cashierId: String, after: SyncCursor?, limit: Int): List<PendingSale> =
        dao.pendingBatch(cashierId, after?.createdAt ?: Long.MIN_VALUE, after?.id ?: "", limit)
            .map { PendingSale(it, dao.linesFor(it.id)) }

    override suspend fun markSynced(id: String, at: Long) = dao.markSynced(id, at)

    override suspend fun recordFailure(id: String, error: String) = dao.recordFailure(id, error)
}
