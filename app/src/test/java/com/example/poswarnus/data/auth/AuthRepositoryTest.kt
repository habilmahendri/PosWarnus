package com.example.poswarnus.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAuthGateway(
    private val signInResult: Result<String> = Result.success("""{"access_token":"abc"}"""),
) : AuthGateway {
    var signInCallCount = 0
        private set
    var signOutCalled = false
        private set

    override suspend fun signIn(email: String, password: String): String {
        signInCallCount++
        return signInResult.getOrThrow()
    }

    override suspend fun signOut() {
        signOutCalled = true
    }
}

private class InMemorySessionStore(initial: String? = null) : SessionStore {
    var current: String? = initial
    override suspend fun save(sessionJson: String?) { current = sessionJson }
    override suspend fun load(): String? = current
}

class AuthRepositoryTest {
    @Test
    fun `login success persists the session and returns Success`() = runTest {
        val sessionStore = InMemorySessionStore()
        val repository = AuthRepositoryImpl(FakeAuthGateway(), sessionStore)

        val result = repository.login("cashier@warnus.test", "correct-password")

        assertEquals(AuthResult.Success, result)
        assertEquals("""{"access_token":"abc"}""", sessionStore.current)
    }

    @Test
    fun `login failure does not persist a session and returns Failure`() = runTest {
        val sessionStore = InMemorySessionStore()
        val gateway = FakeAuthGateway(signInResult = Result.failure(RuntimeException("Invalid login credentials")))
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        val result = repository.login("cashier@warnus.test", "wrong-password")

        assertTrue(result is AuthResult.Failure)
        assertEquals(null, sessionStore.current)
    }

    @Test
    fun `logout clears the persisted session`() = runTest {
        val sessionStore = InMemorySessionStore(initial = """{"access_token":"abc"}""")
        val gateway = FakeAuthGateway()
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        repository.logout()

        assertTrue(gateway.signOutCalled)
        assertEquals(null, sessionStore.current)
    }

    @Test
    fun `isLoggedIn reflects whether a session is persisted`() = runTest {
        val repository = AuthRepositoryImpl(FakeAuthGateway(), InMemorySessionStore(initial = null))
        assertFalse(repository.isLoggedIn())

        val repositoryWithSession = AuthRepositoryImpl(
            FakeAuthGateway(),
            InMemorySessionStore(initial = """{"access_token":"abc"}"""),
        )
        assertTrue(repositoryWithSession.isLoggedIn())
    }
}
