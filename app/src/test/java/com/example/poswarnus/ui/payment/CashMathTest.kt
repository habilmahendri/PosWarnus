package com.example.poswarnus.ui.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashMathTest {
    @Test
    fun `quick amounts are the nearest bigger round amounts`() {
        assertEquals(listOf(45000L, 50000L, 100000L), CashMath.quickAmounts(43000))
        assertEquals(listOf(100000L), CashMath.quickAmounts(50000))
        assertEquals(listOf(150000L, 200000L), CashMath.quickAmounts(120000))
    }

    @Test
    fun `quick amounts are empty when nothing is strictly bigger`() {
        assertEquals(emptyList<Long>(), CashMath.quickAmounts(100000))
        assertEquals(emptyList<Long>(), CashMath.quickAmounts(0))
    }

    @Test
    fun `parseInput keeps digits only`() {
        assertEquals(0L, CashMath.parseInput(""))
        assertEquals(45L, CashMath.parseInput("0045"))
        assertEquals(50000L, CashMath.parseInput("Rp 50.000"))
    }

    @Test
    fun `parseInput keeps at most nine digits`() {
        assertEquals(123456789L, CashMath.parseInput("1234567890123"))
    }

    @Test
    fun `change is cash minus total`() {
        assertEquals(7000L, CashMath.change(50000, 43000))
    }

    @Test
    fun `canPay needs a positive total covered by cash`() {
        assertTrue(CashMath.canPay(43000, 43000))
        assertFalse(CashMath.canPay(42999, 43000))
        assertFalse(CashMath.canPay(0, 0))
    }
}
