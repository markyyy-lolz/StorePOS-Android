package com.storepos.app.data.local

import com.storepos.app.data.model.OfflineSalePayload
import com.storepos.app.data.model.PendingOfflineSale
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.SaleRpcItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineProductCatalogTest {
    private fun pending(shop: String, id: String, qty: Double) =
        PendingOfflineSale(
            id = "$shop-$id",
            payload = OfflineSalePayload(
                clientKey = "$shop-$id",
                shopId = shop,
                paymentMethod = "cash",
                items = listOf(SaleRpcItem(productId = id, quantity = qty))
            ),
            createdAt = 1L
        )

    @Test
    fun overlaysOnlyUnsyncedSalesForCurrentShop() {
        val products = listOf(
            Product(id = "p1", shopId = "shopA", sku = "001", name = "Rice", stockQuantity = 20.0),
            Product(id = "p2", shopId = "shopA", sku = "002", name = "Water", stockQuantity = 10.0)
        )
        val adjusted = productsWithPendingOfflineSales(
            products,
            listOf(pending("shopA", "p1", 3.0), pending("shopB", "p1", 10.0), pending("shopA", "p1", 2.0)),
            "shopA"
        )
        assertEquals(15.0, adjusted[0].stockQuantity, 0.0001)
        assertEquals(10.0, adjusted[1].stockQuantity, 0.0001)
    }

    @Test
    fun doesNotChangeNonTrackedStockOrGoNegative() {
        val products = listOf(
            Product(id = "tracked", shopId = "shopA", sku = "t", name = "Tracked", stockQuantity = 2.0),
            Product(id = "other", shopId = "shopA", sku = "o", name = "Untracked", trackStock = false, stockQuantity = 2.0)
        )
        val adjusted = productsWithPendingOfflineSales(
            products,
            listOf(pending("shopA", "tracked", 5.0), pending("shopA", "other", 5.0)),
            "shopA"
        )
        assertEquals(0.0, adjusted[0].stockQuantity, 0.0001)
        assertEquals(2.0, adjusted[1].stockQuantity, 0.0001)
    }
}
