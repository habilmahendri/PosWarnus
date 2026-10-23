package com.example.poswarnus.data.sync

import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.remote.NoSessionException
import com.example.poswarnus.data.remote.TransactionRemoteDataSource
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSyncerTest {
    private fun sale(id: String, createdAt: Long, cashier: String = "u1") = PendingSale(
        header = TransactionEntity(
            id = id, createdAt = createdAt, cashierId = cashier, subtotal = 1000, taxLabel = "Pajak", taxBasisPoints = 1000,
            taxAmount = 100, grandTotal = 1100, cashReceived = 2000, changeAmount = 900,
        ),
        lines = listOf(
            TransactionLineEntity(
                id = "$id:0", transactionId = id, menuItemId = "m", name = "N", basePrice = 1000, quantity = 1, selectionsJson = "[]",
            ),
        ),
    )

    private class FakeStore(sales: List<PendingSale>) : TransactionSyncStore {
        private val rows = sales.toMutableList()
        private val synced = mutableSetOf<String>()
        val cursors = mutableListOf<SyncCursor?>()
        val cashierIds = mutableListOf<String>()
        val syncedCalls = mutableListOf<Pair<String, Long>>()
        val failureCalls = mutableListOf<Pair<String, String>>()
        var calls = 0

        override suspend fun pendingBatch(cashierId: String, after: SyncCursor?, limit: Int): List<PendingSale> {
            calls++
            cursors += after
            cashierIds += cashierId
            return rows
                .filter { it.header.id !in synced && it.header.cashierId == cashierId && !it.header.isTraining }
                .sortedWith(compareBy({ it.header.createdAt }, { it.header.id }))
                .filter { after == null || it.header.createdAt > after.createdAt || (it.header.createdAt == after.createdAt && it.header.id > after.id) }
                .take(limit)
        }

        override suspend fun markSynced(id: String, at: Long) {
            calls++
            yield()
            synced += id
            syncedCalls += id to at
        }

        override suspend fun recordFailure(id: String, error: String) {
            calls++
            failureCalls += id to error
        }
    }

    private class FakeRemote(private val script: Map<String, Throwable> = emptyMap(), private val onSuccess: suspend (String) -> Unit = {}) :
        TransactionRemoteDataSource {
        val submitted = mutableListOf<String>()

        override suspend fun submit(sale: PendingSale) {
            submitted += sale.header.id
            script[sale.header.id]?.let { throw it }
            onSuccess(sale.header.id)
        }
    }

    private suspend fun rest(status: HttpStatusCode): RestException {
        val client = HttpClient(MockEngine { respond(content = "", status = status) })
        return RestException("err", "desc", client.get("https://x.example/rpc"))
    }

    private fun syncer(
        store: TransactionSyncStore,
        remote: TransactionRemoteDataSource,
        cashier: String? = "u1",
        batchSize: Int = 20,
        stopsBatch: (Throwable) -> Boolean = SyncErrors::stopsBatch,
    ) = TransactionSyncer(store, remote, { cashier }, now = { 42L }, stopsBatch = stopsBatch, batchSize = batchSize)

    @Test
    fun `uploads in createdAt order and marks SYNCED with now`() = runTest {
        val store = FakeStore(listOf(sale("c", 300), sale("a", 100), sale("b", 200)))
        val remote = FakeRemote()
        val result = syncer(store, remote).syncPending()
        assertEquals(listOf("a", "b", "c"), remote.submitted)
        assertEquals(listOf("a" to 42L, "b" to 42L, "c" to 42L), store.syncedCalls)
        assertEquals(SyncResult(3, 0, false), result)
        assertFalse(result.needsRetry)
    }

    @Test
    fun `a rejected row records rejected prefix and the next row still uploads`() = runTest {
        val store = FakeStore(listOf(sale("a", 100), sale("b", 200)))
        val remote = FakeRemote(mapOf("a" to rest(HttpStatusCode.Forbidden)))
        val result = syncer(store, remote).syncPending()
        assertEquals(1, store.failureCalls.size)
        assertEquals("a", store.failureCalls[0].first)
        assertTrue(store.failureCalls[0].second.startsWith("rejected:"))
        assertEquals(listOf("b" to 42L), store.syncedCalls)
        assertEquals(SyncResult(synced = 1, failed = 1, stoppedEarly = false), result)
        assertTrue(result.needsRetry)
    }

    @Test
    fun `an IOException records offline prefix and stops the batch`() = runTest {
        val store = FakeStore(listOf(sale("a", 100), sale("b", 200), sale("c", 300)))
        val remote = FakeRemote(mapOf("b" to IOException("down")))
        val result = syncer(store, remote).syncPending()
        assertEquals(listOf("a", "b"), remote.submitted)
        assertEquals(listOf("a" to 42L), store.syncedCalls)
        assertEquals(1, store.failureCalls.size)
        assertEquals("b", store.failureCalls[0].first)
        assertTrue(store.failureCalls[0].second.startsWith("offline:"))
        assertEquals(SyncResult(synced = 1, failed = 0, stoppedEarly = true), result)
        assertTrue(result.needsRetry)
    }

    @Test
    fun `an offline error on the first row leaves later rows untouched`() = runTest {
        val store = FakeStore(listOf(sale("a", 100), sale("b", 200)))
        val remote = FakeRemote(mapOf("a" to IOException("down")))
        val result = syncer(store, remote).syncPending()
        assertEquals(listOf("a"), remote.submitted)
        assertEquals(1, store.failureCalls.size)
        assertTrue(store.syncedCalls.isEmpty())
        assertTrue(result.stoppedEarly)
    }

    @Test
    fun `NoSessionException records session prefix and stops`() = runTest {
        val store = FakeStore(listOf(sale("a", 100), sale("b", 200)))
        val remote = FakeRemote(mapOf("a" to NoSessionException()))
        val result = syncer(store, remote).syncPending()
        assertEquals(listOf("a"), remote.submitted)
        assertTrue(store.failureCalls.single().second.startsWith("session:"))
        assertTrue(result.stoppedEarly)
    }

    @Test
    fun `no cashier id returns stoppedEarly with zero store reads and writes`() = runTest {
        val store = FakeStore(listOf(sale("a", 100)))
        val remote = FakeRemote()
        val result = syncer(store, remote, cashier = null).syncPending()
        assertEquals(SyncResult(0, 0, stoppedEarly = true), result)
        assertEquals(0, store.calls)
        assertTrue(remote.submitted.isEmpty())
    }

    @Test
    fun `rows of another cashier are never fetched`() = runTest {
        val store = FakeStore(listOf(sale("mine", 100, "u1"), sale("theirs", 50, "u2")))
        val remote = FakeRemote()
        syncer(store, remote, cashier = "u1").syncPending()
        assertEquals(listOf("mine"), remote.submitted)
        assertTrue(store.cashierIds.all { it == "u1" })
    }

    @Test
    fun `more rows than batchSize all upload with keyset cursors`() = runTest {
        val store = FakeStore((1..5).map { sale("s$it", it * 100L) })
        val remote = FakeRemote()
        val result = syncer(store, remote, batchSize = 2).syncPending()
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), remote.submitted)
        assertEquals(
            listOf(null, SyncCursor(200, "s2"), SyncCursor(400, "s4"), SyncCursor(500, "s5")),
            store.cursors,
        )
        assertEquals(SyncResult(5, 0, false), result)
    }

    @Test
    fun `a failed row is not re-read in the same run`() = runTest {
        val store = FakeStore((1..5).map { sale("s$it", it * 100L) })
        val remote = FakeRemote(mapOf("s1" to rest(HttpStatusCode.NotFound)))
        val result = syncer(store, remote, batchSize = 2).syncPending()
        assertEquals(1, remote.submitted.count { it == "s1" })
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), remote.submitted)
        assertEquals(SyncResult(synced = 4, failed = 1, stoppedEarly = false), result)
    }

    @Test
    fun `cancellation propagates and records nothing for that row`() = runTest {
        val store = FakeStore(listOf(sale("a", 100)))
        val remote = FakeRemote(mapOf("a" to CancellationException("cancelled")))
        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { syncer(store, remote).syncPending() } }
        assertTrue(store.failureCalls.isEmpty())
        assertTrue(store.syncedCalls.isEmpty())
    }

    @Test
    fun `a success followed by cancellation still marks SYNCED`() = runTest(UnconfinedTestDispatcher()) {
        val store = FakeStore(listOf(sale("a", 100), sale("b", 200)))
        val remote = FakeRemote(onSuccess = { currentCoroutineContext().job.cancel() })
        var result: SyncResult? = null
        val job = launch { result = syncer(store, remote).syncPending() }
        job.join()
        assertTrue(job.isCancelled)
        assertNull(result)
        assertEquals(listOf("a" to 42L), store.syncedCalls)
        assertEquals(listOf("a"), remote.submitted)
    }

    @Test
    fun `empty queue returns needsRetry false`() = runTest {
        val result = syncer(FakeStore(emptyList()), FakeRemote()).syncPending()
        assertEquals(SyncResult(0, 0, false), result)
        assertFalse(result.needsRetry)
    }

    @Test
    fun `a custom stopsBatch predicate is honoured`() = runTest {
        val store = FakeStore(listOf(sale("a", 100), sale("b", 200)))
        val remote = FakeRemote(mapOf("a" to IllegalStateException("odd")))
        val result = syncer(store, remote, stopsBatch = { true }).syncPending()
        assertEquals(listOf("a"), remote.submitted)
        assertTrue(result.stoppedEarly)
    }
}
