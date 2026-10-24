package com.example.poswarnus.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun header(
        id: String = "t1",
        isTraining: Boolean = false,
        createdAt: Long = 1_000L,
        cashierId: String = "u1",
    ) = TransactionEntity(
        id = id, createdAt = createdAt, cashierId = cashierId, subtotal = 43000, taxLabel = "Pajak", taxBasisPoints = 1000,
        taxAmount = 4300, grandTotal = 47300, cashReceived = 50000, changeAmount = 2700, isTraining = isTraining,
    )

    private fun line(id: String, tx: String = "t1", name: String = "Nasi Goreng") = TransactionLineEntity(
        id = id, transactionId = tx, menuItemId = "m1", name = name, basePrice = 25000, quantity = 1, selectionsJson = "[]",
    )

    @Test
    fun `insertSale stores header and lines`() = runTest {
        assertTrue(dao.insertSale(header(), listOf(line("t1:0"), line("t1:1", name = "Es Teh"))))
        assertEquals(listOf("t1"), dao.observeAll().first().map { it.id })
        assertEquals(listOf("Nasi Goreng", "Es Teh"), dao.linesFor("t1").map { it.name })
    }

    @Test
    fun `insertSale with an existing id inserts nothing and returns false`() = runTest {
        dao.insertSale(header(), listOf(line("t1:0")))
        assertFalse(dao.insertSale(header(), listOf(line("t1:9", name = "Other"))))
        assertEquals(listOf("Nasi Goreng"), dao.linesFor("t1").map { it.name })
    }

    @Test
    fun `a failing line insert rolls the header back`() = runTest {
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { dao.insertSale(header(), listOf(line("dup"), line("dup"))) }
        }
        assertEquals(emptyList<TransactionEntity>(), dao.observeAll().first())
    }

    @Test
    fun `isTraining round-trips and defaults to false`() = runTest {
        dao.insertSale(header("a"), emptyList())
        dao.insertSale(header("b", isTraining = true), emptyList())
        val byId = dao.observeAll().first().associateBy { it.id }
        assertFalse(byId.getValue("a").isTraining)
        assertTrue(byId.getValue("b").isTraining)
    }

    @Test
    fun `tax settings replace overwrites the single row`() = runTest {
        val taxDao = db.taxSettingsDao()
        assertNull(taxDao.observe().first())
        taxDao.replace(TaxSettingsEntity(label = "Pajak", basisPoints = 1000))
        taxDao.replace(TaxSettingsEntity(label = "Service", basisPoints = 750))
        assertEquals(TaxSettingsEntity(1, "Service", 750), taxDao.observe().first())
    }

    private suspend fun seedPendingOrdering() {
        dao.insertSale(header("a", createdAt = 2), emptyList())
        dao.insertSale(header("b", createdAt = 1), emptyList())
        dao.insertSale(header("c", createdAt = 1), emptyList())
        dao.insertSale(header("d", createdAt = 1, cashierId = "u2"), emptyList())
        dao.insertSale(header("e", createdAt = 1, isTraining = true), emptyList())
        dao.insertSale(header("f", createdAt = 1), emptyList())
        dao.markSynced("f", 9_000)
    }

    private suspend fun row(id: String) = dao.observeAll().first().single { it.id == id }

    @Test
    fun `pendingBatch returns only PENDING non-training rows of the cashier ordered by createdAt then id`() = runTest {
        seedPendingOrdering()
        assertEquals(listOf("b", "c", "a"), dao.pendingBatch("u1", Long.MIN_VALUE, "", 10).map { it.id })
    }

    @Test
    fun `pendingBatch keyset cursor skips rows up to and including the cursor`() = runTest {
        seedPendingOrdering()
        assertEquals(listOf("c", "a"), dao.pendingBatch("u1", 1, "b", 10).map { it.id })
    }

    @Test
    fun `pendingBatch honours limit`() = runTest {
        seedPendingOrdering()
        assertEquals(listOf("b", "c"), dao.pendingBatch("u1", Long.MIN_VALUE, "", 2).map { it.id })
    }

    @Test
    fun `markSynced sets status and time and clears the error`() = runTest {
        dao.insertSale(header("x"), emptyList())
        dao.recordFailure("x", "offline: IOException")
        dao.markSynced("x", 5_000)
        val r = row("x")
        assertEquals("SYNCED", r.syncStatus)
        assertEquals(5_000L, r.syncedAt)
        assertNull(r.lastSyncError)
    }

    @Test
    fun `recordFailure increments attempts and stores the error`() = runTest {
        dao.insertSale(header("x"), emptyList())
        dao.recordFailure("x", "offline: IOException")
        dao.recordFailure("x", "rejected: RestException")
        val r = row("x")
        assertEquals(2, r.syncAttempts)
        assertEquals("rejected: RestException", r.lastSyncError)
        assertEquals("PENDING", r.syncStatus)
    }

    @Test
    fun `observeSyncSummary counts pending and offline rows and returns the latest syncedAt`() = runTest {
        dao.insertSale(header("p1"), emptyList())
        dao.recordFailure("p1", "offline: x")
        dao.insertSale(header("p2"), emptyList())
        dao.recordFailure("p2", "rejected: y")
        dao.insertSale(header("p3"), emptyList())
        dao.insertSale(header("s1"), emptyList())
        dao.markSynced("s1", 7_000)
        dao.insertSale(header("s2"), emptyList())
        dao.markSynced("s2", 3_000)
        dao.insertSale(header("tr", isTraining = true), emptyList())
        dao.recordFailure("tr", "offline: z")
        assertEquals(SyncSummaryRow(3, 1, 7_000L), dao.observeSyncSummary("u1").first())
    }

    @Test
    fun `observeSyncSummary on an empty table gives zeros and null`() = runTest {
        assertEquals(SyncSummaryRow(0, 0, null), dao.observeSyncSummary("u1").first())
    }

    @Test
    fun `observeSyncSummary counts only the given cashier and gives zeros to one without rows`() = runTest {
        dao.insertSale(header("a1", cashierId = "uA"), emptyList())
        dao.recordFailure("a1", "offline: x")
        dao.insertSale(header("a2", cashierId = "uA"), emptyList())
        dao.markSynced("a2", 4_000)
        dao.insertSale(header("b1", cashierId = "uB"), emptyList())
        dao.insertSale(header("b2", cashierId = "uB"), emptyList())
        dao.markSynced("b2", 8_000)
        dao.insertSale(header("trA", cashierId = "uA", isTraining = true), emptyList())
        dao.recordFailure("trA", "offline: z")
        assertEquals(SyncSummaryRow(1, 1, 4_000L), dao.observeSyncSummary("uA").first())
        assertEquals(SyncSummaryRow(1, 0, 8_000L), dao.observeSyncSummary("uB").first())
        assertEquals(SyncSummaryRow(0, 0, null), dao.observeSyncSummary("uC").first())
    }

    @Test
    fun `observeHistory returns newest first with id as tie-break`() = runTest {
        dao.insertSale(header("b", createdAt = 5), listOf(line("b:0", tx = "b")))
        dao.insertSale(header("a", createdAt = 5), emptyList())
        dao.insertSale(header("c", createdAt = 9), emptyList())
        dao.insertSale(header("d", createdAt = 1), emptyList())
        val history = dao.observeHistory("u1", Long.MIN_VALUE).first()
        assertEquals(listOf("c", "a", "b", "d"), history.map { it.header.id })
        assertEquals(listOf("b:0"), history.single { it.header.id == "b" }.lines.map { it.id })
    }

    @Test
    fun `observeHistory only returns the requested cashier`() = runTest {
        dao.insertSale(header("a", cashierId = "u1"), emptyList())
        dao.insertSale(header("b", cashierId = "u2"), emptyList())
        assertEquals(listOf("a"), dao.observeHistory("u1", Long.MIN_VALUE).first().map { it.header.id })
        assertEquals(emptyList<String>(), dao.observeHistory("u3", Long.MIN_VALUE).first().map { it.header.id })
    }

    @Test
    fun `observeHistory excludes training sales`() = runTest {
        dao.insertSale(header("a"), emptyList())
        dao.insertSale(header("t", isTraining = true), emptyList())
        assertEquals(listOf("a"), dao.observeHistory("u1", Long.MIN_VALUE).first().map { it.header.id })
    }

    @Test
    fun `observeHistory fromMillis is inclusive`() = runTest {
        dao.insertSale(header("early", createdAt = 99), emptyList())
        dao.insertSale(header("edge", createdAt = 100), emptyList())
        assertEquals(listOf("edge"), dao.observeHistory("u1", 100).first().map { it.header.id })
    }

    @Test
    fun `new rows default to PENDING with zero attempts`() = runTest {
        dao.insertSale(header(), listOf(line("t1:0")))
        val r = row("t1")
        assertEquals("PENDING", r.syncStatus)
        assertEquals(0, r.syncAttempts)
        assertNull(r.syncedAt)
        assertNull(r.lastSyncError)
    }
}
