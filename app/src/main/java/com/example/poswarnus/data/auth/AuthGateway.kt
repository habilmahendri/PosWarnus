package com.example.poswarnus.data.auth

interface AuthGateway {
    suspend fun signIn(email: String, password: String): String
    suspend fun signOut()

    /**
     * Restores a previously persisted session (as produced by [signIn]) and validates it.
     *
     * Returns true if the session is valid (or was refreshed successfully), false if it is
     * expired/revoked and could not be refreshed, or if the session could not be restored at all.
     */
    suspend fun restoreSession(sessionJson: String): Boolean

    /** Id of the signed-in user, or null when there is no session. */
    fun currentUserId(): String?
}
