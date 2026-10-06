package com.storepos.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Shop(
    val id: String,
    val name: String,
    @SerialName("legal_name") val legalName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val tin: String? = null,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    val timezone: String = "Asia/Manila",
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("booking_code") val bookingCode: String? = null,
    @SerialName("app_code") val appCode: String = "storepos",
    @SerialName("business_type") val businessType: String = "retail"
)

@Serializable
data class ShopMember(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class ProductCategory(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val name: String,
    val description: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class Product(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("category_id") val categoryId: String? = null,
    val sku: String,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val description: String? = null,
    @SerialName("part_number") val partNumber: String? = null,
    @SerialName("item_type") val itemType: String = "product",
    @SerialName("cost_price") val costPrice: Double = 0.0,
    @SerialName("selling_price") val sellingPrice: Double = 0.0,
    @SerialName("wholesale_price") val wholesalePrice: Double? = null,
    @SerialName("retail_parent_id") val retailParentId: String? = null,
    @SerialName("retail_multiplier") val retailMultiplier: Double = 1.0,
    @SerialName("wholesale_min") val wholesaleMin: Double = 0.0,
    @SerialName("variant_group") val variantGroup: String? = null,
    @SerialName("variant_name") val variantName: String? = null,
    @SerialName("is_weighed") val isWeighed: Boolean = false,
    @SerialName("batch_tracked") val batchTracked: Boolean = false,
    @SerialName("serial_tracked") val serialTracked: Boolean = false,
    @SerialName("stock_quantity") val stockQuantity: Double = 0.0,
    @SerialName("reorder_level") val reorderLevel: Double = 5.0,
    @SerialName("track_stock") val trackStock: Boolean = true,
    val unit: String = "pc",
    @SerialName("shelf_location") val shelfLocation: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val oem: Boolean = false,
    @SerialName("warranty_days") val warrantyDays: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class Customer(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("loyalty_points") val loyaltyPoints: Int = 0,
    @SerialName("credit_limit") val creditLimit: Double = 0.0,
    @SerialName("store_credit_balance") val storeCreditBalance: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class Motorcycle(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("motorcycle_model_id") val motorcycleModelId: String? = null,
    val make: String,
    val model: String,
    val variant: String? = null,
    @SerialName("model_year") val modelYear: Int? = null,
    @SerialName("plate_number") val plateNumber: String? = null,
    @SerialName("engine_number") val engineNumber: String? = null,
    @SerialName("chassis_number") val chassisNumber: String? = null,
    val color: String? = null,
    @SerialName("odometer_km") val odometerKm: Double? = null,
    val notes: String? = null
)

@Serializable
data class ServiceItem(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val name: String,
    val category: String? = null,
    val description: String? = null,
    @SerialName("base_price") val basePrice: Double = 0.0,
    @SerialName("default_duration_minutes") val defaultDurationMinutes: Int? = null,
    @SerialName("default_commission") val defaultCommission: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class JobOrder(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("job_number") val jobNumber: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("motorcycle_id") val motorcycleId: String,
    @SerialName("assigned_mechanic_id") val assignedMechanicId: String? = null,
    val complaint: String? = null,
    val diagnosis: String? = null,
    @SerialName("technician_notes") val technicianNotes: String? = null,
    @SerialName("odometer_in") val odometerIn: Double? = null,
    @SerialName("odometer_out") val odometerOut: Double? = null,
    val status: String = "waiting",
    val priority: String = "normal",
    @SerialName("estimated_completion") val estimatedCompletion: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class JobOrderPart(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("job_order_id") val jobOrderId: String,
    @SerialName("product_id") val productId: String? = null,
    val description: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double,
    val source: String = "inventory"
)

@Serializable
data class Supplier(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val name: String,
    @SerialName("contact_person") val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class PurchaseOrder(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("po_number") val poNumber: String,
    @SerialName("supplier_id") val supplierId: String,
    val status: String,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("expected_at") val expectedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Sale(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("sale_number") val saleNumber: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    @SerialName("job_order_id") val jobOrderId: String? = null,
    @SerialName("cashier_id") val cashierId: String,
    val subtotal: Double,
    @SerialName("discount_amount") val discountAmount: Double,
    @SerialName("tax_amount") val taxAmount: Double,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("amount_tendered") val amountTendered: Double? = null,
    @SerialName("change_due") val changeDue: Double? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Expense(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val category: String,
    val description: String,
    val amount: Double,
    @SerialName("expense_date") val expenseDate: String
)

@Serializable
data class SaleItem(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("item_name") val itemName: String,
    val sku: String? = null,
    val quantity: Double,
    @SerialName("unit_cost") val unitCost: Double = 0.0,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("line_total") val lineTotal: Double
)

@Serializable
data class CashierShift(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("opening_cash") val openingCash: Double = 0.0,
    @SerialName("expected_cash") val expectedCash: Double? = null,
    @SerialName("actual_cash") val actualCash: Double? = null,
    val variance: Double? = null,
    val notes: String? = null,
    val status: String = "open"
)

@Serializable
data class SaleReturn(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("return_number") val returnNumber: String,
    val reason: String,
    @SerialName("refund_method") val refundMethod: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Appointment(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val phone: String? = null,
    @SerialName("service_request") val serviceRequest: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String = "booked",
    val source: String = "staff",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AppointmentInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val phone: String? = null,
    @SerialName("service_request") val serviceRequest: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String = "booked",
    val source: String = "staff",
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String? = null
)

@Serializable
data class TechnicianTimeEntry(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("job_order_id") val jobOrderId: String,
    @SerialName("technician_id") val technicianId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("minutes_worked") val minutesWorked: Int? = null,
    val notes: String? = null
)

@Serializable
data class PurchaseOrderItem(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("quantity_ordered") val quantityOrdered: Double,
    @SerialName("quantity_received") val quantityReceived: Double = 0.0,
    @SerialName("unit_cost") val unitCost: Double,
    @SerialName("line_total") val lineTotal: Double
)

@Serializable
data class Warranty(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    @SerialName("warranty_type") val warrantyType: String,
    val description: String,
    @SerialName("starts_on") val startsOn: String,
    @SerialName("expires_on") val expiresOn: String? = null,
    val status: String = "active",
    @SerialName("claim_notes") val claimNotes: String? = null
)

@Serializable
data class WarrantyClaim(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("warranty_id") val warrantyId: String,
    @SerialName("claim_number") val claimNumber: String,
    val issue: String,
    val resolution: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class WarrantyClaimInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("warranty_id") val warrantyId: String,
    val issue: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
data class Quotation(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("quote_number") val quoteNumber: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    val status: String = "draft",
    val subtotal: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("valid_until") val validUntil: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class QuotationItem(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("quotation_id") val quotationId: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("product_id") val productId: String? = null,
    val description: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("line_total") val lineTotal: Double
)

@Serializable
data class AnalyticsTopProduct(
    val name: String,
    val qty: Double = 0.0,
    val sales: Double = 0.0
)

@Serializable
data class AnalyticsSummary(
    val revenue: Double = 0.0,
    @SerialName("gross_profit") val grossProfit: Double = 0.0,
    val transactions: Int = 0,
    @SerialName("avg_ticket") val avgTicket: Double = 0.0,
    val expenses: Double = 0.0,
    @SerialName("top_products") val topProducts: List<AnalyticsTopProduct> = emptyList()
)

@Serializable
data class AppVersion(
    @SerialName("app_code") val appCode: String = "",
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    val title: String? = null,
    @SerialName("release_notes") val releaseNotes: List<String> = emptyList(),
    @SerialName("github_release_url") val githubReleaseUrl: String? = null,
    @SerialName("apk_url") val apkUrl: String? = null,
    val mandatory: Boolean = false,
    @SerialName("minimum_supported_code") val minimumSupportedCode: Int = 1
)

@Serializable data class ShopInsert(
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("created_by") val createdBy: String
)

@Serializable data class ShopMemberInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "owner"
)

@Serializable data class ShopSettingsInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("receipt_header") val receiptHeader: String? = null,
    @SerialName("receipt_footer") val receiptFooter: String? = "Thank you for choosing us."
)

@Serializable data class ProductInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("category_id") val categoryId: String? = null,
    val sku: String,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    @SerialName("part_number") val partNumber: String? = null,
    @SerialName("item_type") val itemType: String = "part",
    @SerialName("cost_price") val costPrice: Double,
    @SerialName("selling_price") val sellingPrice: Double,
    @SerialName("wholesale_price") val wholesalePrice: Double? = null,
    @SerialName("retail_parent_id") val retailParentId: String? = null,
    @SerialName("retail_multiplier") val retailMultiplier: Double = 1.0,
    @SerialName("wholesale_min") val wholesaleMin: Double = 0.0,
    @SerialName("variant_group") val variantGroup: String? = null,
    @SerialName("variant_name") val variantName: String? = null,
    @SerialName("is_weighed") val isWeighed: Boolean = false,
    @SerialName("batch_tracked") val batchTracked: Boolean = false,
    @SerialName("serial_tracked") val serialTracked: Boolean = false,
    @SerialName("stock_quantity") val stockQuantity: Double,
    @SerialName("reorder_level") val reorderLevel: Double = 5.0,
    val unit: String = "pc"
)

@Serializable data class CustomerInsert(
    @SerialName("shop_id") val shopId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String
)

@Serializable data class MotorcycleInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    val make: String,
    val model: String,
    val variant: String? = null,
    @SerialName("model_year") val modelYear: Int? = null,
    @SerialName("plate_number") val plateNumber: String? = null,
    val color: String? = null,
    @SerialName("odometer_km") val odometerKm: Double? = null
)

@Serializable data class SaleInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    @SerialName("job_order_id") val jobOrderId: String? = null,
    @SerialName("cashier_id") val cashierId: String,
    val subtotal: Double,
    @SerialName("discount_amount") val discountAmount: Double,
    @SerialName("tax_amount") val taxAmount: Double,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("amount_tendered") val amountTendered: Double? = null,
    @SerialName("change_due") val changeDue: Double? = null,
    val status: String = "open",
    val notes: String? = null
)

@Serializable data class SaleItemInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("product_id") val productId: String?,
    @SerialName("item_name") val itemName: String,
    val sku: String?,
    val quantity: Double,
    @SerialName("unit_cost") val unitCost: Double,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    @SerialName("line_total") val lineTotal: Double
)

@Serializable data class PaymentInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("sale_id") val saleId: String,
    val method: String,
    val amount: Double,
    val status: String = "paid",
    @SerialName("reference_number") val referenceNumber: String? = null,
    @SerialName("received_by") val receivedBy: String,
    @SerialName("paid_at") val paidAt: String? = null
)

@Serializable data class JobOrderInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("motorcycle_id") val motorcycleId: String,
    @SerialName("assigned_mechanic_id") val assignedMechanicId: String? = null,
    val complaint: String? = null,
    @SerialName("odometer_in") val odometerIn: Double? = null,
    val status: String = "waiting",
    val priority: String = "normal",
    @SerialName("created_by") val createdBy: String
)

@Serializable data class SupplierInsert(
    @SerialName("shop_id") val shopId: String,
    val name: String,
    @SerialName("contact_person") val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null
)

@Serializable data class ExpenseInsert(
    @SerialName("shop_id") val shopId: String,
    val category: String,
    val description: String,
    val amount: Double,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("created_by") val createdBy: String
)

@Serializable
data class SupportThread(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val subject: String,
    val status: String = "open",
    val priority: String = "normal",
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("ai_enabled") val aiEnabled: Boolean = true,
    @SerialName("ai_handoff") val aiHandoff: Boolean = false,
    @SerialName("ai_last_reply_at") val aiLastReplyAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SupportMessage(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_type") val senderType: String,
    val body: String,
    @SerialName("ai_model") val aiModel: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SupportThreadInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("created_by") val createdBy: String,
    val subject: String,
    val priority: String = "normal",
    val status: String = "open"
)

@Serializable
data class SupportMessageInsert(
    @SerialName("thread_id") val threadId: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_type") val senderType: String,
    val body: String
)

@Serializable
data class LicenseAccess(
    val valid: Boolean = false,
    @SerialName("plan_code") val planCode: String? = null,
    val status: String = "unknown",
    val trial: Boolean = false,
    @SerialName("requires_activation") val requiresActivation: Boolean = false,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("max_devices") val maxDevices: Int? = null,
    @SerialName("max_staff") val maxStaff: Int? = null,
    @SerialName("offline_grace_days") val offlineGraceDays: Int? = null,
    @SerialName("days_remaining") val daysRemaining: Int? = null,
    val message: String? = null
)

@Serializable
data class PlanEntitlements(
    val valid: Boolean = false,
    val status: String = "unknown",
    val trial: Boolean = false,
    @SerialName("app_code") val appCode: String = "motopos",
    @SerialName("business_type") val businessType: String = "motorcycle",
    @SerialName("plan_code") val planCode: String? = null,
    @SerialName("plan_name") val planName: String? = null,
    val custom: Boolean = false,
    val features: List<String> = emptyList(),
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("days_remaining") val daysRemaining: Int? = null,
    @SerialName("max_devices") val maxDevices: Int? = null,
    @SerialName("max_staff") val maxStaff: Int? = null,
    @SerialName("offline_grace_days") val offlineGraceDays: Int? = null,
    @SerialName("billing_cycle") val billingCycle: String? = null,
    @SerialName("price_snapshot_php") val priceSnapshotPhp: Double? = null,
    @SerialName("monthly_price_php") val monthlyPricePhp: Double? = null,
    @SerialName("annual_price_php") val annualPricePhp: Double? = null
)

@Serializable
data class ShopAlert(
    val code: String,
    val severity: String = "info",
    val title: String,
    val message: String,
    @SerialName("action_page") val actionPage: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

data class ShopContext(
    val userId: String,
    val shop: Shop,
    val member: ShopMember
)

data class CartLine(
    val product: Product,
    val quantity: Double = 1.0,
    val unitPriceOverride: Double? = null,
    val serials: List<String> = emptyList()
) {
    val unitPrice: Double get() = unitPriceOverride ?: product.sellingPrice
    val lineTotal: Double get() = unitPrice * quantity
    val hasPriceOverride: Boolean get() = unitPriceOverride != null && kotlin.math.abs(unitPriceOverride - product.sellingPrice) > 0.009
}


@Serializable
data class BootstrapShopParams(
    @SerialName("p_name") val name: String,
    @SerialName("p_phone") val phone: String? = null,
    @SerialName("p_address") val address: String? = null
)

@Serializable
data class SaleRpcItem(
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double? = null
)

@Serializable
data class CompleteSaleRpcParams(
    @SerialName("p_shop_id") val shopId: String,
    @SerialName("p_customer_id") val customerId: String? = null,
    @SerialName("p_motorcycle_id") val motorcycleId: String? = null,
    @SerialName("p_job_order_id") val jobOrderId: String? = null,
    @SerialName("p_discount_amount") val discountAmount: Double = 0.0,
    @SerialName("p_tax_amount") val taxAmount: Double = 0.0,
    @SerialName("p_amount_tendered") val amountTendered: Double? = null,
    @SerialName("p_payment_method") val paymentMethod: String,
    @SerialName("p_reference_number") val referenceNumber: String? = null,
    @SerialName("p_items") val items: List<SaleRpcItem>
)


@Serializable
data class OfflineSalePayload(
    @SerialName("client_key") val clientKey: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    @SerialName("job_order_id") val jobOrderId: String? = null,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    @SerialName("amount_tendered") val amountTendered: Double? = null,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("reference_number") val referenceNumber: String? = null,
    val items: List<SaleRpcItem>,
    val payments: List<CheckoutPayment> = emptyList()
)

data class PendingOfflineSale(
    val id: String,
    val payload: OfflineSalePayload,
    val createdAt: Long,
    val lastError: String? = null
)


@Serializable
data class StockTransfer(
    val id: String,
    @SerialName("from_shop_id") val fromShopId: String,
    @SerialName("to_shop_id") val toShopId: String,
    @SerialName("transfer_number") val transferNumber: String,
    val status: String,
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("shipped_at") val shippedAt: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

data class AccessibleBranch(
    val shop: Shop,
    val member: ShopMember
)


@Serializable
data class ShopSettings(
    @SerialName("shop_id") val shopId: String,
    @SerialName("receipt_header") val receiptHeader: String? = null,
    @SerialName("receipt_footer") val receiptFooter: String? = null,
    @SerialName("tax_enabled") val taxEnabled: Boolean = false,
    @SerialName("default_tax_rate") val defaultTaxRate: Double = 0.0,
    @SerialName("low_stock_default") val lowStockDefault: Double = 5.0,
    @SerialName("allow_negative_stock") val allowNegativeStock: Boolean = false,
    @SerialName("printer_paper_width_mm") val printerPaperWidthMm: Int = 80,
    @SerialName("auto_print_receipt") val autoPrintReceipt: Boolean = true,
    @SerialName("require_cashier_shift") val requireCashierShift: Boolean = true,
    @SerialName("cashier_discount_limit_percent") val cashierDiscountLimitPercent: Double = 5.0,
    @SerialName("manager_pin_for_discount") val managerPinForDiscount: Boolean = true,
    @SerialName("allow_hold_sales") val allowHoldSales: Boolean = true,
    @SerialName("cash_drawer_enabled") val cashDrawerEnabled: Boolean = false,
    @SerialName("receipt_show_cashier") val receiptShowCashier: Boolean = true,
    @SerialName("receipt_title") val receiptTitle: String = "SALES RECEIPT",
    @SerialName("receipt_show_logo") val receiptShowLogo: Boolean = false,
    @SerialName("receipt_show_address") val receiptShowAddress: Boolean = true,
    @SerialName("receipt_show_phone") val receiptShowPhone: Boolean = true,
    @SerialName("receipt_show_tin") val receiptShowTin: Boolean = true,
    @SerialName("receipt_show_receipt_number") val receiptShowReceiptNumber: Boolean = true,
    @SerialName("receipt_show_date") val receiptShowDate: Boolean = true,
    @SerialName("receipt_show_payment_reference") val receiptShowPaymentReference: Boolean = true,
    @SerialName("receipt_show_digital_qr") val receiptShowDigitalQr: Boolean = true,
    @SerialName("receipt_compact_mode") val receiptCompactMode: Boolean = false,
    @SerialName("receipt_section_order") val receiptSectionOrder: List<String> = listOf(
        "store", "meta", "items", "totals", "payment", "digital", "footer"
    )
)

@Serializable
data class CheckoutPayment(
    val method: String,
    val amount: Double,
    val tendered: Double? = null,
    @SerialName("reference_number") val referenceNumber: String? = null
)

@Serializable
data class HeldSaleItem(
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    @SerialName("unit_price_override") val unitPriceOverride: Double? = null
)

@Serializable
data class HeldSale(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("held_by") val heldBy: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    val label: String? = null,
    val notes: String? = null,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    val items: List<HeldSaleItem> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class HeldSaleInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("held_by") val heldBy: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("motorcycle_id") val motorcycleId: String? = null,
    val label: String? = null,
    val notes: String? = null,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    val items: List<HeldSaleItem>
)

@Serializable
data class CashMovement(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("movement_type") val movementType: String,
    val amount: Double,
    val reason: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ZReport(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("opening_cash") val openingCash: Double = 0.0,
    @SerialName("cash_sales") val cashSales: Double = 0.0,
    @SerialName("noncash_sales") val noncashSales: Double = 0.0,
    @SerialName("cash_in") val cashIn: Double = 0.0,
    @SerialName("cash_out") val cashOut: Double = 0.0,
    @SerialName("cash_refunds") val cashRefunds: Double = 0.0,
    @SerialName("gross_sales") val grossSales: Double = 0.0,
    val discounts: Double = 0.0,
    val tax: Double = 0.0,
    @SerialName("transaction_count") val transactionCount: Int = 0,
    @SerialName("expected_cash") val expectedCash: Double = 0.0,
    @SerialName("actual_cash") val actualCash: Double = 0.0,
    val variance: Double = 0.0,
    @SerialName("generated_at") val generatedAt: String
)

@Serializable
data class CustomerReceivable(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("sale_id") val saleId: String,
    @SerialName("original_amount") val originalAmount: Double,
    val balance: Double,
    @SerialName("due_date") val dueDate: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String
)


@Serializable
data class ReturnableSaleItem(
    @SerialName("sale_item_id") val saleItemId: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("item_name") val itemName: String,
    val sku: String? = null,
    @SerialName("sold_quantity") val soldQuantity: Double,
    @SerialName("returned_quantity") val returnedQuantity: Double = 0.0,
    @SerialName("remaining_quantity") val remainingQuantity: Double,
    @SerialName("unit_refund") val unitRefund: Double
)


@Serializable
data class InventoryCount(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("count_number") val countNumber: String,
    val status: String,
    @SerialName("started_by") val startedBy: String,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("approved_at") val approvedAt: String? = null
)

@Serializable
data class InventoryCountItem(
    val id: String,
    @SerialName("count_id") val countId: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("system_quantity") val systemQuantity: Double,
    @SerialName("counted_quantity") val countedQuantity: Double,
    val variance: Double = 0.0
)


@Serializable
data class ServiceReminder(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("motorcycle_id") val motorcycleId: String,
    val title: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("due_odometer_km") val dueOdometerKm: Double? = null,
    val status: String = "pending",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class ServiceReminderInsert(
    @SerialName("shop_id") val shopId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("motorcycle_id") val motorcycleId: String,
    val title: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("due_odometer_km") val dueOdometerKm: Double? = null,
    val status: String = "pending",
    val notes: String? = null,
    @SerialName("created_by") val createdBy: String
)
