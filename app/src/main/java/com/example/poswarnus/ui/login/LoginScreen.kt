package com.example.poswarnus.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is LoginEvent.NavigateToDashboard) onNavigateToDashboard()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        OutlinedTextField(value = state.email, onValueChange = viewModel::onEmailChanged, label = { Text("Email") })
        OutlinedTextField(value = state.password, onValueChange = viewModel::onPasswordChanged, label = { Text("Password") })
        state.errorMessage?.let { Text(it) }
        Button(onClick = viewModel::onLoginClicked, enabled = !state.isLoading) {
            if (state.isLoading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text("Login")
        }
    }
}
