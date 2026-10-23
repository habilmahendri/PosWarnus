package com.example.poswarnus.ui.payment

object CashMath {
    const val MAX_DIGITS = 9

    private val ROUND_STEPS = listOf(5_000L, 10_000L, 50_000L, 100_000L)

    /** Up to three distinct round amounts strictly above [total], ascending (e.g. 43.000 -> 45.000, 50.000, 100.000). */
    fun quickAmounts(total: Long): List<Long> =
        ROUND_STEPS.map { step -> (total + step - 1) / step * step }
            .filter { it > total }
            .distinct()
            .sorted()
            .take(3)

    /** Keeps digits only, at most [MAX_DIGITS] of them; blank means 0. */
    fun parseInput(raw: String): Long =
        raw.filter { it.isDigit() }.take(MAX_DIGITS).toLongOrNull() ?: 0L

    fun change(cash: Long, total: Long): Long = cash - total

    fun canPay(cash: Long, total: Long): Boolean = total > 0 && cash >= total
}
