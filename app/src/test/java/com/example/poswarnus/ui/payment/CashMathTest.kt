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
    fun `appendDigits adds digits at the end`() {
        assertEquals(5L, CashMath.appendDigits(0, "5"))
        assertEquals(50L, CashMath.appendDigits(5, "0"))
        assertEquals(50000L, CashMath.appendDigits(50, "000"))
    }

    @Test
    fun `appendDigits on zero stays zero for zeros`() {
        assertEquals(0L, CashMath.appendDigits(0, "0"))
        assertEquals(0L, CashMath.appendDigits(0, "000"))
    }

    @Test
    fun `appendDigits ignores input past nine digits`() {
        assertEquals(123456789L, CashMath.appendDigits(123456789, "5"))
        assertEquals(123456789L, CashMath.appendDigits(123456789, "000"))
    }

    @Test
    fun `appendDigits keeps what fits when a triple zero would overflow`() {
        assertEquals(123456000L, CashMath.appendDigits(123456, "000"))
        assertEquals(123456700L, CashMath.appendDigits(1234567, "00"))
    }

    @Test
    fun `backspace drops the last digit`() {
        assertEquals(5000L, CashMath.backspace(50000))
        assertEquals(0L, CashMath.backspace(7))
        assertEquals(0L, CashMath.backspace(0))
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
