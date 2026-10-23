package com.example.poswarnus.data.remote

import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.sync.PendingSale
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SupabaseTransactionRemoteDataSourceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var responseBody = "null"

    private val client = createSupabaseClient("https://example.supabase.co", "anon-key") {
        install(Postgrest)
        httpEngine = MockEngine { request ->
            requests += request
            respond(responseBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private val sale = PendingSale(
        header = TransactionEntity(
            id = "tx-1", createdAt = 1_000L, cashierId = "user-1", subtotal = 30_000L,
            taxLabel = "PPN", taxBasisPoints = 1100, taxAmount = 3_300L, grandTotal = 33_300L,
            cashReceived = 50_000L, changeAmount = 16_700L,
        ),
        lines = listOf(
            TransactionLineEntity("tx-1:0", "tx-1", "m1", "Kopi", 15_000L, 2, "[]"),
            TransactionLineEntity("tx-1:1", "tx-1", "m2", "Teh", 10_000L, 1, "[]"),
        ),
    )

    @Test
    fun `submit throws NoSessionException without a session and never hits the network`() = runTest {
        val source = SupabaseTransactionRemoteDataSource(client, sessionAvailable = { false })
        try {
            source.submit(sale)
            fail("expected NoSessionException")
        } catch (e: NoSessionException) {
            // expected
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `submit posts to the submit_transaction rpc with the sale under p`() = runTest {
        val source = SupabaseTransactionRemoteDataSource(client, sessionAvailable = { true })

        source.submit(sale)

        assertEquals(1, requests.size)
        assertEquals("/rest/v1/rpc/submit_transaction", requests[0].url.encodedPath)
        val body = (requests[0].body as TextContent).text
        val p = Json.parseToJsonElement(body).jsonObject.getValue("p").jsonObject
        assertEquals("tx-1", p.getValue("id").jsonPrimitive.content)
        assertEquals(2, p.getValue("lines").jsonArray.size)
    }

    @Test
    fun `submit propagates a server error`() = runTest {
        status = HttpStatusCode.NotFound
        responseBody = """{"code":"PGRST202","message":"Could not find the function","details":null,"hint":null}"""
        val source = SupabaseTransactionRemoteDataSource(client, sessionAvailable = { true })
        try {
            source.submit(sale)
            fail("expected RestException")
        } catch (e: RestException) {
            // expected
        }
    }
}
