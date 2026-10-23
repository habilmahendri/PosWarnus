package com.example.poswarnus.data.sync

import androidx.room.Room
import com.example.poswarnus.data.local.AppDatabase
import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomTransactionSyncStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao
    private lateinit var store: RoomTransactionSyncStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.transactionDao()
        store = RoomTransactionSyncStore(dao)
    }

    @After
    fun tearDown() { db.close() }

    private fun header(id: String, createdAt: Long) = TransactionEntity(
        id = id, createdAt = createdAt, cashierId = "u1", subtotal = 1000, taxLabel = "Pajak", taxBasisPoints = 1000,
        taxAmount = 100, grandTotal = 1100, cashReceived = 2000, changeAmount = 900,
    )

    private fun line(id: String, tx: String) = TransactionLineEntity(
        id = id, transactionId = tx, menuItemId = "m", name = id, basePrice = 1000, quantity = 1, selectionsJson = "[]",
    )

    @Test
    fun `pendingBatch returns headers with their lines in order for a null cursor`() = runTest {
        dao.insertSale(header("b", 200), listOf(line("b:0", "b")))
        dao.insertSale(header("a", 100), listOf(line("a:0", "a"), line("a:1", "a")))
        val batch = store.pendingBatch("u1", null, 10)
        assertEquals(listOf("a", "b"), batch.map { it.header.id })
        assertEquals(listOf("a:0", "a:1"), batch[0].lines.map { it.id })
        assertEquals(listOf("b:0"), batch[1].lines.map { it.id })
        assertEquals(listOf("b"), store.pendingBatch("u1", SyncCursor(100, "a"), 10).map { it.header.id })
    }

    @Test
    fun `markSynced and recordFailure delegate to the dao`() = runTest {
        dao.insertSale(header("a", 100), listOf(line("a:0", "a")))
        dao.insertSale(header("b", 200), listOf(line("b:0", "b")))
        store.recordFailure("a", "offline: IOException: x")
        store.markSynced("b", 777L)
        val rows = dao.observeAll().first().associateBy { it.id }
        assertEquals(1, rows.getValue("a").syncAttempts)
        assertEquals("offline: IOException: x", rows.getValue("a").lastSyncError)
        assertEquals(SyncStatus.PENDING, rows.getValue("a").syncStatus)
        assertEquals(SyncStatus.SYNCED, rows.getValue("b").syncStatus)
        assertEquals(777L, rows.getValue("b").syncedAt)
        assertNull(rows.getValue("b").lastSyncError)
    }
}
