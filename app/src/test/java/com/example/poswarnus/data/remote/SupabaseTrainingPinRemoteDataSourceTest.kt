package com.example.poswarnus.data.remote

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

class SupabaseTrainingPinRemoteDataSourceTest {
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

    @Test
    fun `verifyPin throws NoSessionException without a session and never hits the network`() = runTest {
        val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { false })
        try {
            source.verifyPin("123456")
            fail("expected NoSessionException")
        } catch (e: NoSessionException) {
            // expected
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `verifyPin posts to the verify_training_pin rpc with the pin and decodes the boolean result`() = runTest {
        responseBody = "true"
        val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })

        val result = source.verifyPin("123456")

        assertTrue(result)
        assertEquals(1, requests.size)
        assertEquals("/rest/v1/rpc/verify_training_pin", requests[0].url.encodedPath)
        val body = (requests[0].body as TextContent).text
        assertEquals("123456", Json.parseToJsonElement(body).jsonObject.getValue("p_pin").jsonPrimitive.content)
    }

    @Test
    fun `verifyPin returns false for a false result`() = runTest {
        responseBody = "false"
        val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })

        assertEquals(false, source.verifyPin("000000"))
    }

    @Test
    fun `verifyPin propagates a server error`() = runTest {
        status = HttpStatusCode.InternalServerError
        responseBody = """{"code":"XX000","message":"boom","details":null,"hint":null}"""
        val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })
        try {
            source.verifyPin("123456")
            fail("expected RestException")
        } catch (e: RestException) {
            // expected
        }
    }

    @Test
    fun `recordTransition throws NoSessionException without a session and never hits the network`() = runTest {
        val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { false })
        try {
            source.recordTransition("cashier-1", TrainingAuditAction.ENABLE)
            fail("expected NoSessionException")
        } catch (e: NoSessionException) {
            // expected
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `recordTransition posts to the training_mode_audit table with cashier id and action`() = runTest {
        val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })

        source.recordTransition("cashier-1", TrainingAuditAction.ENABLE)

        assertEquals(1, requests.size)
        assertEquals("/rest/v1/training_mode_audit", requests[0].url.encodedPath)
        val body = (requests[0].body as TextContent).text
        val json = Json.parseToJsonElement(body).jsonArray[0].jsonObject
        assertEquals("cashier-1", json.getValue("cashier_id").jsonPrimitive.content)
        assertEquals("enable", json.getValue("action").jsonPrimitive.content)
    }
}
