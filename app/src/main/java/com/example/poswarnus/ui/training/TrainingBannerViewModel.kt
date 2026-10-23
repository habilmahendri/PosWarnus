package com.example.poswarnus.ui.training

import androidx.lifecycle.ViewModel
import com.example.poswarnus.data.training.TrainingModeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TrainingBannerViewModel @Inject constructor(
    trainingMode: TrainingModeStore,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = trainingMode.enabled
}
