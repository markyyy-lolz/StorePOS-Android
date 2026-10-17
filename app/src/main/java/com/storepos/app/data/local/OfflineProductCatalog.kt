package com.storepos.app.data.local

import com.storepos.app.data.model.PendingOfflineSale
import com.storepos.app.data.model.Product

/**
 * A cloud snapshot does not yet include sales waiting in this device's offline queue.
 * Overlay only this shop's pending quantities, avoiding a temporary stock increase
 * during reconnection. Never apply this to a cached snapshot: offline checkouts
 * already deduct those quantities before saving to SQLite.
 */
internal fun productsWithPendingOfflineSales(
    cloudProducts: List<Product>,
    pending: List<PendingOfflineSale>,
    shopId: String
): List<Product> {
    val unsynced = mutableMapOf<String, Double>()
    pending.asSequence()
        .filter { it.payload.shopId == shopId }
        .flatMap { it.payload.items.asSequence() }
        .filter { it.quantity > 0.0 }
        .forEach { line ->
            unsynced[line.productId] = (unsynced[line.productId] ?: 0.0) + line.quantity
        }
    return cloudProducts.map { product ->
        val sold = if (product.shopId == shopId && product.trackStock) {
            unsynced[product.id] ?: 0.0
        } else 0.0
        if (sold == 0.0) product
        else product.copy(stockQuantity = (product.stockQuantity - sold).coerceAtLeast(0.0))
    }
}
