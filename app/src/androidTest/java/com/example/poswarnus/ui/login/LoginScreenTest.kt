package com.example.poswarnus.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthResult
import org.junit.Rule
import org.junit.Test

private class NeverCalledAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult {
        error("login() should never be called")
    }

    override suspend fun logout() {
        error("logout() should never be called")
    }

    override suspend fun isLoggedIn(): Boolean = false
}

private class AlwaysSucceedsAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult = AuthResult.Success

    override suspend fun logout() {}

    override suspend fun isLoggedIn(): Boolean = false
}

class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingLoginWithEmptyFieldsShowsValidationError() {
        // Uses a real LoginViewModel backed by a repository whose isLoggedIn() is false and
        // login() is never expected to be called for this case, so any AuthRepository stub works.
        composeTestRule.setContent {
            LoginScreen(onNavigateToDashboard = {}, viewModel = LoginViewModel(NeverCalledAuthRepository()))
        }

        composeTestRule.onNodeWithText("Login").performClick()

        composeTestRule.onNodeWithText("Email and password are required").assertExists()
    }

    @Test
    fun enteringCredentialsAndLoggingInNavigatesOnSuccess() {
        var navigated = false
        composeTestRule.setContent {
            LoginScreen(
                onNavigateToDashboard = { navigated = true },
                viewModel = LoginViewModel(AlwaysSucceedsAuthRepository()),
            )
        }

        composeTestRule.onNodeWithText("Email").performTextInput("cashier@warnus.test")
        composeTestRule.onNodeWithText("Password").performTextInput("correct-password")
        composeTestRule.onNodeWithText("Login").performClick()
        composeTestRule.waitForIdle()

        assert(navigated)
    }
}
