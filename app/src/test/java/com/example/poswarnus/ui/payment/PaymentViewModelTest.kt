package com.example.poswarnus.ui.payment

import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import com.example.poswarnus.data.tax.TaxConfig
import com.example.poswarnus.data.tax.TaxRepository
import com.example.poswarnus.data.transaction.SaleSnapshot
import com.example.poswarnus.data.transaction.TransactionRepository
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.CartStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTaxRepository(initial: TaxConfig? = TaxConfig("Pajak", 1000)) : TaxRepository {
    val config = MutableStateFlow(initial)
    override val taxConfig: Flow<TaxConfig?> = config
    override suspend fun refresh() {}
}

private class FakeTransactionRepository : TransactionRepository {
    val saved = mutableListOf<Pair<SaleSnapshot, Long>>()
    var results = ArrayDeque<Result<Unit>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun save(sale: SaleSnapshot, cashReceived: Long): Result<Unit> {
        saved += sale to cashReceived
        gate?.await()
        return results.removeFirstOrNull() ?: Result.success(Unit)
    }
}

private class FakeAuth(var userId: String? = "user-1") : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult = AuthResult.Success
    override suspend fun logout() {}
    override suspend fun isLoggedIn(): Boolean = true
    override suspend fun ensureSessionForSync(): Boolean = userId != null
    override fun currentUserId(): String? = userId
}

private val nasi = CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 1)
private val ayam = CartLine("l2", "m2", "Ayam Katsu", 18000, emptyList(), 1)

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cartStore = CartStore()
    private val tax = FakeTaxRepository()
    private val transactions = FakeTransactionRepository()
    private val auth = FakeAuth()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel() = PaymentViewModel(cartStore, tax, transactions, auth)

    /** Subtotal 43.000, 10% tax 4.300, grand total 47.300. */
    private fun fillCart() {
        cartStore.add(nasi)
        cartStore.add(ayam)
    }

    private fun PaymentViewModel.opened(): PaymentUiState.Paying {
        open()
        dispatcher.scheduler.advanceUntilIdle()
        return paying()
    }

    private fun PaymentViewModel.paying() = uiState.value as PaymentUiState.Paying

    private fun settle() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `open freezes totals so a later tax refresh does not change them`() = runTest {
        fillCart()
        val vm = viewModel()
        assertEquals(47300L, vm.opened().totals.grandTotal)

        tax.config.value = TaxConfig("Pajak", 2000)
        cartStore.add(nasi.copy(lineId = "l3", name = "Lain"))
        settle()

        assertEquals(47300L, vm.paying().totals.grandTotal)
    }

    @Test
    fun `open with an empty cart stays closed`() = runTest {
        val vm = viewModel()
        vm.open()
        settle()
        assertEquals(PaymentUiState.Closed, vm.uiState.value)
    }

    @Test
    fun `open with no cached tax uses zero percent and flags it`() = runTest {
        fillCart()
        tax.config.value = null
        val state = viewModel().opened()
        assertEquals(43000L, state.totals.grandTotal)
        assertTrue(state.totals.taxMissing)
    }

    @Test
    fun `confirm is disabled while cash is below the total`() = runTest {
        fillCart()
        val vm = viewModel()
        assertFalse(vm.opened().canConfirm)
        vm.onCashInput("47299")
        assertFalse(vm.paying().canConfirm)
        vm.onCashInput("47300")
        assertTrue(vm.paying().canConfirm)
    }

    @Test
    fun `confirm with cash one rupiah short does nothing`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("47299")
        vm.confirm()
        settle()
        assertTrue(transactions.saved.isEmpty())
        assertTrue(vm.uiState.value is PaymentUiState.Paying)
    }

    @Test
    fun `onCashInput drops non-digits and caps at nine digits`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("Rp 50.000")
        assertEquals(50000L, vm.paying().cash)
        vm.onCashInput("1234567890123")
        assertEquals(123456789L, vm.paying().cash)
        vm.onCashInput("")
        assertEquals(0L, vm.paying().cash)
    }

    @Test
    fun `numpad digits and backspace edit the cash`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onDigits("5")
        vm.onDigits("000")
        vm.onDigits("0")
        assertEquals(50000L, vm.paying().cash)
        vm.onBackspace()
        assertEquals(5000L, vm.paying().cash)
        vm.onBackspace(); vm.onBackspace(); vm.onBackspace(); vm.onBackspace(); vm.onBackspace()
        assertEquals(0L, vm.paying().cash)
    }

    @Test
    fun `numpad is ignored while saving`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        transactions.gate = CompletableDeferred()
        vm.confirm()
        settle()
        vm.onDigits("5")
        vm.onBackspace()
        assertEquals(47300L, vm.paying().cash)
    }

    @Test
    fun `paying exposes the frozen cart lines for the summary`() = runTest {
        fillCart()
        val vm = viewModel()
        assertEquals(listOf("l1", "l2"), vm.opened().lines.map { it.lineId })
        cartStore.add(nasi.copy(lineId = "l3", name = "Lain"))
        settle()
        assertEquals(listOf("l1", "l2"), vm.paying().lines.map { it.lineId })
    }

    @Test
    fun `success keeps the lines and typing after a failure still works`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        vm.confirm()
        settle()
        assertEquals(listOf("l1", "l2"), (vm.uiState.value as PaymentUiState.Success).lines.map { it.lineId })
    }

    @Test
    fun `exact amount sets cash to the grand total and enables confirm`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        assertEquals(47300L, vm.paying().cash)
        assertTrue(vm.paying().canConfirm)
        assertEquals(0L, vm.paying().change)
    }

    @Test
    fun `quick amounts come from the grand total and a quick tap sets the cash`() = runTest {
        fillCart()
        val vm = viewModel()
        assertEquals(listOf(50000L, 100000L), vm.opened().quickAmounts)
        vm.onQuickAmount(50000)
        assertEquals(50000L, vm.paying().cash)
        assertEquals(2700L, vm.paying().change)
    }

    @Test
    fun `successful confirm saves once with change and moves to Success while the cart is untouched`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("50000")
        vm.confirm()
        settle()

        val success = vm.uiState.value as PaymentUiState.Success
        assertEquals(47300L, success.totals.grandTotal)
        assertEquals(50000L, success.cash)
        assertEquals(2700L, success.change)
        assertEquals(1, transactions.saved.size)
        assertEquals(50000L, transactions.saved.single().second)
        assertEquals(2, cartStore.state.value.lines.size)
    }

    @Test
    fun `double confirm saves once`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("50000")
        transactions.gate = CompletableDeferred()
        vm.confirm()
        vm.confirm()
        settle()
        assertEquals(1, transactions.saved.size)
        assertTrue(vm.paying().isSaving)

        transactions.gate!!.complete(Unit)
        settle()
        assertTrue(vm.uiState.value is PaymentUiState.Success)
        assertEquals(1, transactions.saved.size)
    }

    @Test
    fun `failed save keeps the cash and sets error and retry reuses the same transaction id`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("50000")
        transactions.results.addLast(Result.failure(RuntimeException("disk full")))
        vm.confirm()
        settle()

        val failed = vm.paying()
        assertTrue(failed.error)
        assertFalse(failed.isSaving)
        assertEquals(50000L, failed.cash)

        vm.confirm()
        settle()
        assertTrue(vm.uiState.value is PaymentUiState.Success)
        assertEquals(2, transactions.saved.size)
        assertEquals(transactions.saved[0].first.id, transactions.saved[1].first.id)
    }

    @Test
    fun `typing clears the error`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("50000")
        transactions.results.addLast(Result.failure(RuntimeException("x")))
        vm.confirm()
        settle()
        assertTrue(vm.paying().error)
        vm.onCashInput("60000")
        assertFalse(vm.paying().error)
    }

    @Test
    fun `dismiss during Paying closes without clearing the cart`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.dismiss()
        assertEquals(PaymentUiState.Closed, vm.uiState.value)
        assertEquals(2, cartStore.state.value.lines.size)
    }

    @Test
    fun `dismiss while saving does nothing`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onCashInput("50000")
        transactions.gate = CompletableDeferred()
        vm.confirm()
        settle()
        vm.dismiss()
        assertTrue(vm.uiState.value is PaymentUiState.Paying)
    }

    @Test
    fun `dismiss in Success does nothing and finish clears the cart and closes`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        vm.confirm()
        settle()

        vm.dismiss()
        assertTrue(vm.uiState.value is PaymentUiState.Success)

        vm.finish()
        assertEquals(PaymentUiState.Closed, vm.uiState.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
    }

    @Test
    fun `finish outside Success does nothing`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.finish()
        assertTrue(vm.uiState.value is PaymentUiState.Paying)
        assertEquals(2, cartStore.state.value.lines.size)
    }

    @Test
    fun `uses the cashier id from AuthRepository`() = runTest {
        fillCart()
        auth.userId = "kasir-9"
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        vm.confirm()
        settle()
        assertEquals("kasir-9", transactions.saved.single().first.cashierId)
    }

    @Test
    fun `a missing cashier id fails into the error state without saving`() = runTest {
        fillCart()
        auth.userId = null
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        vm.confirm()
        settle()
        assertTrue(vm.paying().error)
        assertTrue(transactions.saved.isEmpty())
    }

    @Test
    fun `the saved snapshot carries the frozen lines and totals`() = runTest {
        fillCart()
        val vm = viewModel()
        vm.opened()
        vm.onExactAmount()
        vm.confirm()
        settle()
        val sale = transactions.saved.single().first
        assertEquals(listOf("l1", "l2"), sale.lines.map { it.lineId })
        assertEquals(47300L, sale.totals.grandTotal)
    }
}
