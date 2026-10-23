package com.example.poswarnus.ui.login

import app.cash.turbine.test
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeAuthRepository(
    var loginResult: AuthResult = AuthResult.Success,
    var isLoggedInResult: Boolean = false,
) : AuthRepository {
    var loginCallCount = 0
        private set

    override suspend fun login(email: String, password: String): AuthResult {
        loginCallCount++
        return loginResult
    }

    override suspend fun logout() {}
    override suspend fun isLoggedIn(): Boolean = isLoggedInResult
    override suspend fun ensureSessionForSync(): Boolean = true
    override fun currentUserId(): String? = "user-1"
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cartStore = CartStore()
    private val trainingModeStore = TrainingModeStore(cartStore)

    private fun CartStore.addSample() =
        add(CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 2))

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `blank email or password shows validation error without calling repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository, cartStore, trainingModeStore)

        viewModel.onPasswordChanged("something")
        viewModel.onLoginClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.loginCallCount)
        assertEquals("Email dan kata sandi wajib diisi.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `successful login emits NavigateToDashboard and clears loading`() = runTest {
        val repository = FakeAuthRepository(loginResult = AuthResult.Success)
        val viewModel = LoginViewModel(repository, cartStore, trainingModeStore)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("correct-password")

        viewModel.events.test {
            viewModel.onLoginClicked()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed login shows error message and clears loading`() = runTest {
        val repository = FakeAuthRepository(loginResult = AuthResult.Failure("Invalid login credentials"))
        val viewModel = LoginViewModel(repository, cartStore, trainingModeStore)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("wrong-password")

        viewModel.events.test {
            viewModel.onLoginClicked()
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals("Invalid login credentials", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful login clears a cart left over from the previous session`() = runTest {
        cartStore.addSample()
        val viewModel = LoginViewModel(FakeAuthRepository(loginResult = AuthResult.Success), cartStore, trainingModeStore)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("correct-password")

        viewModel.events.test {
            viewModel.onLoginClicked()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
        assertTrue(cartStore.state.value.lines.isEmpty())
    }

    @Test
    fun `failed login does not clear the cart`() = runTest {
        cartStore.addSample()
        val viewModel = LoginViewModel(FakeAuthRepository(loginResult = AuthResult.Failure("Invalid login credentials")), cartStore, trainingModeStore)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("wrong-password")

        viewModel.onLoginClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, cartStore.state.value.lines.size)
    }

    @Test
    fun `double-tapping login while loading only triggers one repository call`() = runTest {
        val repository = FakeAuthRepository(loginResult = AuthResult.Success)
        val viewModel = LoginViewModel(repository, cartStore, trainingModeStore)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("correct-password")

        viewModel.onLoginClicked()
        viewModel.onLoginClicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.loginCallCount)
    }

    @Test
    fun `init checks isLoggedIn and emits NavigateToDashboard immediately when already authenticated`() = runTest {
        val repository = FakeAuthRepository(isLoggedInResult = true)
        val viewModel = LoginViewModel(repository, cartStore, trainingModeStore)

        viewModel.events.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
    }

    @Test
    fun `init does not emit NavigateToDashboard when no session is persisted`() = runTest {
        val repository = FakeAuthRepository(isLoggedInResult = false)
        val viewModel = LoginViewModel(repository, cartStore, trainingModeStore)

        viewModel.events.test {
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `successful login turns training mode off`() = runTest {
        trainingModeStore.enable()
        val viewModel = LoginViewModel(FakeAuthRepository(loginResult = AuthResult.Success), cartStore, trainingModeStore)
        viewModel.onEmailChanged("cashier@warnus.test")
        viewModel.onPasswordChanged("correct-password")

        viewModel.events.test {
            viewModel.onLoginClicked()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
        assertEquals(false, trainingModeStore.enabled.value)
    }

    @Test
    fun `restored session turns training mode off`() = runTest {
        trainingModeStore.enable()
        val viewModel = LoginViewModel(FakeAuthRepository(isLoggedInResult = true), cartStore, trainingModeStore)

        viewModel.events.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoginEvent.NavigateToDashboard, awaitItem())
        }
        assertEquals(false, trainingModeStore.enabled.value)
    }
}
