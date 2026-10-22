package com.example.poswarnus.data.remote

interface CatalogRemoteDataSource {
    suspend fun fetchCategories(): List<CategoryDto>
    suspend fun fetchMenuItems(): List<MenuItemDto>
}
