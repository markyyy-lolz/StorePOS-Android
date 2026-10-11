package com.storepos.app.data.local

import com.storepos.app.data.model.CheckoutPayment
import com.storepos.app.data.model.OfflineSalePayload
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.SaleRpcItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncPolicyTest {
    private val shop = "shop-a"
    private val other = "shop-b"
    private val catalog = listOf(
        Product(id = "p1", shopId = shop, sku = "A100", name = "Product A",
            sellingPrice = 39.00, stockQuantity = 10.0),
        Product(id = "p2", shopId = shop, sku = "B200", name = "Product B",
            sellingPrice = 50.00, stockQuantity = 7.0)
    )
    private fun sale(
        key: String = "tablet-1-unique-uuid",
        shopId: String = shop,
        items: List<SaleRpcItem> = listOf(SaleRpcItem("p1", 1.0, 39.00)),
        paymentMethod: String = "cash"
    ) = OfflineSalePayload(
        clientKey = key,
        shopId = shopId,
        paymentMethod = paymentMethod,
        items = items,
        payments = listOf(CheckoutPayment(method = paymentMethod, amount = 39.00))
    )

    @Test fun twoIndependentTabletsCanQueueDistinctSaleKeys() {
        assertNull(OfflineSyncPolicy.reviewReason(shop, sale(key="tablet-a-key"), catalog))
        assertNull(OfflineSyncPolicy.reviewReason(shop, sale(key="tablet-b-key"), catalog))
    }
    @Test fun neverSyncAnotherShopSale() {
        assertEquals("Sale belongs to a different shop.",
            OfflineSyncPolicy.reviewReason(shop, sale(shopId=other), catalog))
    }
    @Test fun factoryResetDeletedProductNeedsReview() {
        assertTrue(OfflineSyncPolicy.reviewReason(shop, sale(), emptyList())!!
            .startsWith("Product was removed or reset"))
    }
    @Test fun removedOrDisabledProductNeedsReview() {
        assertTrue(OfflineSyncPolicy.reviewReason(shop, sale(),
            catalog.map { if (it.id=="p1") it.copy(isActive=false) else it })!!.contains("no longer active"))
    }
    @Test fun doNotRepriceHistoricalCashSale() {
        val pricesChanged = catalog.map {
            if (it.id=="p1") it.copy(sellingPrice=49.0) else it
        }
        assertTrue(OfflineSyncPolicy.reviewReason(shop, sale(), pricesChanged)!!
            .contains("price changed"))
    }
    @Test fun insufficientServerStockNeedsReview() {
        assertTrue(OfflineSyncPolicy.reviewReason(
            shop, sale(items=listOf(SaleRpcItem("p1", 11.0, 39.0))), catalog
        )!!.contains("Insufficient cloud stock"))
    }
    @Test fun oldQueuedSaleWithoutPriceSnapshotRequiresHumanReview() {
        assertTrue(OfflineSyncPolicy.reviewReason(
            shop, sale(items=listOf(SaleRpcItem("p1", 1.0))), catalog
        )!!.contains("Legacy sale"))
    }
    @Test fun offlineOnlyCashCanAutoSync() {
        assertNull(OfflineSyncPolicy.reviewReason(shop, sale(), catalog))
        assertFalse(OfflineSyncPolicy.isCashOnly(sale(paymentMethod="card")))
        assertTrue(OfflineSyncPolicy.reviewReason(shop, sale(paymentMethod="card"), catalog)!!
            .contains("cash payments only"))
        assertTrue(OfflineSyncPolicy.isCashOnly(sale()))
    }
    @Test fun normalMatchingProductQuantitiesReconcile() {
        assertNull(OfflineSyncPolicy.reviewReason(
            shop,
            sale(items=listOf(SaleRpcItem("p1", 2.0, 39.00), SaleRpcItem("p2", 3.0, 50.00))),
            catalog
        ))
    }
    @Test fun newHybridSalesHaveCashierAndTrustedEpoch() {
        val sale = sale().copy(
            cashierId="cashier-a",
            catalogEpoch="123e4567-e89b-12d3-a456-426614174000"
        )
        assertNull(OfflineSyncPolicy.localReviewReason(shop,sale))
        assertTrue(OfflineSyncPolicy.localReviewReason(shop,
            sale.copy(catalogEpoch=null))!!.contains("Legacy"))
        assertTrue(OfflineSyncPolicy.localReviewReason(shop,
            sale.copy(cashierId=null))!!.contains("cashier"))
    }

    @Test fun syncDoesNotPreemptivelyRejectPostedSaleBasedOnCurrentCloudStock() {
        val postedThenTimedOut = sale(items=listOf(SaleRpcItem("p1",2.0,39.0))).copy(
            cashierId="cashier-a",
            catalogEpoch="123e4567-e89b-12d3-a456-426614174000"
        )
        // Cloud might already have decremented stock before losing the response;
        // only server's idempotency ledger can distinguish that from a second sale.
        assertNull(OfflineSyncPolicy.localReviewReason(shop,postedThenTimedOut))
        assertTrue(OfflineSyncPolicy.reviewReason(shop,postedThenTimedOut,
            catalog.map { if (it.id=="p1") it.copy(stockQuantity=0.0) else it })!!
            .contains("Insufficient cloud stock"))
    }

    @Test fun incompleteOfflineCashPayloadCannotUpload() {
        val good = sale().copy(
            cashierId="cashier-a",
            catalogEpoch="123e4567-e89b-12d3-a456-426614174000"
        )
        assertTrue(OfflineSyncPolicy.localReviewReason(shop,
            good.copy(items=listOf(SaleRpcItem("p1",1.0,null))))!!
            .contains("price"))
        assertTrue(OfflineSyncPolicy.localReviewReason(shop,
            good.copy(payments=listOf(CheckoutPayment(method="gcash",amount=39.0))))!!
            .contains("cash"))
        assertTrue(OfflineSyncPolicy.localReviewReason(other,good)!!
            .contains("another shop"))
    }

}
