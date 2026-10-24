package com.example.poswarnus.data.transaction

import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.local.TransactionWithLines
import com.example.poswarnus.data.sync.SyncStatus

data class HistoryLine(
    val name: String,
    val meta: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
)

data class HistoryEntry(
    val id: String,
    val code: String,
    val createdAt: Long,
    val lines: List<HistoryLine>,
    val itemCount: Int,
    val subtotal: Long,
    val taxLabel: String,
    val taxBasisPoints: Int,
    val taxAmount: Long,
    val grandTotal: Long,
    val cashReceived: Long,
    val changeAmount: Long,
    val isPending: Boolean,
)

/** Short human-readable order code: first six hex characters of the id, upper-cased, with a leading hash. */
fun shortCode(id: String): String = "#" + id.replace("-", "").take(6).uppercase()

fun TransactionWithLines.toHistoryEntry(): HistoryEntry {
    val historyLines = lines
        .sortedBy { it.id.substringAfterLast(':').toIntOrNull() ?: Int.MAX_VALUE }
        .map { it.toHistoryLine() }
    return HistoryEntry(
        id = header.id,
        code = shortCode(header.id),
        createdAt = header.createdAt,
        lines = historyLines,
        itemCount = historyLines.sumOf { it.quantity },
        subtotal = header.subtotal,
        taxLabel = header.taxLabel,
        taxBasisPoints = header.taxBasisPoints,
        taxAmount = header.taxAmount,
        grandTotal = header.grandTotal,
        cashReceived = header.cashReceived,
        changeAmount = header.changeAmount,
        isPending = header.syncStatus == SyncStatus.PENDING,
    )
}

private fun TransactionLineEntity.toHistoryLine(): HistoryLine {
    // A line whose stored selections cannot be read is still shown, just without its option text or deltas.
    val selections = try {
        SelectionsJson.decode(selectionsJson)
    } catch (e: Exception) {
        emptyList()
    }
    val variants = selections.filter { it.kind == OptionKind.VARIANT }.map { it.name }
    val modifiers = selections.filter { it.kind == OptionKind.MODIFIER }.map { it.name }
    val unitPrice = basePrice + selections.sumOf { it.priceDelta }
    return HistoryLine(
        name = name,
        meta = (variants + modifiers).joinToString(" · "),
        quantity = quantity,
        unitPrice = unitPrice,
        subtotal = unitPrice * quantity,
    )
}
