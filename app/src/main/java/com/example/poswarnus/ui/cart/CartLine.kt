package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.catalog.OptionKind

/** Snapshot of a chosen option, so a cart line stays priced even if the catalog changes. */
data class SelectedOption(
    val optionId: String,
    val groupId: String,
    val name: String,
    val priceDelta: Long,
    val kind: OptionKind,
)

data class CartLine(
    val lineId: String,
    val menuItemId: String,
    val name: String,
    val basePrice: Long,
    val selections: List<SelectedOption>,
    val quantity: Int,
) {
    /** Identical configurations merge into one line; option order must not matter. */
    val configKey: String
        get() = menuItemId + "|" + selections.map { it.optionId }.sorted().joinToString(",")

    val unitPrice: Long
        get() = basePrice + selections.sumOf { it.priceDelta }

    val lineTotal: Long
        get() = unitPrice * quantity

    /** "Large · Extra Keju, Extra Telur": variant first, then modifiers. */
    val summary: String
        get() {
            val variants = selections.filter { it.kind == OptionKind.VARIANT }.joinToString(", ") { it.name }
            val modifiers = selections.filter { it.kind == OptionKind.MODIFIER }.joinToString(", ") { it.name }
            return listOf(variants, modifiers).filter { it.isNotEmpty() }.joinToString(" · ")
        }
}
