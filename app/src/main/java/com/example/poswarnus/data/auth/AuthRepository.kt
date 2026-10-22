package com.example.poswarnus.data.auth

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean
}

class AuthRepositoryImpl(
    private val gateway: AuthGateway,
    private val sessionStore: SessionStore,
) : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult =
        try {
            val sessionJson = gateway.signIn(email, password)
            sessionStore.save(sessionJson)
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Login failed")
        }

    override suspend fun logout() {
        gateway.signOut()
        sessionStore.save(null)
    }

    override suspend fun isLoggedIn(): Boolean = sessionStore.load() != null
}
