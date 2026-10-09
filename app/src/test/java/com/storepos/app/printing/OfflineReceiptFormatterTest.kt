package com.storepos.app.printing

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.CheckoutPayment
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ShopSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReceiptFormatterTest {
    private val shop = Shop(id = "shop-1", name = "Sherine Store")
    private val settings = ShopSettings(shopId = "shop-1", autoPrintReceipt = true)
    private val cash = listOf(CheckoutPayment(method = "cash", amount = 39.0, tendered = 50.0))
    private val cart = listOf(
        CartLine(
            product = Product(
                id = "product-1", shopId = "shop-1", sku = "P39", name = "Test product",
                sellingPrice = 39.0, stockQuantity = 5.0
            ), quantity = 1.0
        )
    )

    @Test fun offlineReceiptUsesStableProvisionalNumber() {
        val id = "31e52000-5b71-4db3-829a-aabbccddeeff"
        assertEquals("OFF-31E520005B71", OfflineReceiptFormatter.numberFor(id))
        assertEquals(
            OfflineReceiptFormatter.numberFor(id),
            OfflineReceiptFormatter.numberFor(id)
        )
    }

    @Test fun thermalReceiptClearlySaysPendingNotPosted() {
        val receipt = OfflineReceiptFormatter.create(
            shop, settings, "31e52000-5b71-4db3-829a-aabbccddeeff",
            "cashier", cart, cash, discount = 0.0, tax = 0.0,
            paperWidth = 80, cashierLabel = "Cashier",
            footerFeedLines = 3, createdAt = "2026-10-09T16:00:00Z"
        )
        val text = String(receipt.thermalBytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("OFFLINE CASH RECEIPT"))
        assertTrue(text.contains("NOT AN OFFICIAL TAX RECEIPT"))
        assertTrue(text.contains("OFF-31E520005B71"))
        assertTrue(text.contains("NOT POSTED TO STOREPOS CLOUD"))
        assertTrue(text.contains("PENDING SYNC"))
        assertTrue(text.contains("39.00"))
        assertTrue(text.contains("Powered by StorePOS"))
        assertFalse(text.contains("S-2026-"))
        assertFalse(text.contains("https://"))
        assertEquals("pending_sync", receipt.sale.status)
        assertEquals(39.0, receipt.sale.totalAmount, 0.001)
        assertEquals(11.0, receipt.sale.changeDue ?: -1.0, 0.001)
        val cut = ReceiptCutSettings.extraFeedAndFullCut(3)
        assertArrayEquals(
            cut, receipt.thermalBytes.copyOfRange(receipt.thermalBytes.size-cut.size,receipt.thermalBytes.size)
        )
    }

    @Test fun sameFormatterSupports58mmAnd80mm() {
        for (paperWidth in listOf(58, 80)) {
            val result = OfflineReceiptFormatter.create(
                shop, settings, "a8e52000-5b71-4db3-829a-aabbccddeeff",
                "cashier", cart, cash, discount=0.0,tax=0.0,paperWidth=paperWidth,
                cashierLabel=null
            )
            assertTrue(String(result.thermalBytes, Charsets.ISO_8859_1).contains("OFFLINE CASH RECEIPT"))
        }
    }

    @Test fun onlineOnlyPaymentsCannotGenerateOfflineReceipt() {
        val failure = runCatching {
            OfflineReceiptFormatter.create(
                shop, settings, "a8e52000-5b71-4db3-829a-aabbccddeeff", "cashier",
                cart, listOf(CheckoutPayment(method="paymongo",amount=39.0)),
                0.0,0.0,80,null
            )
        }
        assertTrue(failure.isFailure)
    }
}
