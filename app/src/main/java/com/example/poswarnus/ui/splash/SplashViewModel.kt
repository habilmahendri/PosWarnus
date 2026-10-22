package com.example.poswarnus.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _events = Channel<SplashEvent>(Channel.BUFFERED)
    val events: Flow<SplashEvent> = _events.receiveAsFlow()

    fun start() {
        viewModelScope.launch {
            delayProvider()
            _events.send(SplashEvent.NavigateToLogin)
        }
    }
}
