package com.storepos.app.data

import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round

@Serializable
data class StoreRepackResult(
    val id: String,
    val status: String,
    @SerialName("source_balance") val sourceBalance: Double,
    @SerialName("target_balance") val targetBalance: Double
)

object StoreRepackRepository {
    private fun safeQuantity(value: Double): Boolean =
        value.isFinite() && value > 0.0 && value <= 100000.0 &&
            abs(value * 1000.0 - round(value * 1000.0)) < 0.00000001

    /**
     * A server-side PostgreSQL transaction either commits both product stocks,
     * inventory movements, and audit, or commits nothing. Requires migration.
     */
    suspend fun repack(
        shopId: String,
        sourceProductId: String,
        targetProductId: String,
        sourceQuantity: Double,
        targetQuantity: Double,
        requestId: String
    ): StoreRepackResult {
        require(sourceProductId != targetProductId) { "Source and target must be different SKUs." }
        require(safeQuantity(sourceQuantity) && safeQuantity(targetQuantity)) {
            "Enter a positive quantity with up to three decimal places."
        }
        listOf(shopId, sourceProductId, targetProductId, requestId).forEach {
            UUID.fromString(it)
        }
        return SupabaseProvider.client.postgrest.rpc(
            function = "storepos_repack_inventory",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_source_product_id", sourceProductId)
                put("p_target_product_id", targetProductId)
                put("p_source_quantity", sourceQuantity)
                put("p_target_quantity", targetQuantity)
                put("p_request_id", requestId)
            }
        ).decodeAs()
    }
}
