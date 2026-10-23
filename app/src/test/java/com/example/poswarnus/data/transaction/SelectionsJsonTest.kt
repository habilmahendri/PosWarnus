package com.example.poswarnus.data.transaction

import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.ui.cart.SelectedOption
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionsJsonTest {
    @Test
    fun `round-trips a variant and a modifier including a negative delta`() {
        val selections = listOf(
            SelectedOption("o1", "g1", "Large", 5000, OptionKind.VARIANT),
            SelectedOption("o2", "g2", "Tanpa Gula", -1000, OptionKind.MODIFIER),
        )
        assertEquals(selections, SelectionsJson.decode(SelectionsJson.encode(selections)))
    }

    @Test
    fun `round-trips an empty list`() {
        assertEquals("[]", SelectionsJson.encode(emptyList()))
        assertEquals(emptyList<SelectedOption>(), SelectionsJson.decode("[]"))
    }

    @Test
    fun `quotes and unicode in names survive`() {
        val selections = listOf(SelectedOption("o1", "g1", "Extra \"Keju\" ☕ é", 0, OptionKind.MODIFIER))
        assertEquals(selections, SelectionsJson.decode(SelectionsJson.encode(selections)))
    }
}
