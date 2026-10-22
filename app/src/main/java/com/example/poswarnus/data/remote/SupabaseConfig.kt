package com.example.poswarnus.data.remote

data class SupabaseConfig(val url: String, val anonKey: String)

fun SupabaseConfig.requireValid(): SupabaseConfig {
    check(url.isNotBlank()) { "Supabase URL is missing. Set SUPABASE_URL in local.properties." }
    check(anonKey.isNotBlank()) { "Supabase anon key is missing. Set SUPABASE_ANON_KEY in local.properties." }
    return this
}
