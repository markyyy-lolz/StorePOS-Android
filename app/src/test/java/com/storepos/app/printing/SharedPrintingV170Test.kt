package com.storepos.app.printing

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.Sale
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ZReport
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPrintingV170Test {
    private val shop = Shop(id="shop-1",name="StorePOS Retail",tin="000-000-000")

    @Test fun checkoutPrintIdempotencyAndAuditedReprints() {
        assertEquals("sale:transaction-42", SharedPrintRepository.requestKey("transaction-42",false))
        assertEquals("sale:transaction-42", SharedPrintRepository.requestKey("transaction-42",false))
        val one = SharedPrintRepository.requestKey("transaction-42",true)
        val two = SharedPrintRepository.requestKey("transaction-42",true)
        assertTrue(one.startsWith("reprint:transaction-42:"))
        assertNotEquals(one,two)
    }

    @Test fun xReadingNeverClaimsToCloseShift() {
        val report = buildJsonObject {
            put("cash_sales",500.0)
            put("gross_sales",500.0)
            put("transaction_count",3)
        }
        val bytes = ThermalReportFormatter.x(shop,report,"POS 001","2026-10-08")
        val output = String(bytes,Charsets.US_ASCII)
        assertTrue(output.contains("X READING - LIVE"))
        assertTrue(output.contains("DOES NOT CLOSE SHIFT"))
        assertFalse(output.contains("CLOSED SHIFT"))
    }

    @Test fun zReadingUsesAnExistingClosedShiftReport() {
        val z = ZReport(
            id="report-9",shopId=shop.id,shiftId="shift-1",userId="cashier-1",
            cashSales=650.0,noncashSales=200.0,grossSales=850.0,
            transactionCount=12,expectedCash=1650.0,actualCash=1650.0,
            generatedAt="2026-10-08T23:00:00Z"
        )
        val data = ThermalReportFormatter.z(shop,z,"POS 001",true)
        val output = String(data,Charsets.US_ASCII)
        assertTrue(output.contains("BATCH SALES REPORT"))
        assertTrue(output.contains("CASHIER ACCOUNTABILITY"))
        assertTrue(output.contains("CASH COUNT - MANUAL VERIFICATION"))
        assertTrue(output.contains("CLOSED SHIFT"))
        assertEquals(0x1D, data[data.size-3].toInt() and 0xff)
    }

    @Test fun thermalSaleIncludesTaxCustomerAndTerminalAndStillDecodesForPdf() {
        val sale=Sale(id="sale-1",shopId=shop.id,saleNumber="SP-001",
            cashierId="cashier-1",subtotal=100.0,discountAmount=0.0,
            taxAmount=12.0,totalAmount=112.0,status="completed")
        val product=Product(id="product-1",shopId=shop.id,sku="SKU1",name="Sample",sellingPrice=100.0)
        val bytes=BluetoothReceiptPrinter.saleReceipt(shop.name,sale,
            listOf(CartLine(product,1.0)),paperWidth=80,shopTin=shop.tin)
        val lines=ThermalReceiptDecoder.decode(bytes).filterIsInstance<ThermalReceiptElement.Text>()
            .map { it.line }
        assertTrue(lines.any { it.contains("POS Terminal: StorePOS") })
        assertTrue(lines.any { it.contains("VAT / TAX DECLARATION") })
        assertTrue(lines.any { it.contains("Recorded tax") })
        assertTrue(lines.any { it.contains("CUSTOMER INFORMATION") })
        assertTrue(lines.any { it.contains("Signature:") })
    }

    @Test fun refusingToPrintAnotherShopsReport() {
        val report=ZReport(id="r",shopId="motopos-other",shiftId="s",userId="u",
            generatedAt="2026-10-08")
        var blocked=false
        try { ThermalReportFormatter.z(shop,report,"POS 001") }
        catch (e: IllegalArgumentException) { blocked=true }
        assertTrue(blocked)
    }
}
