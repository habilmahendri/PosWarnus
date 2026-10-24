package com.example.poswarnus.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.training.TrainingModeStore
import com.example.poswarnus.ui.cart.CartStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogoutViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cartStore: CartStore,
    private val trainingModeStore: TrainingModeStore,
) : ViewModel() {
    private val _confirming = MutableStateFlow(false)
    val confirming: StateFlow<Boolean> = _confirming.asStateFlow()

    fun requestLogout() {
        _confirming.value = true
    }

    fun cancelLogout() {
        _confirming.value = false
    }

    fun logout(onLoggedOut: () -> Unit) {
        _confirming.value = false
        viewModelScope.launch {
            authRepository.logout()
            // One cashier's order must never leak into the next session.
            cartStore.clear()
            trainingModeStore.reset()
            onLoggedOut()
        }
    }
}
