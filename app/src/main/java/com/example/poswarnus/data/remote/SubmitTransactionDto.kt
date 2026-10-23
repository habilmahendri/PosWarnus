package com.example.poswarnus.data.remote

import com.example.poswarnus.data.sync.PendingSale
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Payload of the `submit_transaction` RPC (spec 5.1). `is_training` is never sent: training sales are not queued. */
@Serializable
data class SubmitTransactionDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("cashier_id") val cashierId: String,
    val subtotal: Long,
    @SerialName("tax_label") val taxLabel: String,
    @SerialName("tax_basis_points") val taxBasisPoints: Int,
    @SerialName("tax_amount") val taxAmount: Long,
    @SerialName("grand_total") val grandTotal: Long,
    @SerialName("cash_received") val cashReceived: Long,
    @SerialName("change_amount") val changeAmount: Long,
    val lines: List<SubmitLineDto>,
)

@Serializable
data class SubmitLineDto(
    val id: String,
    @SerialName("menu_item_id") val menuItemId: String,
    val name: String,
    @SerialName("base_price") val basePrice: Long,
    val quantity: Int,
    val selections: JsonElement,
)

fun PendingSale.toSubmitDto(): SubmitTransactionDto = SubmitTransactionDto(
    id = header.id,
    createdAt = isoUtc(header.createdAt),
    cashierId = header.cashierId,
    subtotal = header.subtotal,
    taxLabel = header.taxLabel,
    taxBasisPoints = header.taxBasisPoints,
    taxAmount = header.taxAmount,
    grandTotal = header.grandTotal,
    cashReceived = header.cashReceived,
    changeAmount = header.changeAmount,
    lines = lines.map {
        SubmitLineDto(
            id = it.id,
            menuItemId = it.menuItemId,
            name = it.name,
            basePrice = it.basePrice,
            quantity = it.quantity,
            selections = Json.parseToJsonElement(it.selectionsJson),
        )
    },
)

// minSdk 24 without desugaring: no java.time. SimpleDateFormat is not thread-safe, so one per call.
private fun isoUtc(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(epochMillis)
