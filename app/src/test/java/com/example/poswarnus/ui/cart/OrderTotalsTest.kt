package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.tax.TaxConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTotalsTest {
    private val ppn = TaxConfig("Pajak", 1000)

    @Test
    fun `ten percent tax is added to the subtotal`() {
        val t = OrderTotals.of(43000, ppn)
        assertEquals(4300L, t.taxAmount)
        assertEquals(47300L, t.grandTotal)
        assertEquals("Pajak 10%", t.taxLine)
        assertFalse(t.taxMissing)
    }

    @Test
    fun `tax rounds half up`() {
        assertEquals(3L, OrderTotals.of(25, ppn).taxAmount)
        assertEquals(2L, OrderTotals.of(24, ppn).taxAmount)
    }

    @Test
    fun `fractional percent shows one decimal`() {
        val t = OrderTotals.of(10000, TaxConfig("Pajak", 750))
        assertEquals(750L, t.taxAmount)
        assertEquals("Pajak 7.5%", t.taxLine)
    }

    @Test
    fun `zero subtotal gives zero totals`() {
        val t = OrderTotals.of(0, ppn)
        assertEquals(0L, t.taxAmount)
        assertEquals(0L, t.grandTotal)
    }

    @Test
    fun `missing tax config means zero tax and a warning flag`() {
        val t = OrderTotals.of(43000, null)
        assertEquals(0L, t.taxAmount)
        assertEquals(43000L, t.grandTotal)
        assertEquals("Pajak", t.taxLabel)
        assertTrue(t.taxMissing)
    }

    @Test
    fun `configured zero percent is not missing`() {
        val t = OrderTotals.of(43000, TaxConfig("Service", 0))
        assertEquals(0L, t.taxAmount)
        assertFalse(t.taxMissing)
    }

    @Test
    fun `percentText formats basis points`() {
        assertEquals("10%", percentText(1000))
        assertEquals("7.5%", percentText(750))
        assertEquals("10.5%", percentText(1050))
        assertEquals("0%", percentText(0))
    }
}
