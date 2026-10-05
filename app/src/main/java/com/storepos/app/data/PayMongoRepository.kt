package com.storepos.app.data

import com.storepos.app.data.model.PayMongoCheckoutSession
import com.storepos.app.data.model.PayMongoCheckoutStart
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
        description: String
    ): PayMongoCheckoutStart =
        client.functions.invoke(
            function = "storepos-paymongo-checkout",
            body = buildJsonObject {
                put("shop_id", shopId)
                put("amount", amount)
                put("request_id", requestId)
                put("description", description)
            }
        ).body()

    suspend fun checkoutSession(id: String): PayMongoCheckoutSession? =
        client.from("paymongo_checkout_sessions").select {
            filter { eq("id", id) }
        }.decodeList<PayMongoCheckoutSession>().firstOrNull()
}
