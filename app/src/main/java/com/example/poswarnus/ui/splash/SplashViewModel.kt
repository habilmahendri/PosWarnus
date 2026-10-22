package com.example.poswarnus.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashEvent {
    data object NavigateToLogin : SplashEvent
}

@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {
    private var delayProvider: suspend () -> Unit = { delay(1500) }

    constructor(delayProvider: suspend () -> Unit) : this() {
        this.delayProvider = delayProvider
    }

    private val _events = MutableSharedFlow<SplashEvent>()
    val events: SharedFlow<SplashEvent> = _events.asSharedFlow()

    fun start() {
        viewModelScope.launch {
            delayProvider()
            _events.emit(SplashEvent.NavigateToLogin)
        }
    }
}
