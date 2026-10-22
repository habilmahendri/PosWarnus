package com.example.poswarnus.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class SupabaseAuthGateway(private val client: SupabaseClient) : AuthGateway {
    override suspend fun signIn(email: String, password: String): String {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val session = requireNotNull(client.auth.currentSessionOrNull()) {
            "Supabase reported a successful sign-in but returned no session"
        }
        return Json.encodeToString(session)
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun restoreSession(sessionJson: String): Boolean =
        try {
            val session = Json.decodeFromString<UserSession>(sessionJson)
            // importSession(autoRefresh = true) refreshes the session using the stored refresh
            // token when it is already expired. On a network failure while refreshing, the
            // underlying supabase-kt implementation retries indefinitely with a delay, so this is
            // bounded with a timeout to avoid hanging app startup while offline.
            val completed = withTimeoutOrNull(RESTORE_TIMEOUT_MS) {
                client.auth.importSession(session, autoRefresh = true)
            }
            completed != null && client.auth.sessionStatus.value is SessionStatus.Authenticated
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    private companion object {
        const val RESTORE_TIMEOUT_MS = 10_000L
    }
}
