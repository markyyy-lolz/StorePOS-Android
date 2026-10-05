package com.storepos.app.data

import com.storepos.app.data.model.RetailManagerApproval
import com.storepos.app.data.model.RetailPaymentReconciliation
import com.storepos.app.data.model.RetailReceiptReprint
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object RetailOpsRepository {
    private val client get() = SupabaseProvider.client

    private suspend fun action(shopId: String, action: String, data: JsonObject = JsonObject(emptyMap())): JsonElement =
        client.postgrest.rpc(
            function = "storepos_v13_action",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_action", action)
                put("p_data", data)
            }
        ).decodeAs()

    suspend fun dataHealth(shopId: String): JsonObject =
        action(shopId, "data_health").jsonObject

    suspend fun supplierPrices(shopId: String): JsonArray =
        action(shopId, "supplier_prices").jsonArray

    suspend fun xReport(shopId: String, shiftId: String? = null): JsonObject =
        action(shopId, "x_report", buildJsonObject {
            shiftId?.takeIf { it.isNotBlank() }?.let { put("shift_id", it) }
        }).jsonObject

    suspend fun reconcilePayment(
        shopId: String,
        businessDate: String,
        method: String,
        actualAmount: Double,
        reference: String?,
        notes: String?
    ): JsonObject = action(shopId, "reconcile_payment", buildJsonObject {
        put("business_date", businessDate)
        put("method", method)
        put("actual_amount", actualAmount)
        reference?.trim()?.takeIf { it.isNotBlank() }?.let { put("reference", it) }
        notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
    }).jsonObject

    suspend fun requestApproval(
        shopId: String,
        type: String,
        reason: String,
        details: String?
    ): JsonObject = action(shopId, "request_approval", buildJsonObject {
        put("approval_type", type)
        put("reason", reason.trim())
        put("request_payload", buildJsonObject {
            details?.trim()?.takeIf { it.isNotBlank() }?.let { put("details", it) }
        })
    }).jsonObject

    suspend fun reviewApproval(
        shopId: String,
        id: String,
        decision: String,
        notes: String? = null
    ): JsonObject = action(shopId, "review_approval", buildJsonObject {
        put("id", id)
        put("decision", decision)
        notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
    }).jsonObject

    suspend fun approvals(shopId: String): List<RetailManagerApproval> =
        client.from("retail_manager_approvals").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<RetailManagerApproval>().sortedByDescending { it.createdAt }

    suspend fun reconciliations(shopId: String): List<RetailPaymentReconciliation> =
        client.from("retail_payment_reconciliations").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<RetailPaymentReconciliation>().sortedByDescending { it.businessDate + it.createdAt }

    suspend fun receiptReprints(shopId: String): List<RetailReceiptReprint> =
        client.from("retail_receipt_reprints").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<RetailReceiptReprint>().sortedByDescending { it.printedAt }
}
