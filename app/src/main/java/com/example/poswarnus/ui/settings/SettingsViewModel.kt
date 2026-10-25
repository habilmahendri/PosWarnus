package com.example.poswarnus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.remote.NoSessionException
import com.example.poswarnus.data.remote.TrainingAuditAction
import com.example.poswarnus.data.remote.TrainingPinRemoteDataSource
import com.example.poswarnus.data.sync.SyncErrors
import com.example.poswarnus.data.training.TrainingModeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val trainingEnabled: Boolean = false,
    val pinDialog: PinDialogState? = null,
)

data class PinDialogState(
    val pinInput: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trainingMode: TrainingModeStore,
    private val trainingPinRemote: TrainingPinRemoteDataSource,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val pinDialog = MutableStateFlow<PinDialogState?>(null)
    private var pinVerificationJob: Job? = null

    val uiState: StateFlow<SettingsUiState> =
        combine(trainingMode.enabled, pinDialog) { enabled, dialog ->
            SettingsUiState(trainingEnabled = enabled, pinDialog = dialog)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(trainingEnabled = trainingMode.enabled.value),
        )

    /** Turning on opens the PIN dialog; turning off disables at once, closing the dialog if it was open. */
    fun onTrainingToggle(checked: Boolean) {
        if (checked) {
            pinDialog.value = PinDialogState()
        } else {
            pinVerificationJob?.cancel()
            pinDialog.value = null
            trainingMode.disable()
            viewModelScope.launch {
                runCatching {
                    trainingPinRemote.recordTransition(authRepository.currentUserId().orEmpty(), TrainingAuditAction.DISABLE)
                }
            }
        }
    }

    fun onPinInputChange(value: String) {
        pinDialog.value = pinDialog.value?.copy(pinInput = value)
    }

    fun onPinCancel() {
        pinDialog.value = null
    }

    fun onPinSubmit() {
        val dialog = pinDialog.value ?: return
        if (dialog.submitting) return
        pinDialog.value = dialog.copy(submitting = true)
        pinVerificationJob = viewModelScope.launch {
            try {
                val verified = trainingPinRemote.verifyPin(dialog.pinInput)
                if (verified) {
                    trainingMode.enable()
                    runCatching {
                        trainingPinRemote.recordTransition(authRepository.currentUserId().orEmpty(), TrainingAuditAction.ENABLE)
                    }
                    pinDialog.value = null
                } else {
                    pinDialog.value = PinDialogState(error = "PIN salah")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                pinDialog.value = if (SyncErrors.isOffline(e) || e is NoSessionException) {
                    PinDialogState(error = "Perlu koneksi untuk verifikasi PIN")
                } else {
                    PinDialogState(error = "PIN salah")
                }
            }
        }
    }
}
