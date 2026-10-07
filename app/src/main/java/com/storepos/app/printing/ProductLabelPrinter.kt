package com.storepos.app.printing

import android.content.Context
import com.storepos.app.data.model.Product
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale

object ProductLabelPrinter {
    suspend fun printConfigured(
        context: Context,
        product: Product,
        copies: Int
    ): Result<Unit> {
        val prefs = context.getSharedPreferences("motopos_settings", 0)
        val transport = prefs.getString("printer_transport", "bluetooth") ?: "bluetooth"
        val address = prefs.getString("printer_address", null)
            ?: return Result.failure(IllegalStateException("No receipt / label printer is configured. Open Settings > Receipt printer first."))
        val name = prefs.getString("printer_name", null) ?: "StorePOS printer"
        val paperWidth = prefs.getInt("paper_width", 80)
        val device = PrinterDevice(name = name, address = address, transport = transport)
        val printer: ReceiptPrinter = if (transport == "usb") {
            UsbReceiptPrinter(context)
        } else {
            BluetoothReceiptPrinter(context)
        }

        return runCatching {
            printer.connect(device).getOrThrow()
            printer.printReceipt(labelBytes(product, copies.coerceIn(1, 100), paperWidth)).getOrThrow()
        }.also {
            runCatching { printer.disconnect() }
        }
    }

    fun labelBytes(product: Product, copies: Int, paperWidth: Int = 80): ByteArray {
        val charset = Charset.forName("CP437")
        val out = ByteArrayOutputStream()
        val lineWidth = if (paperWidth == 58) 32 else 48

        fun raw(vararg bytes: Int) {
            out.write(bytes.map { it.toByte() }.toByteArray())
        }

        fun text(value: String) {
            out.write(value.toByteArray(charset))
        }

        fun line(value: String = "") {
            text(value)
            raw(0x0A)
        }

        fun center(value: String): String {
            val clean = value.take(lineWidth)
            val pad = ((lineWidth - clean.length) / 2).coerceAtLeast(0)
            return " ".repeat(pad) + clean
        }

        fun printBarcode(value: String) {
            val code = value
                .filter { it.code in 32..126 }
                .take(40)
            if (code.isBlank()) return

            raw(0x1D, 0x48, 0x02)
            raw(0x1D, 0x68, 0x38)
            raw(0x1D, 0x77, 0x02)

            val data = ("{B" + code).toByteArray(Charsets.US_ASCII)
            raw(0x1D, 0x6B, 0x49, data.size)
            out.write(data)
            raw(0x0A)
        }

        repeat(copies.coerceIn(1, 100)) {
            raw(0x1B, 0x40)
            raw(0x1B, 0x61, 0x01)

            raw(0x1B, 0x45, 0x01)
            line(center(product.name.take(if (paperWidth == 58) 30 else 44)))
            raw(0x1B, 0x45, 0x00)

            product.brand?.takeIf { it.isNotBlank() }?.let {
                line(center(it.take(lineWidth)))
            }

            raw(0x1D, 0x21, 0x11)
            line(center("PHP " + String.format(Locale.US, "%,.2f", product.sellingPrice)))
            raw(0x1D, 0x21, 0x00)

            val code = product.barcode?.takeIf { it.isNotBlank() }
                ?: product.sku.takeIf { it.isNotBlank() }

            code?.let {
                printBarcode(it)
                line(center(it.take(lineWidth)))
            }

            line(center("SKU: " + product.sku.take((lineWidth - 5).coerceAtLeast(1))))
            product.shelfLocation?.takeIf { it.isNotBlank() }?.let {
                line(center("Shelf: " + it.take((lineWidth - 7).coerceAtLeast(1))))
            }

            raw(0x0A, 0x0A, 0x0A)
            raw(0x1D, 0x56, 0x00)
        }

        return out.toByteArray()
    }
}
