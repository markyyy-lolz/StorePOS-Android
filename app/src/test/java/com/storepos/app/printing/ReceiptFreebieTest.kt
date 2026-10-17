package com.storepos.app.printing

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.CheckoutPayment
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.ReceiptFreebieRule
import com.storepos.app.data.model.Sale
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ShopSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFreebieTest {
    private val shop = Shop(id = "s", name = "Sherine Store")
    private val settings = ShopSettings(shopId = "s")
    private val cart = listOf(CartLine(
        Product(id = "p", shopId = "s", sku = "P", name = "Groceries", sellingPrice = 1600.0)
    ))
    private val rule = ReceiptFreebieRule(
        shopId = "s", enabled = true, minimumTotal = 1500.0,
        giftName = "Free Rice 1kg", quantity = 1
    )

    private fun bytesFor(final: Double, ruleValue: ReceiptFreebieRule = rule, mm: Int = 80): String {
        val sale = Sale(
            id = "sale", shopId = "s", saleNumber = "S-00001", cashierId = "cashier",
            subtotal = 1600.0, discountAmount = 1600.0 - final,
            taxAmount = 0.0, totalAmount = final, status = "completed"
        )
        return String(BluetoothReceiptPrinter.saleReceipt(
            shopName = shop.name, sale = sale, cart = cart, paperWidth = mm,
            payments = listOf(CheckoutPayment(method = "cash", amount = final)),
            receiptFreebieRule = ruleValue
        ), Charsets.ISO_8859_1)
    }

    @Test fun thresholdUsesFinalTotalAfterDiscountsNotSubtotal() {
        assertFalse(rule.qualifies(1499.99))
        assertTrue(rule.qualifies(1500.00))
        assertTrue(rule.qualifies(1501.00))
        assertFalse(bytesFor(1499.99).contains("FREE ITEM REWARD"))
        assertTrue(bytesFor(1500.00).contains("FREE ITEM REWARD"))
    }

    @Test fun disabledAndCrossShopPromotionsCannotPrint() {
        assertFalse(bytesFor(2000.0, rule.copy(enabled = false)).contains("FREE ITEM REWARD"))
        assertFalse(bytesFor(2000.0, rule.copy(shopId = "other")).contains("FREE ITEM REWARD"))
    }

    @Test fun sameFreebieCanBeShownOn58And80mmAndKeepsSaleNumber() {
        for (mm in listOf(58, 80)) {
            val text = bytesFor(1600.0, mm = mm)
            assertTrue(text.contains("FREE ITEM REWARD"))
            assertTrue(text.contains("Free Rice 1kg"))
            assertTrue(text.contains("PRICE: PHP 0.00"))
            assertTrue(text.contains("S-00001"))
            assertTrue(text.contains(PosSupplierFooter.NAME))
        }
    }

    @Test fun offlineReceiptContainsIdenticalClaimNoticeWithoutCloudToken() {
        val receipt = OfflineReceiptFormatter.create(
            shop = shop,
            settings = settings,
            clientKey = "0f5ac002-abcd-4321-9876-eeee00001111",
            cashierId = "cashier",
            cart = cart,
            payments = listOf(CheckoutPayment(method="cash",amount=1600.0,tendered=1600.0)),
            discount = 0.0, tax = 0.0, paperWidth = 58,
            cashierLabel = null, receiptFreebieRule = rule
        )
        val content = String(receipt.thermalBytes, Charsets.ISO_8859_1)
        assertTrue(content.contains("FREE ITEM REWARD"))
        assertTrue(content.contains("OFFLINE CASH RECEIPT"))
        assertTrue(content.contains("OFF-"))
        assertFalse(content.contains("https://"))
    }
    private fun cutPositions(bytes: ByteArray): List<Int> =
        (0..bytes.size - 3).filter { i ->
            bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x56.toByte() &&
                bytes[i + 2] == 0x00.toByte()
        }

    @Test fun qualifyingReceiptHasTwoCutsAndPhysicalVoucherStartsAfterFirstCut() {
        for (mm in listOf(58, 80)) {
            val sale = Sale(
                id = "sale", shopId = "s", saleNumber = "S-00001", cashierId = "cashier",
                subtotal = 1600.0, discountAmount = 0.0, taxAmount = 0.0,
                totalAmount = 1600.0, status = "completed"
            )
            val bytes = BluetoothReceiptPrinter.saleReceipt(
                shopName = shop.name, sale = sale, cart = cart, paperWidth = mm,
                receiptFreebieRule = rule, footerFeedLines = 3
            )
            val cuts = cutPositions(bytes)
            assertEquals("Main receipt and separate voucher each get a full cutter", 2, cuts.size)
            val mainPaper = String(bytes.copyOfRange(0, cuts[0]), Charsets.ISO_8859_1)
            val voucher = String(bytes.copyOfRange(cuts[0] + 3, bytes.size), Charsets.ISO_8859_1)
            assertTrue(mainPaper.contains("Groceries"))
            assertTrue(mainPaper.contains(PosSupplierFooter.PHONE))
            assertFalse(mainPaper.contains("FREE ITEM CLAIM SLIP"))
            assertFalse(mainPaper.contains("FREE ITEM REWARD"))
            assertTrue(voucher.contains("FREE ITEM CLAIM SLIP"))
            assertTrue(voucher.contains("Free Rice 1kg"))
            assertTrue(voucher.contains("Sale Ref: S-00001"))
            assertFalse(voucher.contains("Groceries"))
            assertArrayEquals(
                ReceiptCutSettings.extraFeedAndFullCut(3),
                bytes.copyOfRange(bytes.size - 6, bytes.size)
            )
            val decoded = ThermalReceiptDecoder.decode(bytes)
            assertEquals(1, decoded.count { it is ThermalReceiptElement.Cut })
            val beforeCut = decoded.takeWhile { it !is ThermalReceiptElement.Cut }
            val afterCut = decoded.dropWhile { it !is ThermalReceiptElement.Cut }.drop(1)
            assertTrue(beforeCut.any { it is ThermalReceiptElement.Text && it.line.contains("Groceries") })
            assertTrue(afterCut.any { it is ThermalReceiptElement.Text && it.line.contains("FREE ITEM CLAIM SLIP") })
        }
    }

    @Test fun receiptBelowThresholdOrDisabledAlwaysHasSingleOriginalFullCut() {
        for (configured in listOf(rule.copy(enabled = false), rule.copy(minimumTotal = 2000.0))) {
            val sale = Sale(
                id = "sale", shopId = "s", saleNumber = "S-00001", cashierId = "cashier",
                subtotal = 1600.0, discountAmount = 0.0, taxAmount = 0.0,
                totalAmount = 1600.0, status = "completed"
            )
            val bytes = BluetoothReceiptPrinter.saleReceipt(
                shop.name, sale, cart, paperWidth = 80, receiptFreebieRule = configured
            )
            assertEquals(1, cutPositions(bytes).size)
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains("FREE ITEM CLAIM SLIP"))
            assertEquals(0, ThermalReceiptDecoder.decode(bytes).count { it is ThermalReceiptElement.Cut })
        }
    }

    @Test fun offlineClaimSlipKeepsProvisionalSaleIdAndBothCuts() {
        val receipt = OfflineReceiptFormatter.create(
            shop = shop, settings = settings,
            clientKey = "0f5ac002-abcd-4321-9876-eeee00001111",
            cashierId = "cashier", cart = cart,
            payments = listOf(CheckoutPayment(method = "cash", amount = 1600.0)),
            discount = 0.0, tax = 0.0, paperWidth = 80,
            cashierLabel = null, receiptFreebieRule = rule
        )
        assertEquals(2, cutPositions(receipt.thermalBytes).size)
        val text = String(receipt.thermalBytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("OFFLINE - PENDING SYNC"))
        assertTrue(text.contains("Sale Ref: OFF-0F5AC002ABCD"))
    }

}
