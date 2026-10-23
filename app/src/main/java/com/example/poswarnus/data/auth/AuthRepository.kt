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

    /**
     * Makes sure the live Supabase session exists before a sync, restoring the stored one if needed.
     * Unlike [isLoggedIn] it never clears the stored session when restoring fails (e.g. offline).
     */
    suspend fun ensureSessionForSync(): Boolean

    /** Id of the signed-in cashier, or null when there is no session. */
    fun currentUserId(): String?
}

class AuthRepositoryImpl(
    private val gateway: AuthGateway,
    private val sessionStore: SessionStore,
) : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult =
        try {
            val sessionJson = gateway.signIn(email, password)
            sessionStore.save(sessionJson)
            cachedUserId = gateway.currentUserId()
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
            cachedUserId = null
            sessionStore.save(null)
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        val sessionJson = sessionStore.load() ?: return false
        val isValid = gateway.restoreSession(sessionJson)
        if (isValid) cachedUserId = gateway.currentUserId() else sessionStore.save(null)
        return isValid
    }

    override suspend fun ensureSessionForSync(): Boolean {
        if (gateway.currentUserId() != null) return true
        val sessionJson = sessionStore.load() ?: return false
        val restored = try {
            gateway.restoreSession(sessionJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (restored) cachedUserId = gateway.currentUserId()
        return restored
    }

    // supabase-kt reports no user once a token refresh fails on a network error, which would block
    // every offline sale; the id seen at login / session restore stays valid until logout.
    @Volatile
    private var cachedUserId: String? = null

    override fun currentUserId(): String? = gateway.currentUserId() ?: cachedUserId

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
