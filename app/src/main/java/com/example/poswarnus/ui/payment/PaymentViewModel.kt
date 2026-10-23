package com.example.poswarnus.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.tax.TaxRepository
import com.example.poswarnus.data.transaction.SaleSnapshot
import com.example.poswarnus.data.transaction.TransactionRepository
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.CartStore
import com.example.poswarnus.ui.cart.OrderTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PaymentUiState {
    data object Closed : PaymentUiState

    /** [change] is negative while the cash is short. */
    data class Paying(
        val totals: OrderTotals,
        /** The cart as frozen when the screen opened, for the order summary. */
        val lines: List<CartLine>,
        val cash: Long,
        val quickAmounts: List<Long>,
        val change: Long,
        val canConfirm: Boolean,
        val isSaving: Boolean,
        val error: Boolean,
    ) : PaymentUiState

    data class Success(
        val totals: OrderTotals,
        val lines: List<CartLine>,
        val cash: Long,
        val change: Long,
    ) : PaymentUiState
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val cartStore: CartStore,
    private val taxRepository: TaxRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    /** What the dialog froze when it opened; a retry after a failed save reuses [id]. */
    private data class Frozen(val id: String, val createdAt: Long, val lines: List<CartLine>, val totals: OrderTotals)

    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Closed)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var frozen: Frozen? = null
    private var opening = false

    fun open() {
        if (opening || _uiState.value != PaymentUiState.Closed) return
        val lines = cartStore.state.value.lines
        if (lines.isEmpty()) return
        opening = true
        viewModelScope.launch {
            try {
                val totals = OrderTotals.of(lines.sumOf { it.lineTotal }, taxRepository.taxConfig.first())
                frozen = Frozen(UUID.randomUUID().toString(), System.currentTimeMillis(), lines, totals)
                _uiState.value = paying(totals, lines, cash = 0, isSaving = false, error = false)
            } finally {
                opening = false
            }
        }
    }

    fun onCashInput(raw: String) = editCash { CashMath.parseInput(raw) }

    fun onQuickAmount(amount: Long) = editCash { amount }

    fun onExactAmount() {
        val totals = frozen?.totals ?: return
        editCash { totals.grandTotal }
    }

    /** Numpad key: "0".."9" or "000". */
    fun onDigits(digits: String) = editCash { CashMath.appendDigits(it, digits) }

    fun onBackspace() = editCash { CashMath.backspace(it) }

    private fun editCash(transform: (Long) -> Long) {
        _uiState.update { current ->
            if (current is PaymentUiState.Paying && !current.isSaving) {
                paying(current.totals, current.lines, transform(current.cash), isSaving = false, error = false)
            } else {
                current
            }
        }
    }

    fun confirm() {
        val previous = _uiState.getAndUpdate { current ->
            if (current is PaymentUiState.Paying && current.canConfirm && !current.isSaving) {
                current.copy(isSaving = true, error = false)
            } else {
                current
            }
        }
        if (previous !is PaymentUiState.Paying || !previous.canConfirm || previous.isSaving) return
        val sale = frozen ?: return
        viewModelScope.launch {
            val cashierId = authRepository.currentUserId()
            val result = if (cashierId == null) {
                Result.failure(IllegalStateException("No signed-in cashier"))
            } else {
                transactionRepository.save(
                    SaleSnapshot(sale.id, sale.createdAt, cashierId, sale.lines, sale.totals),
                    previous.cash,
                )
            }
            _uiState.update { current ->
                if (current !is PaymentUiState.Paying) {
                    current
                } else if (result.isSuccess) {
                    PaymentUiState.Success(sale.totals, sale.lines, previous.cash, previous.cash - sale.totals.grandTotal)
                } else {
                    current.copy(isSaving = false, error = true)
                }
            }
        }
    }

    /** Closes the payment step, keeping the cart. Not possible while saving or once the sale succeeded. */
    fun dismiss() {
        val previous = _uiState.getAndUpdate { current ->
            if (current is PaymentUiState.Paying && !current.isSaving) PaymentUiState.Closed else current
        }
        if (previous is PaymentUiState.Paying && !previous.isSaving) frozen = null
    }

    /** "Selesai": the sale is stored, so the cart is emptied for the next order. */
    fun finish() {
        val previous = _uiState.getAndUpdate { current ->
            if (current is PaymentUiState.Success) PaymentUiState.Closed else current
        }
        if (previous is PaymentUiState.Success) {
            frozen = null
            cartStore.clear()
        }
    }

    private fun paying(totals: OrderTotals, lines: List<CartLine>, cash: Long, isSaving: Boolean, error: Boolean) =
        PaymentUiState.Paying(
            totals = totals,
            lines = lines,
            cash = cash,
            quickAmounts = CashMath.quickAmounts(totals.grandTotal),
            change = CashMath.change(cash, totals.grandTotal),
            canConfirm = CashMath.canPay(cash, totals.grandTotal),
            isSaving = isSaving,
            error = error,
        )
}
