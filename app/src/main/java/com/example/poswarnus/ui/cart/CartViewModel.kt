package com.example.poswarnus.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.OptionGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DialogTarget {
    data class New(val itemId: String) : DialogTarget
    data class Edit(val lineId: String) : DialogTarget
}

/** `draft == null` while the item's option groups are loading. */
data class DialogUiState(val target: DialogTarget, val item: MenuItem, val draft: OrderDraft?)

data class CartLineUi(val line: CartLine, val unavailable: Boolean)

data class CartUiState(
    val lines: List<CartLineUi> = emptyList(),
    val subtotal: Long = 0,
    val totalQuantity: Int = 0,
    val quantityByMenuItem: Map<String, Int> = emptyMap(),
    val dialog: DialogUiState? = null,
    val showClearConfirm: Boolean = false,
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val cartStore: CartStore,
) : ViewModel() {
    // `session` tags each opened dialog so a slow group load can't fill a dialog opened later.
    private data class DialogSession(val session: Long, val ui: DialogUiState)

    private val dialogState = MutableStateFlow<DialogSession?>(null)
    private val showClearConfirm = MutableStateFlow(false)
    private var nextSession = 0L

    private val menuItems: StateFlow<List<MenuItem>> =
        repository.menuItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<CartUiState> = combine(
        cartStore.state,
        menuItems,
        dialogState,
        showClearConfirm,
    ) { cart, items, dialog, clearConfirm ->
        val byId = items.associateBy { it.id }
        // An Edit dialog whose line vanished (merged, removed, cart cleared on logout) must not dangle.
        val visibleDialog = dialog?.ui?.takeIf { ui ->
            val target = ui.target
            target !is DialogTarget.Edit || cart.lines.any { it.lineId == target.lineId }
        }
        CartUiState(
            lines = cart.lines.map { line ->
                CartLineUi(line, unavailable = byId[line.menuItemId]?.isAvailable != true)
            },
            subtotal = cart.subtotal,
            totalQuantity = cart.totalQuantity,
            quantityByMenuItem = cart.quantityByMenuItem,
            dialog = visibleDialog,
            // A confirm left open while the last line was removed elsewhere would clear nothing.
            showClearConfirm = clearConfirm && cart.lines.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartUiState())

    init {
        // Drop the stale request too, so refilling the cart doesn't resurface the confirm.
        viewModelScope.launch {
            cartStore.state.collect { if (it.lines.isEmpty()) showClearConfirm.value = false }
        }
    }

    fun onMenuItemClicked(item: MenuItem) {
        if (!item.isAvailable) return
        openDialog(DialogTarget.New(item.id), item) { groups -> OrderDraft(item, groups) }
    }

    fun onLineClicked(lineId: String) {
        val line = cartStore.state.value.lines.firstOrNull { it.lineId == lineId } ?: return
        // Nothing to render once the item has left the catalog; merely unavailable items stay editable.
        val item = menuItems.value.firstOrNull { it.id == line.menuItemId } ?: return
        openDialog(DialogTarget.Edit(lineId), item) { groups -> OrderDraft.fromLine(item, groups, line) }
    }

    private fun openDialog(target: DialogTarget, item: MenuItem, buildDraft: (List<OptionGroup>) -> OrderDraft) {
        val session = ++nextSession
        dialogState.value = DialogSession(session, DialogUiState(target, item, draft = null))
        viewModelScope.launch {
            val groups = repository.observeOptionGroups(item.id).first()
            val draft = buildDraft(groups)
            dialogState.update { current ->
                if (current?.session == session) current.copy(ui = current.ui.copy(draft = draft)) else current
            }
        }
    }

    fun toggleOption(groupId: String, optionId: String) = updateDraft { it.toggle(groupId, optionId) }

    fun incrementQuantity() = updateDraft { it.withQuantity(it.quantity + 1) }

    fun decrementQuantity() = updateDraft { it.withQuantity(it.quantity - 1) }

    private fun updateDraft(transform: (OrderDraft) -> OrderDraft) {
        dialogState.update { current ->
            val draft = current?.ui?.draft ?: return@update current
            current.copy(ui = current.ui.copy(draft = transform(draft)))
        }
    }

    /**
     * Closing the dialog and reading what to apply happen in one atomic step, so a double tap
     * finds the dialog already gone and adds nothing.
     */
    fun confirmDialog() {
        val previous = dialogState.getAndUpdate { if (it?.ui?.draft?.canSubmit == true) null else it }
        val ui = previous?.ui ?: return
        val draft = ui.draft ?: return
        if (!draft.canSubmit) return
        when (val target = ui.target) {
            is DialogTarget.New -> cartStore.add(
                CartLine(
                    lineId = UUID.randomUUID().toString(),
                    menuItemId = ui.item.id,
                    name = ui.item.name,
                    basePrice = ui.item.price.roundToLong(),
                    selections = draft.toCartSelections(),
                    quantity = draft.quantity,
                ),
            )
            // May merge into another line and drop this lineId; the dialog is already closed either way.
            is DialogTarget.Edit -> cartStore.replace(target.lineId, draft.toCartSelections(), draft.quantity)
        }
    }

    fun dismissDialog() {
        dialogState.value = null
    }

    fun incrementLine(lineId: String) {
        val line = cartStore.state.value.lines.firstOrNull { it.lineId == lineId } ?: return
        cartStore.setQuantity(lineId, line.quantity + 1)
    }

    fun decrementLine(lineId: String) {
        val line = cartStore.state.value.lines.firstOrNull { it.lineId == lineId } ?: return
        cartStore.setQuantity(lineId, line.quantity - 1)
    }

    fun removeLine(lineId: String) = cartStore.remove(lineId)

    fun requestClearAll() {
        if (cartStore.state.value.lines.isNotEmpty()) showClearConfirm.value = true
    }

    fun confirmClearAll() {
        cartStore.clear()
        showClearConfirm.value = false
    }

    fun cancelClearAll() {
        showClearConfirm.value = false
    }
}
