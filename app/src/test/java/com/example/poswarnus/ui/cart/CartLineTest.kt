package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.catalog.OptionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CartLineTest {
    private val large = SelectedOption("large", "g-size", "Large", 10000, OptionKind.VARIANT)
    private val keju = SelectedOption("keju", "g-extra", "Extra Keju", 5000, OptionKind.MODIFIER)
    private val telur = SelectedOption("telur", "g-extra", "Extra Telur", 3000, OptionKind.MODIFIER)

    private fun line(sel: List<SelectedOption>, qty: Int = 1) = CartLine("l", "m1", "Nasi Goreng", 25000, sel, qty)

    @Test fun `configKey is independent of selection order`() {
        assertEquals(line(listOf(large, keju, telur)).configKey, line(listOf(telur, large, keju)).configKey)
    }

    @Test fun `configKey differs when an option differs`() {
        assertNotEquals(line(listOf(large, keju)).configKey, line(listOf(large, telur)).configKey)
    }

    @Test fun `configKey differs when the snapshot base price differs`() {
        val old = CartLine("a", "m1", "Nasi Goreng", 25000, listOf(large), 1)
        val repriced = CartLine("b", "m1", "Nasi Goreng", 30000, listOf(large), 1)
        assertNotEquals(old.configKey, repriced.configKey)
    }

    @Test fun `configKey differs when an option delta snapshot differs`() {
        assertNotEquals(line(listOf(large)).configKey, line(listOf(large.copy(priceDelta = 12000))).configKey)
    }

    @Test fun `configKey equal for same config and same price`() {
        val a = CartLine("a", "m1", "Nasi Goreng", 25000, listOf(large, keju), 1)
        val b = CartLine("b", "m1", "Nasi Goreng", 25000, listOf(keju, large), 4)
        assertEquals(a.configKey, b.configKey)
    }

    @Test fun `summary lists variant first then modifiers`() {
        assertEquals("Large · Extra Keju, Extra Telur", line(listOf(keju, telur, large)).summary)
    }

    @Test fun `summary empty without selections`() {
        assertEquals("", line(emptyList()).summary)
    }

    @Test fun `lineTotal is unitPrice times quantity`() {
        val l = line(listOf(large, keju), 3)
        assertEquals(40000L, l.unitPrice)
        assertEquals(120000L, l.lineTotal)
    }
}
