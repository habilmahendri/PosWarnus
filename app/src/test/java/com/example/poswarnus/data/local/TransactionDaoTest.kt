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

    private fun header(id: String = "t1", isTraining: Boolean = false) = TransactionEntity(
        id = id, createdAt = 1_000L, cashierId = "u1", subtotal = 43000, taxLabel = "Pajak", taxBasisPoints = 1000,
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
}
