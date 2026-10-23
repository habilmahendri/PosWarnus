package com.example.poswarnus.data.remote

import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.local.TransactionEntity
import com.example.poswarnus.data.local.TransactionLineEntity
import com.example.poswarnus.data.sync.PendingSale
import com.example.poswarnus.data.transaction.SelectionsJson
import com.example.poswarnus.ui.cart.SelectedOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitTransactionDtoTest {
    private val selectionsString = SelectionsJson.encode(
        listOf(SelectedOption("o1", "g1", "Less sugar", -2_000L, OptionKind.MODIFIER)),
    )

    private val sale = PendingSale(
        header = TransactionEntity(
            id = "tx-1", createdAt = 1_000L, cashierId = "user-1", subtotal = 30_000L,
            taxLabel = "PPN", taxBasisPoints = 1100, taxAmount = 3_300L, grandTotal = 33_300L,
            cashReceived = 50_000L, changeAmount = 16_700L, isTraining = false,
        ),
        lines = listOf(
            TransactionLineEntity("tx-1:0", "tx-1", "m1", "Kopi", 15_000L, 2, selectionsString),
            TransactionLineEntity("tx-1:1", "tx-1", "m2", "Teh", 10_000L, 1, "[]"),
        ),
    )

    private val json: JsonObject = Json.encodeToJsonElement(sale.toSubmitDto()).jsonObject

    @Test
    fun `header has exactly the snake_case keys and no is_training`() {
        assertEquals(
            setOf(
                "id", "created_at", "cashier_id", "subtotal", "tax_label", "tax_basis_points",
                "tax_amount", "grand_total", "cash_received", "change_amount", "lines",
            ),
            json.keys,
        )
        assertFalse("is_training" in json.keys)
    }

    @Test
    fun `created_at is ISO-8601 UTC with milliseconds`() {
        assertEquals("1970-01-01T00:00:01.000Z", json.getValue("created_at").jsonPrimitive.content)
    }

    @Test
    fun `money and counts are JSON numbers`() {
        for (key in listOf("subtotal", "tax_basis_points", "tax_amount", "grand_total", "cash_received", "change_amount")) {
            assertFalse("$key must not be a string", json.getValue(key).jsonPrimitive.isString)
        }
        assertEquals(33_300L, json.getValue("grand_total").jsonPrimitive.content.toLong())
        assertEquals("PPN", json.getValue("tax_label").jsonPrimitive.content)
        assertEquals("user-1", json.getValue("cashier_id").jsonPrimitive.content)
    }

    @Test
    fun `lines have the expected keys and selections are JSON arrays not strings`() {
        val lines = json.getValue("lines").jsonArray
        assertEquals(2, lines.size)
        val first = lines[0].jsonObject
        assertEquals(setOf("id", "menu_item_id", "name", "base_price", "quantity", "selections"), first.keys)
        assertEquals("tx-1:0", first.getValue("id").jsonPrimitive.content)
        assertEquals("m1", first.getValue("menu_item_id").jsonPrimitive.content)
        assertFalse(first.getValue("base_price").jsonPrimitive.isString)
        assertFalse(first.getValue("quantity").jsonPrimitive.isString)
        val selections = first.getValue("selections")
        assertTrue(selections is JsonArray)
        assertEquals(Json.parseToJsonElement(selectionsString), selections)
        assertEquals(-2_000L, selections.jsonArray[0].jsonObject.getValue("priceDelta").jsonPrimitive.content.toLong())
        assertEquals(JsonArray(emptyList()), lines[1].jsonObject.getValue("selections"))
        assertEquals(JsonPrimitive("tx-1:1"), lines[1].jsonObject.getValue("id"))
    }
}
