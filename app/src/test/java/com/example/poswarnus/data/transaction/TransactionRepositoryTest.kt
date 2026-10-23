package com.example.poswarnus.data.transaction

import androidx.room.Room
import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.local.AppDatabase
import com.example.poswarnus.data.local.SyncSummaryRow
import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.sync.FakeSyncScheduler
import com.example.poswarnus.data.tax.TaxConfig
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.OrderTotals
import com.example.poswarnus.ui.cart.SelectedOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepositoryImpl
    private val scheduler = FakeSyncScheduler()

    private val large = SelectedOption("o1", "g1", "Large", 5000, OptionKind.VARIANT)
    private val lines = listOf(
        CartLine("l1", "m1", "Nasi Goreng", 25000, listOf(large), 1),
        CartLine("l2", "m2", "Es Teh", 5000, emptyList(), 2),
    )
    private val totals = OrderTotals.of(lines.sumOf { it.lineTotal }, TaxConfig("Pajak", 1000))
    private val sale = SaleSnapshot("tx-1", 1_000L, "user-1", lines, totals)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = TransactionRepositoryImpl(db.transactionDao(), scheduler)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `save writes header and lines with change and totals`() = runTest {
        // subtotal 25000+5000 + 2*5000 = 40000, tax 4000, grand 44000
        assertTrue(repository.save(sale, cashReceived = 50000).isSuccess)

        val header = db.transactionDao().observeAll().first().single()
        assertEquals("tx-1", header.id)
        assertEquals("user-1", header.cashierId)
        assertEquals(40000L, header.subtotal)
        assertEquals("Pajak", header.taxLabel)
        assertEquals(1000, header.taxBasisPoints)
        assertEquals(4000L, header.taxAmount)
        assertEquals(44000L, header.grandTotal)
        assertEquals(50000L, header.cashReceived)
        assertEquals(6000L, header.changeAmount)
        assertFalse(header.isTraining)

        val stored = db.transactionDao().linesFor("tx-1")
        assertEquals(listOf("Nasi Goreng", "Es Teh"), stored.map { it.name })
        assertEquals(listOf(25000L, 5000L), stored.map { it.basePrice })
        assertEquals(listOf(1, 2), stored.map { it.quantity })
        assertEquals(listOf("m1", "m2"), stored.map { it.menuItemId })
        assertEquals(listOf("tx-1:0", "tx-1:1"), stored.map { it.id })
    }

    @Test
    fun `saving the same snapshot twice leaves one row and succeeds both times`() = runTest {
        assertTrue(repository.save(sale, 50000).isSuccess)
        assertTrue(repository.save(sale, 50000).isSuccess)
        assertEquals(1, db.transactionDao().observeAll().first().size)
        assertEquals(2, db.transactionDao().linesFor("tx-1").size)
    }

    @Test
    fun `selections are stored as the snapshot json`() = runTest {
        repository.save(sale, 50000)
        val first = db.transactionDao().linesFor("tx-1").first()
        assertEquals(listOf(large), SelectionsJson.decode(first.selectionsJson))
    }

    @Test
    fun `save returns failure when the dao throws`() = runTest {
        val failing = failingDao()
        val failScheduler = FakeSyncScheduler()
        val result = TransactionRepositoryImpl(failing, failScheduler).save(sale, 50000)
        assertTrue(result.isFailure)
        assertEquals("disk full", result.exceptionOrNull()?.message)
        assertEquals(0, failScheduler.requestSyncCount)
    }

    @Test
    fun `save calls requestSync once after a successful write`() = runTest {
        assertTrue(repository.save(sale, 50000).isSuccess)
        assertEquals(1, scheduler.requestSyncCount)
    }

    @Test
    fun `a throwing scheduler does not fail save`() = runTest {
        val throwing = FakeSyncScheduler(failure = IllegalStateException("no workmanager"))
        val repo = TransactionRepositoryImpl(db.transactionDao(), throwing)
        assertTrue(repo.save(sale, 50000).isSuccess)
        assertEquals(1, throwing.requestSyncCount)
        assertEquals(1, db.transactionDao().observeAll().first().size)
    }

    @Test
    fun `saving an existing id still succeeds and schedules`() = runTest {
        assertTrue(repository.save(sale, 50000).isSuccess)
        assertTrue(repository.save(sale, 50000).isSuccess)
        assertEquals(2, scheduler.requestSyncCount)
    }

    private fun failingDao() = run {
        object : TransactionDao() {
            override suspend fun insertHeader(header: TransactionEntity): Long = error("disk full")
            override suspend fun insertLines(lines: List<TransactionLineEntity>) = error("disk full")
            override suspend fun insertSale(header: TransactionEntity, lines: List<TransactionLineEntity>): Boolean =
                error("disk full")
            override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(emptyList())
            override suspend fun linesFor(transactionId: String): List<TransactionLineEntity> = emptyList()
            override suspend fun pendingBatch(
                cashierId: String, afterCreatedAt: Long, afterId: String, limit: Int,
            ): List<TransactionEntity> = emptyList()
            override suspend fun markSynced(id: String, at: Long) = Unit
            override suspend fun recordFailure(id: String, error: String) = Unit
            override fun observeSyncSummary(): Flow<SyncSummaryRow> = flowOf(SyncSummaryRow(0, 0, null))
        }
    }
}
