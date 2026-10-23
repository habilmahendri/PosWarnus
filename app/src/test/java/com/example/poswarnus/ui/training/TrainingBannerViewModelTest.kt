package com.example.poswarnus.ui.training

import com.example.poswarnus.data.training.TrainingModeStore
import com.example.poswarnus.ui.cart.CartStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingBannerViewModelTest {
    @Test
    fun `banner viewmodel follows the store`() {
        val store = TrainingModeStore(CartStore())
        val viewModel = TrainingBannerViewModel(store)

        assertFalse(viewModel.enabled.value)
        store.enable()
        assertTrue(viewModel.enabled.value)
        store.disable()
        assertFalse(viewModel.enabled.value)
    }
}
