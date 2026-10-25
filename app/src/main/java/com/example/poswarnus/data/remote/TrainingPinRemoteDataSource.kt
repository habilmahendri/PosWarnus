package com.example.poswarnus.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface TrainingPinRemoteDataSource {
    suspend fun verifyPin(pin: String): Boolean
    suspend fun recordTransition(cashierId: String, action: TrainingAuditAction)
}

enum class TrainingAuditAction(val wireValue: String) { ENABLE("enable"), DISABLE("disable") }

/** Verifies the owner PIN via the `verify_training_pin` RPC and records on/off transitions in `training_mode_audit`. */
class SupabaseTrainingPinRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionAvailable: () -> Boolean,
) : TrainingPinRemoteDataSource {
    override suspend fun verifyPin(pin: String): Boolean {
        if (!sessionAvailable()) throw NoSessionException()
        return client.postgrest.rpc("verify_training_pin", buildJsonObject { put("p_pin", pin) })
            .decodeAs<Boolean>()
    }

    override suspend fun recordTransition(cashierId: String, action: TrainingAuditAction) {
        if (!sessionAvailable()) throw NoSessionException()
        client.postgrest.from("training_mode_audit").insert(
            buildJsonObject {
                put("cashier_id", cashierId)
                put("action", action.wireValue)
            },
        )
    }
}
