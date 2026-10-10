package com.storepos.app.data.local

import com.storepos.app.data.model.OfflineSalePayload
import com.storepos.app.data.model.Product
import kotlin.math.abs

/**
 * Fail-closed reconciliation before replaying a local sale into the shared
 * cloud register. Unknown products, catalog resets, older unknown prices or
 * oversold stock need a manager to verify the physical cash receipt.
 */
object OfflineSyncPolicy {
    const val REVIEW_PREFIX = "NEEDS_REVIEW: "

    /**
     * Validate immutable cash outbox format locally. Never reject a retry
     * against CURRENT product stock before checking the server idempotency
     * ledger: the online request may already have committed before timeout.
     * Price, catalog-epoch and quantity locks are enforced by the cloud RPC.
     */
    fun localReviewReason(shopId: String, payload: OfflineSalePayload): String? {
        if (shopId != payload.shopId) return "Offline sale belongs to another shop."
        if (payload.catalogEpoch.isNullOrBlank())
            return "Legacy/pre-reset offline sale is missing verified catalog epoch."
        if (payload.cashierId.isNullOrBlank())
            return "Offline sale has no original cashier identity."
        if (payload.items.isEmpty() || payload.items.any {
            it.quantity <= 0 || !it.quantity.isFinite() ||
                it.unitPrice == null || it.unitPrice < 0 || !it.unitPrice.isFinite()
        }) return "Offline sale lines have invalid or missing price/quantity snapshots."
        if (payload.payments.isEmpty() || payload.payments.any {
            it.method != "cash" || it.amount <= 0 || !it.amount.isFinite()
        }) return "Offline replay only accepts valid cash payments."
        return null
    }

    fun reviewReason(shopId: String, payload: OfflineSalePayload, products: List<Product>): String? {
        if (shopId != payload.shopId) return "Sale belongs to a different shop."
        if (payload.items.isEmpty()) return "Offline sale is missing product lines."
        if (payload.payments.isEmpty() || payload.payments.any { it.method != "cash" }) {
            return "Offline synchronization accepts cash payments only."
        }
        if (payload.items.any { it.quantity <= 0 || !it.quantity.isFinite() }) {
            return "Offline sale has an invalid quantity."
        }
        val catalog = products.associateBy { it.id }
        for (line in payload.items) {
            val product = catalog[line.productId]
                ?: return "Product was removed or reset in the cloud: " + line.productId
            if (product.shopId != shopId || !product.isActive) {
                return "Product is no longer active in this shop: " + product.sku
            }
            val soldPrice = line.unitPrice ?: return "Legacy sale has no validated unit-price snapshot."
            if (!soldPrice.isFinite() || abs(product.sellingPrice - soldPrice) > 0.009) {
                return "Product price changed since offline checkout: " + product.sku
            }
        }
        for ((id, quantity) in payload.items.groupBy { it.productId }
            .mapValues { (_, lines) -> lines.sumOf { it.quantity } }) {
            val product = catalog[id] ?: continue
            if (product.trackStock && product.stockQuantity + 0.0001 < quantity) {
                return "Insufficient cloud stock to reconcile: " + product.sku
            }
        }
        return null
    }

    fun isCashOnly(payload: OfflineSalePayload): Boolean =
        payload.payments.isNotEmpty() && payload.payments.all { it.method == "cash" }
}
