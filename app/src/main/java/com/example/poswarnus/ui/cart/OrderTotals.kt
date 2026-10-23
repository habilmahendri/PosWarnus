package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.tax.TaxConfig

/** 1000 -> "10%", 750 -> "7.5%", 1050 -> "10.5%". */
fun percentText(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = (basisPoints % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole%" else "$whole.$fraction%"
}

/** Whole-Rupiah money maths for one order; no rounding beyond the tax's half-up. */
data class OrderTotals(
    val subtotal: Long,
    val taxLabel: String,
    val taxBasisPoints: Int,
    val taxAmount: Long,
    val grandTotal: Long,
    /** True when no tax config was ever cached, so 0% is a fallback rather than a setting. */
    val taxMissing: Boolean,
) {
    val taxLine: String
        get() = "$taxLabel ${percentText(taxBasisPoints)}"

    companion object {
        fun of(subtotal: Long, tax: TaxConfig?): OrderTotals {
            val label = tax?.label ?: "Pajak"
            val bps = tax?.basisPoints ?: 0
            val taxAmount = (subtotal * bps + 5000) / 10000
            return OrderTotals(subtotal, label, bps, taxAmount, subtotal + taxAmount, taxMissing = tax == null)
        }
    }
}
