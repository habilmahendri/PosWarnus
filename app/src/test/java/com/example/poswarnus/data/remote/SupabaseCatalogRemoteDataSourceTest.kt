package com.example.poswarnus.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SupabaseCatalogRemoteDataSourceTest {
    private val requestedPaths = mutableListOf<String>()

    private val client = createSupabaseClient("https://example.supabase.co", "anon-key") {
        install(Postgrest)
        httpEngine = MockEngine { request ->
            requestedPaths += request.url.encodedPath
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private suspend fun assertThrowsNoSession(block: suspend () -> Unit) {
        try {
            block()
            fail("expected NoSessionException")
        } catch (e: NoSessionException) {
            // expected
        }
    }

    @Test
    fun `option fetches throw without a session and never hit the network`() = runTest {
        val source = SupabaseCatalogRemoteDataSource(client, sessionAvailable = { false })

        assertThrowsNoSession { source.fetchOptionGroups() }
        assertThrowsNoSession { source.fetchOptions() }
        assertTrue(requestedPaths.isEmpty())
    }

    @Test
    fun `option fetches query Postgrest when a session exists`() = runTest {
        val source = SupabaseCatalogRemoteDataSource(client, sessionAvailable = { true })

        assertEquals(emptyList<OptionGroupDto>(), source.fetchOptionGroups())
        assertEquals(emptyList<OptionDto>(), source.fetchOptions())
        assertEquals(listOf("/rest/v1/option_groups", "/rest/v1/options"), requestedPaths)
    }

    @Test
    fun `categories and menu items still fetch without a session`() = runTest {
        val source = SupabaseCatalogRemoteDataSource(client, sessionAvailable = { false })

        assertEquals(emptyList<CategoryDto>(), source.fetchCategories())
        assertEquals(emptyList<MenuItemDto>(), source.fetchMenuItems())
        assertEquals(listOf("/rest/v1/categories", "/rest/v1/menu_items"), requestedPaths)
    }
}
