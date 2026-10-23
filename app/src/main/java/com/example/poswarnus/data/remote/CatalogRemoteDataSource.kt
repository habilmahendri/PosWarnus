package com.example.poswarnus.data.remote

interface CatalogRemoteDataSource {
    suspend fun fetchCategories(): List<CategoryDto>
    suspend fun fetchMenuItems(): List<MenuItemDto>
    suspend fun fetchOptionGroups(): List<OptionGroupDto>
    suspend fun fetchOptions(): List<OptionDto>
}
