package com.storepos.app.printing

import android.content.Context
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ZReport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.UUID

/** X never closes a shift; Z prints only an already recorded close snapshot. */
object ThermalReportFormatter {
    private const val width = 48
    private val divider = "-".repeat(width)
    private fun money(amount: Double) = String.format(Locale.US,"%,.2f",amount)
    private fun pair(name: String, amount: String) =
        name.take((width-amount.length-1).coerceAtLeast(1)).padEnd((width-amount.length).coerceAtLeast(1)) + amount

    private fun header(shop: Shop, title: String, terminal: String, date: String): MutableList<String> =
        mutableListOf<String>().apply {
            add(shop.name.take(width).padStart((width+shop.name.length.coerceAtMost(width))/2))
            shop.address?.let { add(it.take(width)) }
            shop.tin?.let { add("TIN: " + it.take(width-5)) }
            add(divider)
            add(title.padStart((width+title.length)/2))
            add("POS terminal: " + terminal.take(width-14))
            add("Generated: " + date.take(width-11))
            add(divider)
        }

    private fun bytes(lines: List<String>): ByteArray =
        byteArrayOf(0x1B,0x40) +
            (lines.joinToString("\n",postfix="\n\n\n").toByteArray(Charsets.US_ASCII)) +
            byteArrayOf(0x1D,0x56,0x00)

    private fun JsonObject.amount(name: String): Double =
        get(name)?.jsonPrimitive?.doubleOrNull ?: 0.0

    fun x(shop: Shop, report: JsonObject, terminal: String, timestamp: String): ByteArray {
        val lines = header(shop,"X READING - LIVE",terminal,timestamp)
        lines += "TENDER RECONCILIATION"
        val methods = runCatching { report["payment_breakdown"]?.jsonObject }.getOrNull()
        methods?.forEach { (key,value) ->
            lines += pair(key.uppercase(),money(value.jsonPrimitive.doubleOrNull ?: 0.0))
        }
        lines += divider
        lines += pair("Cash sales", money(report.amount("cash_sales")))
        lines += pair("Noncash sales", money(report.amount("noncash_sales")))
        lines += pair("Gross sales", money(report.amount("gross_sales")))
        lines += pair("Cash in", money(report.amount("cash_in")))
        lines += pair("Cash out", money(report.amount("cash_out")))
        lines += pair("Cash refunds", money(report.amount("cash_refunds")))
        lines += pair("Expected drawer", money(report.amount("expected_cash")))
        lines += divider
        lines += "CASHIER AUDIT"
        lines += pair("Transactions",report.amount("transaction_count").toInt().toString())
        lines += divider
        lines += "LIVE READING - DOES NOT CLOSE SHIFT"
        lines += "Powered by StorePOS"
        return bytes(lines)
    }

    fun z(shop: Shop, report: ZReport, terminal: String, batch: Boolean = false): ByteArray {
        require(report.shopId == shop.id) { "Z-report belongs to a different store." }
        val title = if (batch) "BATCH SALES REPORT" else "Z READING - FINAL"
        val lines = header(shop,title,terminal,report.generatedAt)
        lines += "TENDER RECONCILIATION"
        lines += pair("Cash",money(report.cashSales))
        lines += pair("Noncash",money(report.noncashSales))
        lines += pair("Gross sales",money(report.grossSales))
        lines += pair("Discounts",money(report.discounts))
        lines += pair("Recorded tax",money(report.tax))
        lines += divider
        lines += "CASHIER ACCOUNTABILITY"
        lines += pair("Opening cash",money(report.openingCash))
        lines += pair("Cash in",money(report.cashIn))
        lines += pair("Cash out",money(report.cashOut))
        lines += pair("Cash refunds",money(report.cashRefunds))
        lines += pair("Expected drawer",money(report.expectedCash))
        lines += pair("Actual drawer",money(report.actualCash))
        lines += pair("Drawer variance",money(report.variance))
        lines += divider
        lines += "CASHIER AUDIT"
        lines += pair("Transactions",report.transactionCount.toString())
        lines += "Shift ID: " + report.shiftId.take(36)
        if (batch) {
            lines += divider
            lines += "CASH COUNT - MANUAL VERIFICATION"
            listOf("1000","500","200","100","50","20","10","5","1").forEach {
                lines += (it + " x ______ = __________").padStart(33)
            }
            lines += "Verified by: __________________________"
        }
        lines += divider
        lines += "CLOSED SHIFT - HISTORICAL SNAPSHOT"
        lines += "Powered by StorePOS"
        return bytes(lines)
    }
}

/** Direct print when selected, otherwise upload exactly the same ESC/POS bytes. */
object SharedPrintDispatcher {
    suspend fun dispatch(
        context: Context, shopId: String, kind: String, payload: ByteArray,
        receiptNumber: String? = null
    ): String {
        if (SharedPrintRepository.mode(context) != "direct") {
            val id = SharedPrintRepository.enqueue(shopId,SharedPrintRepository.deviceId(context),
                kind,payload,kind + ":" + UUID.randomUUID(),receiptNumber=receiptNumber)
            return "Report queued (" + id.take(8) + ")"
        }
        val prefs = context.getSharedPreferences("motopos_settings",0)
        val transport = prefs.getString("printer_transport","bluetooth") ?: "bluetooth"
        val address = prefs.getString("printer_address",null)
            ?: error("Select a printer or enable shared printing in Settings.")
        val name = prefs.getString("printer_name","VOZY G80") ?: "VOZY G80"
        val target: ReceiptPrinter = if (transport == "usb") UsbReceiptPrinter(context)
            else BluetoothReceiptPrinter(context)
        try {
            target.connect(PrinterDevice(name,address,transport)).getOrThrow()
            target.printReceipt(payload).getOrThrow()
            return "Report sent to printer (physical output not confirmed)"
        } finally {
            target.disconnect()
        }
    }
}
