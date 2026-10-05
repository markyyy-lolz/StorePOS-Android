package com.storepos.app.data

import com.storepos.app.data.model.PayMongoCheckoutEnvelope
import com.storepos.app.data.model.PayMongoCheckoutSession
import com.storepos.app.data.model.PayMongoIntegration
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.call.body
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PayMongoRepository {
    private val client get() = SupabaseProvider.client

    suspend fun integration(shopId: String): PayMongoIntegration? =
        client.from("paymongo_integrations").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<PayMongoIntegration>().firstOrNull()

    suspend fun createCheckout(
        shopId: String,
        amount: Double,
        requestId: String,
        description: String,
        flow: String = "hosted",
        expirySeconds: Int = 300
    ): PayMongoCheckoutSession =
        client.functions.invoke(
            function = "storepos-paymongo-checkout",
            body = buildJsonObject {
                put("action", "create")
                put("shop_id", shopId)
                put("amount", amount)
                put("request_id", requestId)
                put("description", description)
                put("flow", flow)
                put("expiry_seconds", expirySeconds)
            }
        ).body<PayMongoCheckoutEnvelope>().session

    suspend fun syncCheckout(
        shopId: String,
        sessionId: String
    ): PayMongoCheckoutSession =
        client.functions.invoke(
            function = "storepos-paymongo-checkout",
            body = buildJsonObject {
                put("action", "status")
                put("shop_id", shopId)
                put("session_id", sessionId)
            }
        ).body<PayMongoCheckoutEnvelope>().session

    suspend fun cancelCheckout(
        shopId: String,
        sessionId: String
    ): PayMongoCheckoutSession =
        client.functions.invoke(
            function = "storepos-paymongo-checkout",
            body = buildJsonObject {
                put("action", "cancel")
                put("shop_id", shopId)
                put("session_id", sessionId)
            }
        ).body<PayMongoCheckoutEnvelope>().session
}
