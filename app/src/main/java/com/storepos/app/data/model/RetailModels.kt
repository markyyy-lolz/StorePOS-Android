package com.storepos.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class RetailBatch(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("batch_number") val batchNumber: String,
    @SerialName("expires_on") val expiresOn: String? = null,
    val quantity: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RetailSerial(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("serial_number") val serialNumber: String,
    val status: String = "available",
    @SerialName("sale_id") val saleId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RetailPriceHistory(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("old_price") val oldPrice: Double? = null,
    @SerialName("new_price") val newPrice: Double,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class RetailPriceSchedule(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("product_id") val productId: String,
    val price: Double,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("applied_at") val appliedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RetailPromo(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val name: String,
    val kind: String,
    val items: JsonElement,
    @SerialName("discount_percent") val discountPercent: Double = 0.0,
    @SerialName("buy_qty") val buyQty: Double = 1.0,
    @SerialName("free_qty") val freeQty: Double = 1.0,
    @SerialName("bundle_price") val bundlePrice: Double = 0.0,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class RetailOrder(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    val items: JsonElement,
    @SerialName("quoted_total") val quotedTotal: Double = 0.0,
    val deposit: Double = 0.0,
    @SerialName("deposit_method") val depositMethod: String = "cash",
    @SerialName("due_at") val dueAt: String? = null,
    val status: String = "reserved",
    @SerialName("sale_id") val saleId: String? = null,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class RetailSupplierReturn(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    @SerialName("credit_amount") val creditAmount: Double = 0.0,
    val reason: String,
    val status: String = "pending",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class RetailChecklist(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("user_id") val userId: String,
    val day: String,
    val kind: String,
    val checks: JsonElement,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class RetailFavorite(
    @SerialName("shop_id") val shopId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
    val position: Int = 0
)

@Serializable
data class RetailSaleDetail(
    @SerialName("sale_id") val saleId: String,
    @SerialName("shop_id") val shopId: String,
    val items: JsonElement,
    val charges: JsonElement,
    @SerialName("batch_allocations") val batchAllocations: JsonElement,
    @SerialName("receipt_token") val receiptToken: String,
    @SerialName("exchange_sale_id") val exchangeSaleId: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class RetailQuoteLine(
    @SerialName("product_id") val productId: String,
    @SerialName("base_product_id") val baseProductId: String,
    val name: String,
    val unit: String = "pc",
    val quantity: Double,
    @SerialName("base_quantity") val baseQuantity: Double,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("line_total") val lineTotal: Double,
    @SerialName("unit_cost") val unitCost: Double = 0.0,
    val serials: List<String> = emptyList()
)

@Serializable
data class RetailQuote(
    val items: List<RetailQuoteLine> = emptyList(),
    val subtotal: Double = 0.0,
    @SerialName("charges_total") val chargesTotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0
)

@Serializable
data class RetailCheckoutResult(
    val sale: Sale,
    @SerialName("receipt_token") val receiptToken: String? = null,
    @SerialName("exchange_refund_due") val exchangeRefundDue: Double = 0.0
)

@Serializable
data class RetailReorderInsight(
    @SerialName("product_id") val productId: String,
    val name: String,
    val sku: String,
    @SerialName("stock_quantity") val stockQuantity: Double,
    @SerialName("reorder_level") val reorderLevel: Double,
    @SerialName("suggested_quantity") val suggestedQuantity: Double,
    val unit: String = "pc"
)

@Serializable
data class RetailMoverInsight(
    @SerialName("product_id") val productId: String,
    val name: String,
    val sku: String,
    val quantity: Double = 0.0,
    val revenue: Double = 0.0,
    @SerialName("last_sold_at") val lastSoldAt: String? = null
)

@Serializable
data class RetailDeadStockInsight(
    @SerialName("product_id") val productId: String,
    val name: String,
    val sku: String,
    @SerialName("stock_quantity") val stockQuantity: Double,
    val unit: String = "pc",
    @SerialName("last_sold_at") val lastSoldAt: String? = null
)

@Serializable
data class RetailInsights(
    val days: Int = 30,
    val reorder: List<RetailReorderInsight> = emptyList(),
    @SerialName("fast_movers") val fastMovers: List<RetailMoverInsight> = emptyList(),
    @SerialName("dead_stock") val deadStock: List<RetailDeadStockInsight> = emptyList()
)

data class RetailCharge(
    val name: String,
    val amount: Double
)

data class RetailPromoItem(
    val productId: String,
    val quantity: Double = 1.0
)

data class RetailImportRow(
    val sku: String,
    val name: String,
    val barcode: String? = null,
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val unit: String = "pc",
    val reorderLevel: Double = 5.0,
    val openingStock: Double = 0.0
)

@Serializable
data class RetailActionResult(
    val ok: Boolean = true
)

@Serializable
data class RetailReceiptPayload(
    val data: JsonObject = JsonObject(emptyMap())
)
