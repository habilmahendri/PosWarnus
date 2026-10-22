package com.example.poswarnus.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
data class MenuItemDto(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val price: Double,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("is_available") val isAvailable: Boolean,
)
