package com.example.poswarnus.data.remote

import com.example.poswarnus.data.sync.PendingSale
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** Uploads one sale through the idempotent `submit_transaction` RPC. Server errors surface as exceptions. */
class SupabaseTransactionRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionAvailable: () -> Boolean,
) : TransactionRemoteDataSource {
    override suspend fun submit(sale: PendingSale) {
        if (!sessionAvailable()) throw NoSessionException()
        val payload = Json.encodeToJsonElement(sale.toSubmitDto())
        client.postgrest.rpc("submit_transaction", buildJsonObject { put("p", payload) })
    }
}
