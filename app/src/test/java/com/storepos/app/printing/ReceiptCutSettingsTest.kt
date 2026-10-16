package com.storepos.app.printing

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.Sale
import com.storepos.app.data.model.Shop
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptCutSettingsTest {
    private val shop = Shop(id = "retail-shop", name = "StorePOS Retail")
    private val sale = Sale(
        id = "sale-100", shopId = shop.id, saleNumber = "S-100",
        cashierId = "cashier", subtotal = 39.0, discountAmount = 0.0,
        taxAmount = 0.0, totalAmount = 39.0, status = "completed"
    )
    private val product = Product(id = "product-100", shopId = shop.id,
        name = "Test product", sku = "P-100", sellingPrice = 39.0)

    private fun assertFooterGap(receipt: ByteArray, extraLines: Int) {
        // Hardware cutter must follow the final printed supplier contact.
        val needle = (PosSupplierFooter.PHONE + "\n").toByteArray(Charsets.US_ASCII)
        val footerIndex = receipt.indices.firstOrNull { start ->
            start + needle.size <= receipt.size &&
                receipt.copyOfRange(start, start + needle.size).contentEquals(needle)
        }
        // All receipt/report outputs must finish their printed footer before the cutter.
        assertTrue("Missing StorePOS footer in ESC/POS data", footerIndex != null)
        val gapIndex = footerIndex!! + needle.size
        val expected = ReceiptCutSettings.extraFeedAndFullCut(extraLines)
        assertArrayEquals(expected, receipt.copyOfRange(gapIndex, receipt.size))
    }

    @Test fun defaultIsExistingTwoLinesBeforeFullCut() {
        assertEquals(2, ReceiptCutSettings.DEFAULT_LINES)
        assertArrayEquals(byteArrayOf(0x0A, 0x0A, 0x1D, 0x56, 0x00),
            ReceiptCutSettings.extraFeedAndFullCut(2))
    }

    @Test fun zeroGapKeepsFullCutAndEightIsMaximum() {
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x00),
            ReceiptCutSettings.extraFeedAndFullCut(0))
        assertEquals(8, ReceiptCutSettings.normalize(100))
        assertEquals(0, ReceiptCutSettings.normalize(-5))
        assertEquals(11, ReceiptCutSettings.extraFeedAndFullCut(80).size)
        assertEquals(3, ReceiptCutSettings.extraFeedAndFullCut(-10).size)
    }

    @Test fun saleReceiptHasCutOnlyAfterBranding() {
        for (lines in listOf(0, 2, 5, 8)) {
            val receipt = BluetoothReceiptPrinter.saleReceipt(
                shop.name, sale, listOf(CartLine(product, 1.0)),
                footerFeedLines = lines
            )
            assertFooterGap(receipt, lines)
        }
    }

    @Test fun directAndSharedTestPrintUseIdenticalCutBytes() {
        for (lines in 0..8) {
            val print = BluetoothReceiptPrinter.testReceipt(
                shop.name, paperWidth = 80, footerFeedLines = lines
            )
            val queued = print.copyOf()
            assertArrayEquals(print, queued)
            assertFooterGap(print, lines)
        }
    }

    @Test fun supplierContactVisibleOnBothReceiptWidths() {
        for (paperWidth in listOf(58, 80)) {
            val printed = String(
                BluetoothReceiptPrinter.saleReceipt(
                    shop.name, sale, listOf(CartLine(product, 1.0)),
                    paperWidth = paperWidth
                ),
                Charsets.US_ASCII
            )
            assertTrue(printed.contains(PosSupplierFooter.NAME))
            assertTrue(printed.contains(PosSupplierFooter.PHONE))
            assertFalse(printed.contains("Powered by StorePOS"))
            if (paperWidth == 58) {
                assertTrue(printed.contains("PULO SAN ROQUE,"))
                assertTrue(printed.contains("PAOMBONG, BULACAN"))
            } else {
                assertTrue(printed.contains(PosSupplierFooter.ADDRESS))
            }
            val lineWidth = if (paperWidth == 58) 32 else 48
            assertTrue(PosSupplierFooter.centeredLines(lineWidth).all { it.length <= lineWidth })
        }
    }

    @Test fun xReportFooterGapMatchesReceipt() {
        val report = buildJsonObject {
            put("cash_sales", 39.0)
            put("gross_sales", 39.0)
            put("transaction_count", 1)
        }
        val data = ThermalReportFormatter.x(
            shop, report, "T-01", "2026-10-09", footerFeedLines = 3
        )
        assertFooterGap(data, 3)
    }

    @Test fun footerFeedChangesNoSaleAmountsOrPrintableLine() {
        val a = BluetoothReceiptPrinter.saleReceipt(shop.name, sale,
            listOf(CartLine(product, 1.0)), footerFeedLines = 0)
        val b = BluetoothReceiptPrinter.saleReceipt(shop.name, sale,
            listOf(CartLine(product, 1.0)), footerFeedLines = 8)
        val outputA = String(a, Charsets.US_ASCII).substringBefore("POS SYSTEM SUPPLIED")
        val outputB = String(b, Charsets.US_ASCII).substringBefore("POS SYSTEM SUPPLIED")
        assertEquals(outputA, outputB)
        assertTrue(outputA.contains("39.00"))
        assertFalse(ReceiptCutSettings.extraFeedAndFullCut(0).contentEquals(
            ReceiptCutSettings.extraFeedAndFullCut(8)))
    }
}
