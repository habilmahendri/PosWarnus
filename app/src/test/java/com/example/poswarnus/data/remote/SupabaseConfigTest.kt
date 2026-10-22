package com.example.poswarnus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SupabaseConfigTest {
    @Test
    fun `requireValid returns the same config when url and anonKey are non-blank`() {
        val config = SupabaseConfig(url = "https://example.supabase.co", anonKey = "key123")
        assertEquals(config, config.requireValid())
    }

    @Test
    fun `requireValid throws when url is blank`() {
        assertThrows(IllegalStateException::class.java) {
            SupabaseConfig(url = "", anonKey = "key123").requireValid()
        }
    }

    @Test
    fun `requireValid throws when anonKey is blank`() {
        assertThrows(IllegalStateException::class.java) {
            SupabaseConfig(url = "https://example.supabase.co", anonKey = "").requireValid()
        }
    }
}
