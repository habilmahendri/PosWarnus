package com.example.poswarnus.data.catalog

enum class OptionKind { VARIANT, MODIFIER }

enum class SelectionMode { SINGLE, MULTI }

data class Option(
    val id: String,
    val groupId: String,
    val name: String,
    val priceDelta: Long,
    val sortOrder: Int,
    val isAvailable: Boolean,
)

data class OptionGroup(
    val id: String,
    val menuItemId: String,
    val name: String,
    val kind: OptionKind,
    val selection: SelectionMode,
    val isRequired: Boolean,
    val sortOrder: Int,
    val options: List<Option>,
)
