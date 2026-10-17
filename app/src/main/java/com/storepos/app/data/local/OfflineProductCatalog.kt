package com.storepos.app.data.local

import com.storepos.app.data.model.PendingOfflineSale
import com.storepos.app.data.model.Product

/** Local cash sales with the current trusted catalog epoch are not in cloud stock yet. */
internal fun productsWithQueuedCashSales(
    cloudProducts: List<Product>,
    unsyncedSales: List<PendingOfflineSale>,
    shopId: String,
    currentEpoch: String
): List<Product> {
    val quantities = mutableMapOf<String, Double>()
    unsyncedSales.asSequence()
        .filter { it.payload.shopId == shopId && it.payload.catalogEpoch == currentEpoch }
        .flatMap { it.payload.items.asSequence() }
        .filter { it.quantity > 0 }
        .forEach { line ->
            quantities[line.productId] = (quantities[line.productId] ?: 0.0) + line.quantity
        }
    return cloudProducts.map { product ->
        val units = if (product.shopId == shopId && product.trackStock) quantities[product.id] ?: 0.0 else 0.0
        if (units == 0.0) product
        else product.copy(stockQuantity = (product.stockQuantity - units).coerceAtLeast(0.0))
    }
}
