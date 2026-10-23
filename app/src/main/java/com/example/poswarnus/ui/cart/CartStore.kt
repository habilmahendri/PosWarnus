package com.example.poswarnus.ui.cart

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CartState(val lines: List<CartLine> = emptyList()) {
    val subtotal: Long
        get() = lines.sumOf { it.lineTotal }

    val totalQuantity: Int
        get() = lines.sumOf { it.quantity }

    val quantityByMenuItem: Map<String, Int>
        get() = lines.groupBy { it.menuItemId }.mapValues { (_, v) -> v.sumOf { it.quantity } }
}

/** In-memory cart. Lines are plain snapshots; identical configurations merge into one line. */
@Singleton
class CartStore @Inject constructor() {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    fun add(line: CartLine) {
        _state.update { current ->
            val existing = current.lines.firstOrNull { it.configKey == line.configKey }
            if (existing == null) {
                current.copy(lines = current.lines + line)
            } else {
                current.copy(
                    lines = current.lines.map {
                        if (it.lineId == existing.lineId) it.copy(quantity = it.quantity + line.quantity) else it
                    },
                )
            }
        }
    }

    fun replace(lineId: String, selections: List<SelectedOption>, quantity: Int) {
        _state.update { current ->
            val edited = current.lines.firstOrNull { it.lineId == lineId } ?: return@update current
            val updated = edited.copy(selections = selections, quantity = quantity)
            val other = current.lines.firstOrNull { it.lineId != lineId && it.configKey == updated.configKey }
            if (other == null) {
                current.copy(lines = current.lines.map { if (it.lineId == lineId) updated else it })
            } else {
                current.copy(
                    lines = current.lines
                        .filter { it.lineId != lineId }
                        .map { if (it.lineId == other.lineId) it.copy(quantity = it.quantity + quantity) else it },
                )
            }
        }
    }

    fun setQuantity(lineId: String, quantity: Int) {
        _state.update { current ->
            if (current.lines.none { it.lineId == lineId }) return@update current
            if (quantity <= 0) {
                current.copy(lines = current.lines.filter { it.lineId != lineId })
            } else {
                current.copy(lines = current.lines.map { if (it.lineId == lineId) it.copy(quantity = quantity) else it })
            }
        }
    }

    fun remove(lineId: String) {
        _state.update { current -> current.copy(lines = current.lines.filter { it.lineId != lineId }) }
    }

    fun clear() {
        _state.update { CartState() }
    }
}
