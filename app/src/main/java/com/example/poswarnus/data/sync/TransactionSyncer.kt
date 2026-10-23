package com.example.poswarnus.data.sync

import com.example.poswarnus.data.remote.TransactionRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class SyncResult(val synced: Int, val failed: Int, val stoppedEarly: Boolean) {
    /** True when something is still PENDING because of an error in this run. */
    val needsRetry: Boolean get() = failed > 0 || stoppedEarly
}

class TransactionSyncer(
    private val store: TransactionSyncStore,
    private val remote: TransactionRemoteDataSource,
    private val currentCashierId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val stopsBatch: (Throwable) -> Boolean = SyncErrors::stopsBatch,
    private val batchSize: Int = 20,
) {
    /** Walks the PENDING, non-training sales of the signed-in cashier in createdAt order. Throws CancellationException or a store (Room) error; the caller (the worker) handles both. */
    suspend fun syncPending(): SyncResult {
        val cashierId = currentCashierId() ?: return SyncResult(0, 0, stoppedEarly = true)
        var synced = 0
        var failed = 0
        var cursor: SyncCursor? = null
        while (true) {
            val batch = store.pendingBatch(cashierId, cursor, batchSize)
            if (batch.isEmpty()) break
            for (sale in batch) {
                val id = sale.header.id
                cursor = SyncCursor(sale.header.createdAt, id)
                try {
                    remote.submit(sale)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(NonCancellable) { store.recordFailure(id, SyncErrors.describe(e)) }
                    if (stopsBatch(e)) return SyncResult(synced, failed, stoppedEarly = true)
                    failed++
                    continue
                }
                withContext(NonCancellable) { store.markSynced(id, now()) }
                synced++
                currentCoroutineContext().ensureActive()
            }
        }
        return SyncResult(synced, failed, stoppedEarly = false)
    }
}
