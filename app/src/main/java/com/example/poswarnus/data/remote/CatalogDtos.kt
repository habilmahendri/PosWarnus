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
    @SerialName("description") val description: String? = null,
    // Default keeps the app working against a Supabase that has no is_featured column yet.
    @SerialName("is_featured") val isFeatured: Boolean = false,
)

/** The single `tax_settings` row; `percent` is e.g. 10 or 7.5. */
@Serializable
data class TaxSettingsDto(
    val label: String,
    val percent: Double,
)

@Serializable
data class OptionGroupDto(
    val id: String,
    @SerialName("menu_item_id") val menuItemId: String,
    val name: String,
    val kind: String,
    val selection: String,
    @SerialName("is_required") val isRequired: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
data class OptionDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val name: String,
    @SerialName("price_delta") val priceDelta: Double,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_available") val isAvailable: Boolean,
)
