package com.example.poswarnus.data.transaction

import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.ui.cart.SelectedOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stored form of a line's chosen options; also the shape Phase 5 uploads as `jsonb`. */
object SelectionsJson {
    @Serializable
    private data class Stored(
        val optionId: String,
        val groupId: String,
        val name: String,
        val priceDelta: Long,
        val kind: String,
    )

    fun encode(selections: List<SelectedOption>): String =
        Json.encodeToString(selections.map { Stored(it.optionId, it.groupId, it.name, it.priceDelta, it.kind.name) })

    fun decode(json: String): List<SelectedOption> =
        Json.decodeFromString<List<Stored>>(json).map {
            SelectedOption(it.optionId, it.groupId, it.name, it.priceDelta, OptionKind.valueOf(it.kind))
        }
}
