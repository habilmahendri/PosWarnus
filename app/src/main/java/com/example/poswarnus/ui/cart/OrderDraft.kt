package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.Option
import com.example.poswarnus.data.catalog.OptionGroup
import com.example.poswarnus.data.catalog.SelectionMode
import kotlin.math.roundToLong

/** In-progress configuration of one menu item inside the detail dialog. */
data class OrderDraft(
    val item: MenuItem,
    val groups: List<OptionGroup>,
    val selected: Map<String, Set<String>> = emptyMap(),
    val quantity: Int = 1,
) {
    fun toggle(groupId: String, optionId: String): OrderDraft {
        val group = groups.firstOrNull { it.id == groupId } ?: return this
        val option = group.options.firstOrNull { it.id == optionId } ?: return this
        if (!option.isAvailable) return this
        val current = selected[groupId].orEmpty()
        val next = when (group.selection) {
            SelectionMode.SINGLE -> setOf(optionId)
            SelectionMode.MULTI -> if (optionId in current) current - optionId else current + optionId
        }
        return copy(selected = selected + (groupId to next))
    }

    fun withQuantity(q: Int): OrderDraft = copy(quantity = q.coerceAtLeast(1))

    /** A required group with no available option cannot be satisfied, so it is not enforced. */
    val missingRequiredGroups: List<OptionGroup>
        get() = groups.filter { g ->
            g.isRequired && g.options.any { it.isAvailable } && selected[g.id].orEmpty().isEmpty()
        }

    val canSubmit: Boolean
        get() = missingRequiredGroups.isEmpty()

    private val basePrice: Long
        get() = item.price.roundToLong()

    /** Absolute price of a variant card: the item's base price plus the option's delta. */
    fun variantPrice(option: Option): Long = basePrice + option.priceDelta

    val unitPrice: Long
        get() = basePrice + toCartSelections().sumOf { it.priceDelta }

    val lineTotal: Long
        get() = unitPrice * quantity

    fun toCartSelections(): List<SelectedOption> =
        groups.sortedBy { it.sortOrder }.flatMap { g ->
            val ids = selected[g.id].orEmpty()
            g.options.sortedBy { it.sortOrder }
                .filter { it.id in ids }
                .map { SelectedOption(it.id, g.id, it.name, it.priceDelta, g.kind) }
        }

    companion object {
        fun fromLine(item: MenuItem, groups: List<OptionGroup>, line: CartLine): OrderDraft {
            val wanted = line.selections.map { it.optionId }.toSet()
            val selected = groups.mapNotNull { g ->
                val ids = g.options.filter { it.isAvailable && it.id in wanted }.map { it.id }.toSet()
                if (ids.isEmpty()) null else g.id to ids
            }.toMap()
            return OrderDraft(item, groups, selected, line.quantity.coerceAtLeast(1))
        }
    }
}
