package com.example.poswarnus.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

/** Thrown instead of querying a table that only `authenticated` may read. */
class NoSessionException : IllegalStateException("No signed-in session; option tables require authentication")

/**
 * @param sessionAvailable whether the Auth plugin currently holds a session. Without one, Postgrest
 * falls back to the anon key and the RLS-protected option tables answer `[]`, which a full-replace
 * refresh would treat as "every option was deleted". Throwing instead makes the refresh fail and
 * keeps the cached options.
 */
class SupabaseCatalogRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionAvailable: () -> Boolean,
) : CatalogRemoteDataSource {
    override suspend fun fetchCategories(): List<CategoryDto> =
        client.postgrest.from("categories").select().decodeList()

    override suspend fun fetchMenuItems(): List<MenuItemDto> =
        client.postgrest.from("menu_items").select().decodeList()

    override suspend fun fetchOptionGroups(): List<OptionGroupDto> {
        requireSession()
        return client.postgrest.from("option_groups").select().decodeList()
    }

    override suspend fun fetchOptions(): List<OptionDto> {
        requireSession()
        return client.postgrest.from("options").select().decodeList()
    }

    private fun requireSession() {
        if (!sessionAvailable()) throw NoSessionException()
    }
}
