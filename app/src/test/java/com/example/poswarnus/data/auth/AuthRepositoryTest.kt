package com.example.poswarnus.data.auth

import io.github.jan.supabase.auth.exception.AuthRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAuthGateway(
    private val signInResult: Result<String> = Result.success("""{"access_token":"abc"}"""),
    private val signOutResult: Result<Unit> = Result.success(Unit),
    private val restoreSessionResult: Result<Boolean> = Result.success(true),
) : AuthGateway {
    var signInCallCount = 0
        private set
    var signOutCalled = false
        private set
    var restoreSessionCallCount = 0
        private set

    override suspend fun signIn(email: String, password: String): String {
        signInCallCount++
        return signInResult.getOrThrow()
    }

    override suspend fun signOut() {
        signOutCalled = true
        signOutResult.getOrThrow()
    }

    override suspend fun restoreSession(sessionJson: String): Boolean {
        restoreSessionCallCount++
        return restoreSessionResult.getOrThrow()
    }

    /** What the live Supabase session would report; null models an expired or failed-refresh session. */
    var userId: String? = "user-1"

    override fun currentUserId(): String? = userId
}

private suspend fun fakeHttpResponse(statusCode: HttpStatusCode): HttpResponse {
    val client = HttpClient(MockEngine { request -> respond(content = "", status = statusCode) })
    return client.get("https://example.supabase.co/auth/v1/token")
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
    fun `currentUserId keeps the cashier id after login when the live session is lost offline`() = runTest {
        val gateway = FakeAuthGateway()
        val repository = AuthRepositoryImpl(gateway, InMemorySessionStore())
        repository.login("kasir@warnus.test", "pw")

        gateway.userId = null // token expired and refresh failed on a network error

        assertEquals("user-1", repository.currentUserId())
    }

    @Test
    fun `currentUserId keeps the cashier id after a stored session is restored`() = runTest {
        val gateway = FakeAuthGateway()
        val repository = AuthRepositoryImpl(gateway, InMemorySessionStore(initial = """{"access_token":"abc"}"""))
        assertTrue(repository.isLoggedIn())

        gateway.userId = null

        assertEquals("user-1", repository.currentUserId())
    }

    @Test
    fun `currentUserId is null after logout even if it was cached`() = runTest {
        val gateway = FakeAuthGateway()
        val repository = AuthRepositoryImpl(gateway, InMemorySessionStore())
        repository.login("kasir@warnus.test", "pw")
        repository.logout()

        gateway.userId = null

        assertEquals(null, repository.currentUserId())
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
    fun `login failure with invalid credentials maps to a user-friendly message, not the raw exception text`() = runTest {
        val sessionStore = InMemorySessionStore()
        val response = fakeHttpResponse(HttpStatusCode.BadRequest)
        val authException = AuthRestException("invalid_credentials", "Invalid login credentials", response)
        val gateway = FakeAuthGateway(signInResult = Result.failure(authException))
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        val result = repository.login("cashier@warnus.test", "wrong-password") as AuthResult.Failure

        assertEquals("Email atau kata sandi salah. Periksa lalu coba lagi.", result.message)
        // The raw exception message embeds the request URL/headers/method - must never leak to the UI.
        assertFalse(result.message.contains("URL"))
        assertFalse(result.message.contains("Headers"))
    }

    @Test
    fun `login failure with an unknown exception maps to a generic message, not the raw exception text`() = runTest {
        val sessionStore = InMemorySessionStore()
        val gateway = FakeAuthGateway(
            signInResult = Result.failure(RuntimeException("some internal detail that should not reach the UI")),
        )
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        val result = repository.login("cashier@warnus.test", "wrong-password") as AuthResult.Failure

        assertEquals("Gagal masuk. Silakan coba lagi.", result.message)
    }

    @Test
    fun `login failure with a network IOException maps to a connectivity message`() = runTest {
        val sessionStore = InMemorySessionStore()
        val gateway = FakeAuthGateway(
            signInResult = Result.failure(java.io.IOException("Unable to resolve host")),
        )
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        val result = repository.login("cashier@warnus.test", "wrong-password") as AuthResult.Failure

        assertEquals("Tidak bisa terhubung. Periksa koneksi lalu coba lagi.", result.message)
    }

    @Test
    fun `login failure never exposes the raw exception message`() = runTest {
        val raw = "Request failed for https://example.supabase.co/auth/v1/token?secret=1"
        val gateway = FakeAuthGateway(signInResult = Result.failure(RuntimeException(raw)))
        val repository = AuthRepositoryImpl(gateway, InMemorySessionStore())

        val result = repository.login("cashier@warnus.test", "wrong-password") as AuthResult.Failure

        val allowed = setOf(
            "Email atau kata sandi salah. Periksa lalu coba lagi.",
            "Gagal masuk. Silakan coba lagi.",
            "Tidak bisa terhubung. Periksa koneksi lalu coba lagi.",
        )
        assertTrue(result.message in allowed)
        assertFalse(result.message.contains("https://"))
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
    fun `logout clears the persisted session even when the remote sign-out call fails`() = runTest {
        val sessionStore = InMemorySessionStore(initial = """{"access_token":"abc"}""")
        val gateway = FakeAuthGateway(signOutResult = Result.failure(RuntimeException("offline")))
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        repository.logout()

        assertTrue(gateway.signOutCalled)
        assertEquals(null, sessionStore.current)
    }

    @Test
    fun `isLoggedIn returns false immediately when nothing is stored, without calling the gateway`() = runTest {
        val gateway = FakeAuthGateway()
        val repository = AuthRepositoryImpl(gateway, InMemorySessionStore(initial = null))

        assertFalse(repository.isLoggedIn())
        assertEquals(0, gateway.restoreSessionCallCount)
    }

    @Test
    fun `isLoggedIn returns true when a stored session restores successfully`() = runTest {
        val gateway = FakeAuthGateway(restoreSessionResult = Result.success(true))
        val sessionStore = InMemorySessionStore(initial = """{"access_token":"abc"}""")
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        assertTrue(repository.isLoggedIn())
        assertEquals("""{"access_token":"abc"}""", sessionStore.current)
    }

    @Test
    fun `isLoggedIn returns false and clears storage when restoration fails`() = runTest {
        val gateway = FakeAuthGateway(restoreSessionResult = Result.success(false))
        val sessionStore = InMemorySessionStore(initial = """{"access_token":"abc"}""")
        val repository = AuthRepositoryImpl(gateway, sessionStore)

        assertFalse(repository.isLoggedIn())
        assertEquals(null, sessionStore.current)
    }
}
