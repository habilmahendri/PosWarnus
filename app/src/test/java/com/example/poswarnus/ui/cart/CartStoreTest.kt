package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.catalog.OptionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartStoreTest {

    private val large = SelectedOption("o-large", "g-size", "Large", 5_000, OptionKind.VARIANT)
    private val keju = SelectedOption("o-keju", "g-top", "Extra Keju", 3_000, OptionKind.MODIFIER)
    private val telur = SelectedOption("o-telur", "g-top", "Extra Telur", 2_000, OptionKind.MODIFIER)

    private fun line(
        id: String,
        menuItemId: String = "m1",
        basePrice: Long = 20_000,
        selections: List<SelectedOption> = emptyList(),
        quantity: Int = 1,
    ) = CartLine(id, menuItemId, "Item $menuItemId", basePrice, selections, quantity)

    private fun store() = CartStore()

    @Test
    fun `add appends a new line`() {
        val s = store()
        s.add(line("a"))
        s.add(line("b", menuItemId = "m2"))
        assertEquals(listOf("a", "b"), s.state.value.lines.map { it.lineId })
    }

    @Test
    fun `add merges quantity for an equal configKey`() {
        val s = store()
        s.add(line("a", selections = listOf(large, keju), quantity = 2))
        s.add(line("b", selections = listOf(keju, large), quantity = 3))
        assertEquals(1, s.state.value.lines.size)
        assertEquals("a", s.state.value.lines[0].lineId)
        assertEquals(5, s.state.value.lines[0].quantity)
    }

    @Test
    fun `replace updates selections and quantity in place keeping lineId`() {
        val s = store()
        s.add(line("a"))
        s.add(line("b", menuItemId = "m2"))
        s.replace("a", listOf(large), 4)
        val lines = s.state.value.lines
        assertEquals(listOf("a", "b"), lines.map { it.lineId })
        assertEquals(listOf(large), lines[0].selections)
        assertEquals(4, lines[0].quantity)
    }

    @Test
    fun `replace into a configKey held by another line merges and removes the edited line`() {
        val s = store()
        s.add(line("a", quantity = 2))
        s.add(line("b", selections = listOf(large), quantity = 3))
        s.replace("a", listOf(large), 1)
        val lines = s.state.value.lines
        assertEquals(1, lines.size)
        assertEquals("b", lines[0].lineId)
        assertEquals(4, lines[0].quantity)
    }

    @Test
    fun `setQuantity zero removes the line`() {
        val s = store()
        s.add(line("a"))
        s.setQuantity("a", 0)
        assertTrue(s.state.value.lines.isEmpty())
    }

    @Test
    fun `setQuantity on unknown id is a no-op`() {
        val s = store()
        s.add(line("a", quantity = 2))
        s.setQuantity("zzz", 9)
        assertEquals(2, s.state.value.lines[0].quantity)
    }

    @Test
    fun `setQuantity updates an existing line`() {
        val s = store()
        s.add(line("a", quantity = 2))
        s.setQuantity("a", 7)
        assertEquals(7, s.state.value.lines[0].quantity)
    }

    @Test
    fun `remove deletes only that line`() {
        val s = store()
        s.add(line("a"))
        s.add(line("b", menuItemId = "m2"))
        s.remove("a")
        assertEquals(listOf("b"), s.state.value.lines.map { it.lineId })
    }

    @Test
    fun `clear empties`() {
        val s = store()
        s.add(line("a"))
        s.add(line("b", menuItemId = "m2"))
        s.clear()
        assertTrue(s.state.value.lines.isEmpty())
    }

    @Test
    fun `subtotal sums lineTotals`() {
        val s = store()
        s.add(line("a", basePrice = 20_000, selections = listOf(large), quantity = 2)) // 50_000
        s.add(line("b", menuItemId = "m2", basePrice = 10_000, quantity = 3)) // 30_000
        assertEquals(80_000L, s.state.value.subtotal)
        assertEquals(5, s.state.value.totalQuantity)
    }

    @Test
    fun `quantityByMenuItem sums per menu item across configs`() {
        val s = store()
        s.add(line("a", quantity = 2))
        s.add(line("b", selections = listOf(large), quantity = 3))
        s.add(line("c", menuItemId = "m2", quantity = 1))
        assertEquals(mapOf("m1" to 5, "m2" to 1), s.state.value.quantityByMenuItem)
    }

    @Test
    fun `lines are unaffected by later changes to the source MenuItem`() {
        var name = "Nasi Goreng"
        var price = 20_000L
        val s = store()
        s.add(CartLine("a", "m1", name, price, listOf(large), 1))
        name = "Renamed"
        price = 99_000L
        val l = s.state.value.lines[0]
        assertEquals("Nasi Goreng", l.name)
        assertEquals(20_000L, l.basePrice)
        assertEquals(25_000L, l.unitPrice)
        assertEquals("Renamed", name)
        assertEquals(99_000L, price)
    }
}
