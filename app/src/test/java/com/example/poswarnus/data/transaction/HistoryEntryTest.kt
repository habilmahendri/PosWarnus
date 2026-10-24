package com.example.poswarnus.data.transaction

import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.local.TransactionWithLines
import com.example.poswarnus.data.sync.SyncStatus
import com.example.poswarnus.ui.cart.SelectedOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryEntryTest {
    private fun header(id: String = "a3f9c1d2-1111-2222-3333-444455556666", syncStatus: String = SyncStatus.PENDING) =
        TransactionEntity(
            id = id, createdAt = 1_000L, cashierId = "u1", subtotal = 40000, taxLabel = "Pajak", taxBasisPoints = 1000,
            taxAmount = 4000, grandTotal = 44000, cashReceived = 50000, changeAmount = 6000, syncStatus = syncStatus,
        )

    private fun line(id: String, quantity: Int = 1, base: Long = 25000, selections: List<SelectedOption> = emptyList(), json: String? = null) =
        TransactionLineEntity(
            id = id, transactionId = "x", menuItemId = "m", name = "Nasi Goreng", basePrice = base, quantity = quantity,
            selectionsJson = json ?: SelectionsJson.encode(selections),
        )

    private val large = SelectedOption("o1", "g1", "Large", 5000, OptionKind.VARIANT)
    private val keju = SelectedOption("o2", "g2", "Extra Keju", 3000, OptionKind.MODIFIER)
    private val telur = SelectedOption("o3", "g2", "Extra Telur", 2000, OptionKind.MODIFIER)

    @Test
    fun `shortCode is six uppercase hex characters without dashes with a hash`() {
        assertEquals("#A3F9C1", shortCode("a3f9c1d2-1111-2222-3333-444455556666"))
    }

    @Test
    fun `unitPrice adds option price deltas and subtotal multiplies by quantity`() {
        val entry = TransactionWithLines(header(), listOf(line("x:0", quantity = 3, selections = listOf(large, keju)))).toHistoryEntry()
        val l = entry.lines.single()
        assertEquals(33000L, l.unitPrice)
        assertEquals(99000L, l.subtotal)
        assertEquals(3, l.quantity)
        assertEquals("Nasi Goreng", l.name)
    }

    @Test
    fun `meta joins variants first then modifiers with a middle dot and is empty without options`() {
        val entry = TransactionWithLines(
            header(),
            listOf(
                line("x:0", selections = listOf(keju, large, telur)),
                line("x:1"),
            ),
        ).toHistoryEntry()
        assertEquals("Large · Extra Keju · Extra Telur", entry.lines[0].meta)
        assertEquals("", entry.lines[1].meta)
    }

    @Test
    fun `itemCount sums quantities`() {
        val entry = TransactionWithLines(header(), listOf(line("x:0", quantity = 2), line("x:1", quantity = 3))).toHistoryEntry()
        assertEquals(5, entry.itemCount)
    }

    @Test
    fun `isPending is true only for PENDING`() {
        assertTrue(TransactionWithLines(header(syncStatus = SyncStatus.PENDING), emptyList()).toHistoryEntry().isPending)
        assertFalse(TransactionWithLines(header(syncStatus = "SYNCED"), emptyList()).toHistoryEntry().isPending)
    }

    @Test
    fun `lines are ordered by the index suffix of their id`() {
        val entry = TransactionWithLines(
            header(),
            listOf(line("x:10").copy(name = "ten"), line("x:2").copy(name = "two")),
        ).toHistoryEntry()
        assertEquals(listOf("two", "ten"), entry.lines.map { it.name })
    }

    @Test
    fun `corrupt selectionsJson keeps the line with empty meta`() {
        val entry = TransactionWithLines(header(), listOf(line("x:0", json = "not json"))).toHistoryEntry()
        val l = entry.lines.single()
        assertEquals("", l.meta)
        assertEquals(25000L, l.unitPrice)
        assertEquals("Nasi Goreng", l.name)
    }

    @Test
    fun `header fields are copied`() {
        val entry = TransactionWithLines(header(), emptyList()).toHistoryEntry()
        assertEquals("a3f9c1d2-1111-2222-3333-444455556666", entry.id)
        assertEquals("#A3F9C1", entry.code)
        assertEquals(1_000L, entry.createdAt)
        assertEquals(40000L, entry.subtotal)
        assertEquals("Pajak", entry.taxLabel)
        assertEquals(1000, entry.taxBasisPoints)
        assertEquals(4000L, entry.taxAmount)
        assertEquals(44000L, entry.grandTotal)
        assertEquals(50000L, entry.cashReceived)
        assertEquals(6000L, entry.changeAmount)
    }
}
