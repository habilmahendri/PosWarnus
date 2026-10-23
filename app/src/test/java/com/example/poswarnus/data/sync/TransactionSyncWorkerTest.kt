package com.example.poswarnus.data.sync

import android.content.Context
import org.robolectric.RuntimeEnvironment
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.remote.TransactionRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class TransactionSyncWorkerTest {
    private val events = mutableListOf<String>()

    private fun sale(id: String, createdAt: Long) = PendingSale(
        header = TransactionEntity(
            id = id, createdAt = createdAt, cashierId = "u1", subtotal = 1000, taxLabel = "Pajak", taxBasisPoints = 1000,
            taxAmount = 100, grandTotal = 1100, cashReceived = 2000, changeAmount = 900,
        ),
        lines = listOf(
            TransactionLineEntity(
                id = "$id:0", transactionId = id, menuItemId = "m", name = "N", basePrice = 1000, quantity = 1, selectionsJson = "[]",
            ),
        ),
    )

    private inner class FakeStore(sales: List<PendingSale>) : TransactionSyncStore {
        private val rows = sales.toMutableList()
        private val synced = mutableSetOf<String>()
        var failOnMarkSynced: Throwable? = null

        override suspend fun pendingBatch(cashierId: String, after: SyncCursor?, limit: Int): List<PendingSale> {
            events += "pendingBatch"
            return rows
                .filter { it.header.id !in synced }
                .sortedWith(compareBy({ it.header.createdAt }, { it.header.id }))
                .filter { after == null || it.header.createdAt > after.createdAt || (it.header.createdAt == after.createdAt && it.header.id > after.id) }
                .take(limit)
        }

        override suspend fun markSynced(id: String, at: Long) {
            failOnMarkSynced?.let { throw it }
            synced += id
        }

        override suspend fun recordFailure(id: String, error: String) = Unit
    }

    private class FakeRemote(private val failure: Throwable? = null) : TransactionRemoteDataSource {
        override suspend fun submit(sale: PendingSale) {
            failure?.let { throw it }
        }
    }

    private inner class FakeAuth : AuthRepository {
        override suspend fun login(email: String, password: String): AuthResult = AuthResult.Success
        override suspend fun logout() = Unit
        override suspend fun isLoggedIn(): Boolean = true
        override suspend fun ensureSessionForSync(): Boolean {
            events += "ensureSession"
            return true
        }
        override fun currentUserId(): String? = "u1"
    }

    private fun run(store: FakeStore, remote: TransactionRemoteDataSource): ListenableWorker.Result {
        val syncer = TransactionSyncer(store, remote, { "u1" }, now = { 42L })
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                TransactionSyncWorker(appContext, workerParameters, FakeAuth(), syncer)
        }
        val worker = TestListenableWorkerBuilder<TransactionSyncWorker>(RuntimeEnvironment.getApplication())
            .setWorkerFactory(factory)
            .build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun `returns retry when the syncer reports needsRetry`() {
        val result = run(FakeStore(listOf(sale("a", 1))), FakeRemote(IOException("offline")))
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `returns success when nothing is left to retry`() {
        assertEquals(ListenableWorker.Result.success(), run(FakeStore(emptyList()), FakeRemote()))
        assertEquals(ListenableWorker.Result.success(), run(FakeStore(listOf(sale("a", 1), sale("b", 2))), FakeRemote()))
    }

    @Test
    fun `returns retry instead of crashing when the syncer throws`() {
        val store = FakeStore(listOf(sale("a", 1))).apply { failOnMarkSynced = RuntimeException("room broke") }
        assertEquals(ListenableWorker.Result.retry(), run(store, FakeRemote()))
    }

    @Test
    fun `does not swallow cancellation`() {
        val store = FakeStore(listOf(sale("a", 1))).apply { failOnMarkSynced = CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { run(store, FakeRemote()) }
    }

    @Test
    fun `calls ensureSessionForSync before syncing`() {
        run(FakeStore(listOf(sale("a", 1))), FakeRemote())
        assertEquals("ensureSession", events.first())
        assertTrue(events.contains("pendingBatch"))
    }
}
