package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.Option
import com.example.poswarnus.data.catalog.OptionGroup
import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.catalog.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDraftTest {
    private val item = MenuItem("m1", "c1", "Nasi Goreng", 25000.0, null, true)

    private fun opt(id: String, group: String, name: String, delta: Long, sort: Int, available: Boolean = true) =
        Option(id, group, name, delta, sort, available)

    private val size = OptionGroup(
        "g-size", "m1", "Ukuran", OptionKind.VARIANT, SelectionMode.SINGLE, true, 0,
        listOf(opt("regular", "g-size", "Regular", 0, 0), opt("large", "g-size", "Large", 10000, 1)),
    )
    private val extras = OptionGroup(
        "g-extra", "m1", "Tambahan", OptionKind.MODIFIER, SelectionMode.MULTI, false, 1,
        listOf(
            opt("keju", "g-extra", "Extra Keju", 5000, 0),
            opt("telur", "g-extra", "Extra Telur", 3000, 1),
            opt("habis", "g-extra", "Habis", 1000, 2, available = false),
        ),
    )

    @Test fun `single group toggle replaces selection`() {
        val d = OrderDraft(item, listOf(size)).toggle("g-size", "regular").toggle("g-size", "large")
        assertEquals(setOf("large"), d.selected["g-size"])
    }

    @Test fun `single group toggle on selected option keeps it selected`() {
        val d = OrderDraft(item, listOf(size)).toggle("g-size", "large").toggle("g-size", "large")
        assertEquals(setOf("large"), d.selected["g-size"])
    }

    @Test fun `multi group toggle adds then removes`() {
        val d1 = OrderDraft(item, listOf(extras)).toggle("g-extra", "keju").toggle("g-extra", "telur")
        assertEquals(setOf("keju", "telur"), d1.selected["g-extra"])
        val d2 = d1.toggle("g-extra", "keju")
        assertEquals(setOf("telur"), d2.selected["g-extra"])
    }

    @Test fun `unavailable option toggle is ignored`() {
        val d = OrderDraft(item, listOf(extras)).toggle("g-extra", "habis")
        assertTrue(d.selected["g-extra"].orEmpty().isEmpty())
    }

    @Test fun `canSubmit false while a required group is empty and true once selected`() {
        val d = OrderDraft(item, listOf(size, extras))
        assertFalse(d.canSubmit)
        assertEquals(listOf(size), d.missingRequiredGroups)
        assertTrue(d.toggle("g-size", "regular").canSubmit)
    }

    @Test fun `required group with no available options is not enforced`() {
        val dead = size.copy(options = size.options.map { it.copy(isAvailable = false) })
        val d = OrderDraft(item, listOf(dead))
        assertTrue(d.canSubmit)
        assertTrue(d.missingRequiredGroups.isEmpty())
    }

    @Test fun `item with no groups can submit immediately`() {
        assertTrue(OrderDraft(item, emptyList()).canSubmit)
    }

    @Test fun `unitPrice adds variant and modifier deltas to base`() {
        val d = OrderDraft(item, listOf(size, extras)).toggle("g-size", "large").toggle("g-extra", "keju")
        assertEquals(40000L, d.unitPrice)
    }

    @Test fun `variantPrice is base price plus option delta`() {
        val d = OrderDraft(item, listOf(size))
        assertEquals(25000L, d.variantPrice(size.options[0]))
        assertEquals(35000L, d.variantPrice(size.options[1]))
    }

    @Test fun `lineTotal multiplies by quantity`() {
        val d = OrderDraft(item, listOf(size, extras)).toggle("g-size", "large").toggle("g-extra", "keju").withQuantity(2)
        assertEquals(80000L, d.lineTotal)
    }

    @Test fun `withQuantity coerces to at least 1`() {
        assertEquals(1, OrderDraft(item, emptyList()).withQuantity(0).quantity)
        assertEquals(1, OrderDraft(item, emptyList()).withQuantity(-5).quantity)
    }

    @Test fun `toCartSelections sorted by group then option sortOrder`() {
        val d = OrderDraft(item, listOf(extras, size)).toggle("g-extra", "telur").toggle("g-extra", "keju").toggle("g-size", "large")
        assertEquals(listOf("large", "keju", "telur"), d.toCartSelections().map { it.optionId })
    }

    @Test fun `fromLine drops options missing from current groups`() {
        val line = CartLine(
            "l1", "m1", "Nasi Goreng", 25000,
            listOf(
                SelectedOption("large", "g-size", "Large", 10000, OptionKind.VARIANT),
                SelectedOption("gone", "g-extra", "Gone", 1000, OptionKind.MODIFIER),
                SelectedOption("habis", "g-extra", "Habis", 1000, OptionKind.MODIFIER),
            ),
            3,
        )
        val d = OrderDraft.fromLine(item, listOf(size, extras), line)
        assertEquals(setOf("large"), d.selected["g-size"])
        assertTrue(d.selected["g-extra"].orEmpty().isEmpty())
        assertEquals(3, d.quantity)
    }

    @Test fun `fromLine prices from the line snapshot base, not the current item price`() {
        val line = CartLine(
            "l1", "m1", "Nasi Goreng", 25000,
            listOf(SelectedOption("large", "g-size", "Large", 10000, OptionKind.VARIANT)),
            2,
        )
        val d = OrderDraft.fromLine(item.copy(price = 30000.0), listOf(size), line)
        assertEquals(25000L, d.basePrice)
        assertEquals(35000L, d.unitPrice)
        assertEquals(70000L, d.lineTotal)
        assertEquals(25000L, d.variantPrice(size.options[0]))
        assertEquals(35000L, d.variantPrice(size.options[1]))
    }

    @Test fun `fromLine keeps only the first option by sortOrder in a single group`() {
        val line = CartLine(
            "l1", "m1", "Nasi Goreng", 25000,
            listOf(
                SelectedOption("large", "g-size", "Large", 10000, OptionKind.VARIANT),
                SelectedOption("regular", "g-size", "Regular", 0, OptionKind.VARIANT),
                SelectedOption("telur", "g-extra", "Extra Telur", 3000, OptionKind.MODIFIER),
                SelectedOption("keju", "g-extra", "Extra Keju", 5000, OptionKind.MODIFIER),
            ),
            1,
        )
        val d = OrderDraft.fromLine(item, listOf(size, extras), line)
        assertEquals(setOf("regular"), d.selected["g-size"])
        assertEquals(setOf("keju", "telur"), d.selected["g-extra"])
    }

    @Test fun `new draft base defaults to the rounded item price`() {
        assertEquals(25000L, OrderDraft(item, emptyList()).basePrice)
    }

    @Test fun `price 12500_5 rounds to 12501 base`() {
        val d = OrderDraft(item.copy(price = 12500.5), emptyList())
        assertEquals(12501L, d.unitPrice)
    }
}
