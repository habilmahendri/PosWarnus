package com.example.poswarnus.ui.settings

import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import com.example.poswarnus.data.remote.TrainingAuditAction
import com.example.poswarnus.data.remote.TrainingPinRemoteDataSource
import com.example.poswarnus.data.training.TrainingModeStore
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.CartStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTrainingPinRemoteDataSource(
    private val result: suspend () -> Boolean = { true },
) : TrainingPinRemoteDataSource {
    val recorded = mutableListOf<Pair<String, TrainingAuditAction>>()
    var recordTransitionThrows: Throwable? = null
    override suspend fun verifyPin(pin: String): Boolean = result()
    override suspend fun recordTransition(cashierId: String, action: TrainingAuditAction) {
        recordTransitionThrows?.let { throw it }
        recorded += cashierId to action
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val cartStore = CartStore()
    private val store = TrainingModeStore(cartStore)
    private val fakeAuthRepository = object : AuthRepository {
        override suspend fun login(email: String, password: String): AuthResult = error("not used")
        override suspend fun logout() = error("not used")
        override suspend fun isLoggedIn(): Boolean = error("not used")
        override suspend fun ensureSessionForSync(): Boolean = error("not used")
        override fun currentUserId(): String = "cashier-1"
    }
    private var remote = FakeTrainingPinRemoteDataSource()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        cartStore.add(CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 2))
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle on opens a fresh pin dialog without changing the store`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onTrainingToggle(true)

        assertFalse(store.enabled.value)
        assertEquals(1, cartStore.state.value.lines.size)
        assertEquals(PinDialogState(), viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `toggle off disables immediately, clears the cart and records a disable audit row`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        store.enable()
        cartStore.add(CartLine("l2", "m2", "Es Teh", 5000, emptyList(), 1))

        viewModel.onTrainingToggle(false)

        assertFalse(store.enabled.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
        assertEquals(null, viewModel.uiState.value.pinDialog)
        assertEquals(listOf("cashier-1" to TrainingAuditAction.DISABLE), remote.recorded)
    }

    @Test
    fun `toggle off while the pin dialog is open closes it and disables at once`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)

        viewModel.onTrainingToggle(false)

        assertFalse(store.enabled.value)
        assertEquals(null, viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `correct pin enables training, clears the cart and records an enable audit row`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeTrainingPinRemoteDataSource(result = { true })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("123456")

        viewModel.onPinSubmit()

        assertTrue(store.enabled.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
        assertEquals(null, viewModel.uiState.value.pinDialog)
        assertEquals(listOf("cashier-1" to TrainingAuditAction.ENABLE), remote.recorded)
    }

    @Test
    fun `wrong pin keeps training off and shows an error with the input cleared`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeTrainingPinRemoteDataSource(result = { false })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("000000")

        viewModel.onPinSubmit()

        assertFalse(store.enabled.value)
        assertEquals(PinDialogState(error = "PIN salah"), viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `offline during verification shows a connectivity message and leaves training off`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeTrainingPinRemoteDataSource(result = { throw java.io.IOException("offline") })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("123456")

        viewModel.onPinSubmit()

        assertFalse(store.enabled.value)
        assertEquals("Perlu koneksi untuk verifikasi PIN", viewModel.uiState.value.pinDialog?.error)
    }

    @Test
    fun `a generic server error fails closed as wrong pin instead of throwing`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeTrainingPinRemoteDataSource(result = { error("server exploded") })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("123456")

        viewModel.onPinSubmit()

        assertFalse(store.enabled.value)
        assertEquals("PIN salah", viewModel.uiState.value.pinDialog?.error)
    }

    @Test
    fun `a failed audit write does not block enabling`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeTrainingPinRemoteDataSource(result = { true })
        remote.recordTransitionThrows = RuntimeException("insert failed")
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("123456")

        viewModel.onPinSubmit()

        assertTrue(store.enabled.value)
        assertEquals(null, viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `submitting twice before the first response only calls verifyPin once`() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val deferred = CompletableDeferred<Boolean>()
        val remote = FakeTrainingPinRemoteDataSource(result = { calls++; deferred.await() })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("123456")

        viewModel.onPinSubmit()
        viewModel.onPinSubmit()

        assertEquals(1, calls)
        deferred.complete(true)
    }

    @Test
    fun `toggling off while verification is in flight cancels it so a late true never re-enables`() = runTest(UnconfinedTestDispatcher()) {
        val deferred = CompletableDeferred<Boolean>()
        val remote = FakeTrainingPinRemoteDataSource(result = { deferred.await() })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("123456")
        viewModel.onPinSubmit()

        viewModel.onTrainingToggle(false)
        deferred.complete(true)

        assertFalse(store.enabled.value)
        assertEquals(null, viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `a failed audit write does not block disabling`() = runTest(UnconfinedTestDispatcher()) {
        remote.recordTransitionThrows = RuntimeException("insert failed")
        backgroundScope.launch { viewModel.uiState.collect {} }
        store.enable()

        viewModel.onTrainingToggle(false)

        assertFalse(store.enabled.value)
        assertEquals(null, viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `reopening the dialog after a wrong attempt starts fresh`() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeTrainingPinRemoteDataSource(result = { false })
        viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)
        viewModel.onPinInputChange("000000")
        viewModel.onPinSubmit()
        viewModel.onTrainingToggle(false)

        viewModel.onTrainingToggle(true)

        assertEquals(PinDialogState(), viewModel.uiState.value.pinDialog)
    }

    @Test
    fun `uiState follows the store`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        store.enable()

        assertTrue(viewModel.uiState.value.trainingEnabled)
    }
}
