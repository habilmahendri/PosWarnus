package com.example.poswarnus.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
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
}
