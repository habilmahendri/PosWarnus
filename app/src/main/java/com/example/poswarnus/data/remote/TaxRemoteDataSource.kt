package com.example.poswarnus.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

interface TaxRemoteDataSource {
    /** The configured tax row, or null when the table has none. */
    suspend fun fetchTaxSettings(): TaxSettingsDto?
}

/** `tax_settings` is readable by `authenticated` only, so a missing session throws instead of reading `[]`. */
class SupabaseTaxRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionAvailable: () -> Boolean,
) : TaxRemoteDataSource {
    override suspend fun fetchTaxSettings(): TaxSettingsDto? {
        if (!sessionAvailable()) throw NoSessionException()
        return client.postgrest.from("tax_settings").select().decodeList<TaxSettingsDto>().firstOrNull()
    }
}
