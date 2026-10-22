package com.example.poswarnus.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

class SupabaseCatalogRemoteDataSource(private val client: SupabaseClient) : CatalogRemoteDataSource {
    override suspend fun fetchCategories(): List<CategoryDto> =
        client.postgrest.from("categories").select().decodeList()

    override suspend fun fetchMenuItems(): List<MenuItemDto> =
        client.postgrest.from("menu_items").select().decodeList()
}
