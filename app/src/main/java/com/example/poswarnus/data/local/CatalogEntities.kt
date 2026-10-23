package com.example.poswarnus.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val isAvailable: Boolean,
    val description: String? = null,
    // defaultValue must match the DDL in MIGRATION_2_3 exactly, or Room schema validation fails.
    @ColumnInfo(defaultValue = "0") val isFeatured: Boolean = false,
)

@Entity(tableName = "option_groups", indices = [Index("menuItemId")])
data class OptionGroupEntity(
    @PrimaryKey val id: String,
    val menuItemId: String,
    val name: String,
    val kind: String,
    val selection: String,
    val isRequired: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "options", indices = [Index("groupId")])
data class OptionEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val priceDelta: Long,
    val sortOrder: Int,
    val isAvailable: Boolean,
)
