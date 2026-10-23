package com.example.poswarnus.data.training

import com.example.poswarnus.ui.cart.CartStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory training-mode flag. Deliberately never persisted: a process restart, logout or login
 * always returns to real mode.
 */
@Singleton
class TrainingModeStore @Inject constructor(
    private val cartStore: CartStore,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Switching modes starts from an empty cart so real and training lines never mix. */
    fun enable() {
        cartStore.clear()
        _enabled.value = true
    }

    fun disable() {
        cartStore.clear()
        _enabled.value = false
    }

    /** Used by logout and login, which clear the cart themselves. */
    fun reset() {
        _enabled.value = false
    }
}
