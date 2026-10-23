package com.example.poswarnus.ui.dashboard

import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import com.example.poswarnus.data.training.TrainingModeStore
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.CartStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Records the cart size at each step so the test can pin the order of logout side effects. */
private class RecordingAuthRepository(private val cartStore: CartStore, private val events: MutableList<String>) : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult = AuthResult.Success
    override suspend fun logout() { events += "logout(lines=${cartStore.state.value.lines.size})" }
    override suspend fun isLoggedIn(): Boolean = true
    override suspend fun ensureSessionForSync(): Boolean = true
    override fun currentUserId(): String? = "user-1"
}

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `logout clears the cart after signing out and before navigating`() = runTest {
        val cartStore = CartStore().apply { add(CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 2)) }
        val events = mutableListOf<String>()
        val viewModel = LogoutViewModel(RecordingAuthRepository(cartStore, events), cartStore, TrainingModeStore(cartStore))

        viewModel.logout { events += "onLoggedOut(lines=${cartStore.state.value.lines.size})" }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("logout(lines=1)", "onLoggedOut(lines=0)"), events)
    }

    @Test
    fun `logout turns training mode off`() = runTest {
        val cartStore = CartStore()
        val trainingModeStore = TrainingModeStore(cartStore).apply { enable() }
        val viewModel = LogoutViewModel(RecordingAuthRepository(cartStore, mutableListOf()), cartStore, trainingModeStore)

        viewModel.logout {}
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, trainingModeStore.enabled.value)
    }
}
