package com.example.poswarnus.data.auth

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(mapLoginError(e))
        }

    override suspend fun logout() {
        try {
            gateway.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Local session state must be cleared even if the remote sign-out call fails (e.g.
            // while offline), otherwise the app would keep treating the user as logged in.
        } finally {
            sessionStore.save(null)
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        val sessionJson = sessionStore.load() ?: return false
        val isValid = gateway.restoreSession(sessionJson)
        if (!isValid) sessionStore.save(null)
        return isValid
    }

    private fun mapLoginError(e: Exception): String = when {
        // Supabase's own rest exceptions (and any RestException in general) embed the request
        // URL, headers and HTTP method in their message, which is not appropriate to show.
        e is AuthRestException -> "Email atau kata sandi salah. Periksa lalu coba lagi."
        e is RestException -> "Gagal masuk. Silakan coba lagi."
        e is HttpRequestException -> "Tidak bisa terhubung. Periksa koneksi lalu coba lagi."
        e is HttpRequestTimeoutException -> "Tidak bisa terhubung. Periksa koneksi lalu coba lagi."
        e is java.io.IOException -> "Tidak bisa terhubung. Periksa koneksi lalu coba lagi."
        else -> "Gagal masuk. Silakan coba lagi."
    }
}
