package com.example.poswarnus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.training.TrainingModeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val trainingEnabled: Boolean = false,
    val confirmingEnable: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trainingMode: TrainingModeStore,
) : ViewModel() {
    private val confirmingEnable = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(trainingMode.enabled, confirmingEnable) { enabled, confirming ->
            SettingsUiState(trainingEnabled = enabled, confirmingEnable = confirming)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(trainingEnabled = trainingMode.enabled.value),
        )

    /** Turning on needs a confirmation because it empties the cart; turning off applies at once. */
    fun onTrainingToggle(checked: Boolean) {
        if (checked) {
            confirmingEnable.value = true
        } else {
            trainingMode.disable()
        }
    }

    fun confirmEnable() {
        trainingMode.enable()
        confirmingEnable.value = false
    }

    fun cancelEnable() {
        confirmingEnable.value = false
    }
}
