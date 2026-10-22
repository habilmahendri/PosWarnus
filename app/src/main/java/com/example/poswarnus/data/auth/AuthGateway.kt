package com.example.poswarnus.data.auth

interface AuthGateway {
    suspend fun signIn(email: String, password: String): String
    suspend fun signOut()
}
