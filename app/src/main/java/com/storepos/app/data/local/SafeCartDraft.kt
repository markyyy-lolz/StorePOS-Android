package com.storepos.app.data.local

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.Product
import kotlinx.serialization.Serializable

/** Unpaid shopping cart ONLY. Never a completed sale, tender or receipt. */
@Serializable
data class SafeCartDraft(
    val shopId: String,
    val cashierId: String,
    val catalogEpoch: String,
    val savedAt: Long,
    val items: List<SafeCartDraftItem>
)

@Serializable
data class SafeCartDraftItem(
    val productId: String,
    val quantity: Double
)

/**
 * Restoring after a crash never trusts cached prices, discounts, manager PINs,
 * payments, coupons or stock. Checkout performs normal validation again.
 */
object CartDraftRecovery {
    const val MAX_AGE_MS = 72L * 60L * 60L * 1000L

    fun snapshot(
        shopId: String,
        cashierId: String,
        epoch: String,
        cart: List<CartLine>,
        now: Long
    ): SafeCartDraft {
        require(shopId.isNotBlank() && cashierId.isNotBlank() && epoch.isNotBlank())
        require(cart.size in 1..200)
        val items = cart.map {
            require(it.product.shopId == shopId)
            require(it.quantity.isFinite() && it.quantity > 0.0 && it.quantity <= 100000.0)
            // Never recover manual prices/serialized unit selections without a new validation.
            SafeCartDraftItem(it.product.id, it.quantity)
        }
        require(items.map { it.productId }.distinct().size == items.size)
        return SafeCartDraft(shopId, cashierId, epoch, now, items)
    }

    fun eligible(draft: SafeCartDraft, shopId: String, cashierId: String, epoch: String, now: Long): Boolean =
        draft.shopId == shopId && draft.cashierId == cashierId &&
            draft.catalogEpoch == epoch && epoch.isNotBlank() &&
            now >= draft.savedAt && now - draft.savedAt <= MAX_AGE_MS &&
            draft.items.isNotEmpty() && draft.items.size <= 200 &&
            draft.items.all { it.quantity.isFinite() && it.quantity > 0.0 && it.quantity <= 100000.0 }

    fun restore(draft: SafeCartDraft, products: List<Product>): List<CartLine> {
        val byId = products.filter { it.isActive && it.shopId == draft.shopId }.associateBy { it.id }
        // Items missing from the current verified catalog are omitted, NEVER synthesized.
        return draft.items.mapNotNull { line ->
            byId[line.productId]?.let { product ->
                // Prices always come from CURRENT product catalog.
                CartLine(product = product, quantity = line.quantity)
            }
        }
    }
}
