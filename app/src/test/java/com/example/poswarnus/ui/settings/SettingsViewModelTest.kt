package com.example.poswarnus.ui.settings

import com.example.poswarnus.data.training.TrainingModeStore
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.CartStore
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val cartStore = CartStore()
    private val store = TrainingModeStore(cartStore)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        cartStore.add(CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 2))
        viewModel = SettingsViewModel(store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle on asks for confirmation without changing anything`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onTrainingToggle(true)

        assertFalse(store.enabled.value)
        assertEquals(1, cartStore.state.value.lines.size)
        assertTrue(viewModel.uiState.value.confirmingEnable)
        assertFalse(viewModel.uiState.value.trainingEnabled)
    }

    @Test
    fun `cancel leaves flag and cart untouched`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)

        viewModel.cancelEnable()

        assertFalse(store.enabled.value)
        assertEquals(1, cartStore.state.value.lines.size)
        assertFalse(viewModel.uiState.value.confirmingEnable)
    }

    @Test
    fun `confirm enables training and clears the cart`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onTrainingToggle(true)

        viewModel.confirmEnable()

        assertTrue(store.enabled.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
        assertTrue(viewModel.uiState.value.trainingEnabled)
        assertFalse(viewModel.uiState.value.confirmingEnable)
    }

    @Test
    fun `toggle off disables immediately and clears the cart`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        store.enable()
        cartStore.add(CartLine("l2", "m2", "Es Teh", 5000, emptyList(), 1))

        viewModel.onTrainingToggle(false)

        assertFalse(store.enabled.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
        assertFalse(viewModel.uiState.value.trainingEnabled)
        assertFalse(viewModel.uiState.value.confirmingEnable)
    }

    @Test
    fun `uiState follows the store`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        store.enable()

        assertTrue(viewModel.uiState.value.trainingEnabled)
    }
}
