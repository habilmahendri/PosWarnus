package com.example.poswarnus.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import com.example.poswarnus.ui.cart.CartStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginEvent {
    data object NavigateToDashboard : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cartStore: CartStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) _events.send(LoginEvent.NavigateToDashboard)
        }
    }

    fun onEmailChanged(value: String) { _uiState.value = _uiState.value.copy(email = value, errorMessage = null) }
    fun onPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(password = value, errorMessage = null) }

    fun onLoginClicked() {
        val state = _uiState.value
        if (state.isLoading) return
        val email = state.email.trim()
        if (email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Email dan kata sandi wajib diisi.")
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authRepository.login(email, state.password)) {
                is AuthResult.Success -> {
                    // A back-exit keeps the process (and the singleton cart) alive without a logout,
                    // so the previous cashier's order must not reach this session.
                    cartStore.clear()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(LoginEvent.NavigateToDashboard)
                }
                is AuthResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
