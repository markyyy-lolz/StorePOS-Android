package com.storepos.app.data.local

import com.storepos.app.data.model.OfflineSalePayload
import com.storepos.app.data.model.PendingOfflineSale
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.SaleRpcItem
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineProductCatalogTest {
    private fun queued(shop: String, epoch: String, id: String, qty: Double) =
        PendingOfflineSale(
            id = "$shop-$id-$qty",
            createdAt = 1L,
            payload = OfflineSalePayload(
                clientKey = "$shop-$id-$qty",
                shopId = shop,
                paymentMethod = "cash",
                catalogEpoch = epoch,
                items = listOf(SaleRpcItem(productId = id, quantity = qty))
            )
        )

    @Test fun neverOverlayOtherShopsOrOldEpochs() {
        val catalog = listOf(Product(id = "p", shopId = "store", sku = "p", name = "Product", stockQuantity = 20.0))
        val pending = listOf(
            queued("store", "new", "p", 3.0),
            queued("other", "new", "p", 7.0),
            queued("store", "old", "p", 8.0)
        )
        val result = productsWithQueuedCashSales(catalog, pending, "store", "new")
        assertEquals(17.0, result.single().stockQuantity, 0.0001)
    }

    @Test fun ignoresNonTrackedItemsAndDoesNotGoNegative() {
        val catalog = listOf(
            Product(id = "p", shopId = "store", sku = "p", name = "Product", stockQuantity = 2.0),
            Product(id = "x", shopId = "store", sku = "x", name = "Other", stockQuantity = 2.0, trackStock = false)
        )
        val result = productsWithQueuedCashSales(catalog, listOf(queued("store","epoch","p",5.0), queued("store","epoch","x",5.0)), "store", "epoch")
        assertEquals(0.0, result[0].stockQuantity, 0.0001)
        assertEquals(2.0, result[1].stockQuantity, 0.0001)
    }
}
