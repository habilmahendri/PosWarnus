package com.example.poswarnus.data.sync

import com.example.poswarnus.data.remote.NoSessionException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SyncErrorsTest {
    private suspend fun response(status: HttpStatusCode): HttpResponse {
        val client = HttpClient(MockEngine { respond(content = "", status = status) })
        return client.get("https://project.example.supabase.co/rest/v1/rpc/submit_transaction") {
            header("Authorization", "Bearer SECRET-TOKEN-123")
        }
    }

    private suspend fun rest(status: Int, error: String = "error", description: String? = "description") =
        RestException(error, description, response(HttpStatusCode.fromValue(status)))

    private fun requestBuilder() = HttpRequestBuilder().apply { url("https://project.example.supabase.co/rest/v1/rpc/x") }

    @Test
    fun `stopsBatch is true for offline and session errors`() = runTest {
        assertTrue(SyncErrors.stopsBatch(NoSessionException()))
        assertTrue(SyncErrors.stopsBatch(IOException("boom")))
        assertTrue(SyncErrors.stopsBatch(HttpRequestException("down", requestBuilder())))
        assertTrue(SyncErrors.stopsBatch(HttpRequestTimeoutException(requestBuilder())))
        assertTrue(SyncErrors.stopsBatch(rest(401)))
    }

    @Test
    fun `stopsBatch is false for rejections and other errors`() = runTest {
        assertFalse(SyncErrors.stopsBatch(rest(403)))
        assertFalse(SyncErrors.stopsBatch(rest(404)))
        assertFalse(SyncErrors.stopsBatch(IllegalStateException("x")))
    }

    @Test
    fun `describe uses the offline prefix for network errors`() {
        assertTrue(SyncErrors.describe(IOException("boom")).startsWith("offline: "))
        assertTrue(SyncErrors.describe(HttpRequestException("down", requestBuilder())).startsWith("offline: "))
        assertTrue(SyncErrors.describe(HttpRequestTimeoutException(requestBuilder())).startsWith("offline: "))
    }

    @Test
    fun `describe uses the session prefix for no session and 401`() = runTest {
        assertTrue(SyncErrors.describe(NoSessionException()).startsWith("session: "))
        assertTrue(SyncErrors.describe(rest(401)).startsWith("session: "))
    }

    @Test
    fun `describe uses the rejected prefix for 403, 404 and other exceptions`() = runTest {
        assertTrue(SyncErrors.describe(rest(403)).startsWith("rejected: "))
        assertTrue(SyncErrors.describe(rest(404)).startsWith("rejected: "))
        assertTrue(SyncErrors.describe(IllegalStateException("x")).startsWith("rejected: "))
    }

    @Test
    fun `describe of a RestException keeps error and description but never the raw message`() = runTest {
        val text = SyncErrors.describe(rest(404, "PGRST202", "function missing"))
        assertTrue(text, text.contains("PGRST202"))
        assertTrue(text, text.contains("function missing"))
        assertFalse(text, text.contains("https://"))
        assertFalse(text, text.contains("URL"))
        assertFalse(text, text.contains("Headers"))
        assertFalse(text, text.contains("SECRET-TOKEN-123"))
        assertFalse(text, text.contains("Bearer"))
    }

    @Test
    fun `describe of a 401 RestException does not leak the token`() = runTest {
        val text = SyncErrors.describe(rest(401, "JWT expired", null))
        assertTrue(text, text.startsWith("session: "))
        assertFalse(text, text.contains("SECRET-TOKEN-123"))
        assertFalse(text, text.contains("https://"))
    }

    @Test
    fun `describe never contains the URL of a failed request`() {
        val text = SyncErrors.describe(HttpRequestException("down", requestBuilder()))
        assertFalse(text, text.contains("https://"))
    }

    @Test
    fun `describe is cut to 200 characters`() = runTest {
        assertEquals(200, SyncErrors.describe(IllegalStateException("a".repeat(1000))).length)
        assertTrue(SyncErrors.describe(rest(403, "e", "d".repeat(1000))).length <= 200)
    }
}
