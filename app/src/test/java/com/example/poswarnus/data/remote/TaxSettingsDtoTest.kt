package com.example.poswarnus.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxSettingsDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes an integer percent`() {
        assertEquals(
            TaxSettingsDto("Pajak", 10.0),
            json.decodeFromString<TaxSettingsDto>("""{"label":"Pajak","percent":10}"""),
        )
    }

    @Test
    fun `decodes a numeric percent with decimals`() {
        assertEquals(
            TaxSettingsDto("Pajak", 10.0),
            json.decodeFromString<TaxSettingsDto>("""{"id":1,"label":"Pajak","percent":10.00}"""),
        )
    }
}
