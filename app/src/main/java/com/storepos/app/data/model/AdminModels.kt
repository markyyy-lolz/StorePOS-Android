package com.storepos.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuditLog(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("actor_id") val actorId: String? = null,
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("old_data") val oldData: JsonObject? = null,
    @SerialName("new_data") val newData: JsonObject? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SupplierPayable(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    @SerialName("original_amount") val originalAmount: Double,
    val balance: Double,
    @SerialName("due_date") val dueDate: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class SupplierPayment(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("payable_id") val payableId: String,
    @SerialName("supplier_id") val supplierId: String,
    val amount: Double,
    val method: String,
    @SerialName("reference_number") val referenceNumber: String? = null,
    val notes: String? = null,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("paid_at") val paidAt: String
)


@Serializable
data class StoreBackup(
    @SerialName("exported_at") val exportedAt: String,
    val shop: Shop,
    val products: List<Product>,
    val customers: List<Customer>,
    val suppliers: List<Supplier>,
    val sales: List<Sale>,
    val expenses: List<Expense>
)
