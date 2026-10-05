package com.storepos.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RetailPaymentReconciliation(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("business_date") val businessDate: String,
    val method: String,
    @SerialName("expected_amount") val expectedAmount: Double = 0.0,
    @SerialName("actual_amount") val actualAmount: Double = 0.0,
    val variance: Double = 0.0,
    val status: String = "reconciled",
    val reference: String? = null,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class RetailManagerApproval(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("requested_by") val requestedBy: String,
    @SerialName("approval_type") val approvalType: String,
    @SerialName("entity_type") val entityType: String? = null,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("request_payload") val requestPayload: JsonObject = JsonObject(emptyMap()),
    val reason: String,
    val status: String = "pending",
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("review_notes") val reviewNotes: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class RetailReceiptReprint(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("printed_by") val printedBy: String,
    @SerialName("copy_no") val copyNo: Int,
    val reason: String,
    @SerialName("printed_at") val printedAt: String
)
