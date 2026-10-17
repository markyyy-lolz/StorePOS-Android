package com.storepos.app.data.local

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.Product
import org.junit.Assert.*
import org.junit.Test

class CartDraftRecoveryTest {
    private val epoch = "de854a11-47c0-4e52-aaa0-af882228335f"
    private val product = Product(
        id = "p1", shopId = "storepos-shop", sku = "RICE1", name = "Rice",
        sellingPrice = 55.0, stockQuantity = 10.0
    )

    @Test fun restartRestoresUnpaidCartWithCurrentPriceOnly() {
        val draft = CartDraftRecovery.snapshot(
            "storepos-shop", "cashier1", epoch,
            listOf(CartLine(product, 2.0, unitPriceOverride = 1.0)), 1000L
        )
        assertTrue(CartDraftRecovery.eligible(draft, "storepos-shop", "cashier1", epoch, 2000L))
        val restored = CartDraftRecovery.restore(draft, listOf(product.copy(sellingPrice = 60.0)))
        assertEquals(1, restored.size)
        assertEquals(2.0, restored.single().quantity, 0.001)
        assertEquals(60.0, restored.single().unitPrice, 0.001)
        assertNull(restored.single().unitPriceOverride)
    }

    @Test fun rejectsCrossShopCrossCashierResetExpiredAndClockRollback() {
        val draft = CartDraftRecovery.snapshot(
            "storepos-shop", "cashier1", epoch,
            listOf(CartLine(product, 1.0)), 1000L
        )
        assertFalse(CartDraftRecovery.eligible(draft, "another-shop", "cashier1", epoch, 2000L))
        assertFalse(CartDraftRecovery.eligible(draft, "storepos-shop", "cashier2", epoch, 2000L))
        assertFalse(CartDraftRecovery.eligible(draft, "storepos-shop", "cashier1", "other-epoch", 2000L))
        assertFalse(CartDraftRecovery.eligible(draft, "storepos-shop", "cashier1", epoch, 999L))
        assertFalse(CartDraftRecovery.eligible(draft, "storepos-shop", "cashier1", epoch, 1000L + CartDraftRecovery.MAX_AGE_MS + 1))
    }

    @Test fun missingInactiveAndOtherShopProductsNeverBecomePurchasable() {
        val draft = CartDraftRecovery.snapshot(
            "storepos-shop", "cashier1", epoch,
            listOf(CartLine(product, 2.0)), 1000L
        )
        assertTrue(CartDraftRecovery.restore(draft, emptyList()).isEmpty())
        assertTrue(CartDraftRecovery.restore(draft, listOf(product.copy(isActive = false))).isEmpty())
        assertTrue(CartDraftRecovery.restore(draft, listOf(product.copy(shopId = "other"))).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidQuantityCannotBeSaved() {
        CartDraftRecovery.snapshot(
            "storepos-shop", "cashier1", epoch,
            listOf(CartLine(product, Double.NaN)), 1000L
        )
    }
}
