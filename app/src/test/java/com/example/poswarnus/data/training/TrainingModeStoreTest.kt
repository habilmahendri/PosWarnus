package com.example.poswarnus.data.training

import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.cart.CartStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingModeStoreTest {
    private val cartStore = CartStore()
    private val store = TrainingModeStore(cartStore)

    private fun addSampleLine() =
        cartStore.add(CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 2))

    @Test
    fun `starts disabled`() {
        assertFalse(store.enabled.value)
    }

    @Test
    fun `enable clears the cart and turns training on`() {
        addSampleLine()

        store.enable()

        assertTrue(store.enabled.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
    }

    @Test
    fun `disable clears the cart and turns training off`() {
        store.enable()
        addSampleLine()

        store.disable()

        assertFalse(store.enabled.value)
        assertTrue(cartStore.state.value.lines.isEmpty())
    }

    @Test
    fun `reset turns training off and keeps the cart`() {
        store.enable()
        addSampleLine()

        store.reset()

        assertFalse(store.enabled.value)
        assertEquals(1, cartStore.state.value.lines.size)
    }
}
