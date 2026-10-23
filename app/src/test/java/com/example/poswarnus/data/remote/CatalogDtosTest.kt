package com.example.poswarnus.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDtosTest {
    // Same leniency the Supabase client's default serializer uses (ignoreUnknownKeys).
    private val json = Json { ignoreUnknownKeys = true }

    private fun row(extra: String = "") =
        """{"id":"1","category_id":"a","name":"Nasi Goreng","price":25000.0,"image_url":null,"is_available":true$extra}"""

    @Test
    fun `menu item without is_featured decodes as not featured`() {
        assertFalse(json.decodeFromString<MenuItemDto>(row()).isFeatured)
    }

    @Test
    fun `menu item with is_featured true decodes as featured`() {
        assertTrue(json.decodeFromString<MenuItemDto>(row(""","is_featured":true""")).isFeatured)
    }
}
