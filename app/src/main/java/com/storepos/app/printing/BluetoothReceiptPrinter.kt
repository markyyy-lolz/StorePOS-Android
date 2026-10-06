package com.storepos.app.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BluetoothReceiptPrinter(
    private val context: Context
) : ReceiptPrinter {

    private var socket: android.bluetooth.BluetoothSocket? = null
    private var output: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: PrinterDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeDirect()

            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: error("Bluetooth is not available on this device.")
            require(adapter.isEnabled) { "Bluetooth is turned off." }

            val btDevice = adapter.getRemoteDevice(device.address)
            val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val newSocket = btDevice.createRfcommSocketToServiceRecord(spp)
            adapter.cancelDiscovery()
            newSocket.connect()
            socket = newSocket
            output = newSocket.outputStream
        }
    }

    override suspend fun printReceipt(payload: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = output ?: error("Printer is not connected.")
            stream.write(payload)
            stream.flush()
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            closeDirect()
        }
    }

    private fun closeDirect() {
        runCatching { output?.close() }
        runCatching { socket?.close() }
        output = null
        socket = null
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun bondedPrinters(context: Context): List<PrinterDevice> {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
            return adapter.bondedDevices
                .map { PrinterDevice(it.name ?: "Bluetooth printer", it.address) }
                .sortedBy { it.name.lowercase() }
        }

        fun saleReceipt(
            shopName: String,
            sale: com.storepos.app.data.model.Sale,
            cart: List<com.storepos.app.data.model.CartLine>,
            paperWidth: Int = 80,
            receiptHeader: String? = null,
            receiptFooter: String? = null,
            payments: List<com.storepos.app.data.model.CheckoutPayment> = emptyList(),
            cashierLabel: String? = null,
            openCashDrawer: Boolean = false,
            digitalReceiptUrl: String? = null,
            shopAddress: String? = null,
            shopPhone: String? = null,
            shopTin: String? = null,
            receiptTitle: String = "SALES RECEIPT",
            showAddress: Boolean = true,
            showPhone: Boolean = true,
            showTin: Boolean = true,
            showReceiptNumber: Boolean = true,
            showDate: Boolean = true,
            showPaymentReference: Boolean = true,
            showDigitalQr: Boolean = true,
            compactMode: Boolean = false,
            sectionOrder: List<String> = listOf(
                "store", "meta", "items", "totals", "payment", "digital", "footer"
            )
        ): ByteArray {
            val charset = Charset.forName("CP437")
            val width = if (paperWidth == 58) 32 else 48
            val divider = if (compactMode) "-".repeat(width) else "=".repeat(width)
            val thin = "-".repeat(width)

            fun amount(value: Double): String =
                "PHP " + String.format(java.util.Locale.US, "%,.2f", value)

            fun customerPaymentLabel(payment: com.storepos.app.data.model.CheckoutPayment): String {
                val ref = payment.referenceNumber.orEmpty()
                return if (payment.method == "other" && ref.startsWith("PayMongo ", ignoreCase = true)) {
                    "ORPH"
                } else {
                    payment.method.replace("_", " ").uppercase()
                }
            }

            fun customerReference(payment: com.storepos.app.data.model.CheckoutPayment): String? {
                val ref = payment.referenceNumber?.trim()?.takeIf { it.isNotBlank() } ?: return null
                return if (ref.startsWith("PayMongo ", ignoreCase = true)) {
                    ref.replaceFirst(Regex("^PayMongo\\s+", RegexOption.IGNORE_CASE), "")
                } else ref
            }

            fun textBytes(lines: List<String>): ByteArray =
                lines.joinToString("\n", postfix = "\n").toByteArray(charset)

            val sections = linkedMapOf<String, List<String>>()

            sections["store"] = buildList {
                add(center(shopName, width))
                if (showAddress) shopAddress?.trim()?.takeIf { it.isNotBlank() }?.let { add(center(it, width)) }
                if (showPhone) shopPhone?.trim()?.takeIf { it.isNotBlank() }?.let { add(center("Contact: $it", width)) }
                if (showTin) shopTin?.trim()?.takeIf { it.isNotBlank() }?.let { add(center("TIN: $it", width)) }
                receiptHeader?.trim()?.takeIf { it.isNotBlank() }?.lines()?.forEach {
                    add(center(it, width))
                }
                add(center(receiptTitle.trim().ifBlank { "SALES RECEIPT" }, width))
                add(center("NOT AN OFFICIAL TAX RECEIPT", width))
                add(divider)
            }

            sections["meta"] = buildList {
                if (showReceiptNumber) add("Receipt No: " + sale.saleNumber)
                if (showDate) sale.createdAt?.let { add("Date: " + it.replace("T", " ").take(19)) }
                cashierLabel?.let { add("Cashier: " + it.take((width - 9).coerceAtLeast(1))) }
                add(thin)
            }

            sections["items"] = buildList {
                cart.forEach { line ->
                    val qty = if (line.quantity % 1.0 == 0.0) {
                        line.quantity.toInt().toString()
                    } else {
                        String.format(java.util.Locale.US, "%.3f", line.quantity)
                            .trimEnd('0')
                            .trimEnd('.')
                    }
                    add(line.product.name.take(width))
                    val left = qty + " x " + amount(line.unitPrice)
                    val right = amount(line.lineTotal)
                    val spaces = (width - left.length - right.length).coerceAtLeast(1)
                    add((left + " ".repeat(spaces) + right).take(width))
                }
                add(thin)
            }

            sections["totals"] = buildList {
                add(fitPair("Subtotal", amount(sale.subtotal), width))
                if (sale.discountAmount > 0) add(fitPair("Discount", "-" + amount(sale.discountAmount), width))
                if (sale.taxAmount > 0) add(fitPair("Tax", amount(sale.taxAmount), width))
                add(fitPair("TOTAL", amount(sale.totalAmount), width))
                add(divider)
            }

            sections["payment"] = buildList {
                if (payments.isNotEmpty()) {
                    add("PAYMENT DETAILS")
                    payments.forEach { payment ->
                        add(fitPair(customerPaymentLabel(payment), amount(payment.amount), width))
                        add(fitPair("Status", "PAID", width))
                        if (showPaymentReference) {
                            customerReference(payment)?.let { add(("Ref: " + it).take(width)) }
                        }
                        if (payment.method == "cash" && payment.tendered != null) {
                            add(fitPair("Cash tendered", amount(payment.tendered), width))
                        }
                    }
                } else {
                    sale.amountTendered?.let { add(fitPair("Tendered", amount(it), width)) }
                }
                sale.changeDue?.takeIf { it > 0 }?.let {
                    add(fitPair("CHANGE", amount(it), width))
                }
            }

            sections["footer"] = buildList {
                add(thin)
                receiptFooter?.trim()?.takeIf { it.isNotBlank() }?.lines()?.forEach {
                    add(center(it, width))
                } ?: run {
                    add(center("Thank you for your purchase.", width))
                    add(center("Please come again.", width))
                }
                add(thin)
                add(center("Powered by StorePOS", width))
                add(center("Retail Management & POS System", width))
            }

            val editable = listOf("meta", "items", "totals", "payment", "digital")
            val normalizedMiddle = sectionOrder
                .filter { it in editable }
                .distinct()
                .toMutableList()
                .also { current ->
                    editable.filterNot { it in current }.forEach(current::add)
                }
            val normalizedOrder = listOf("store") + normalizedMiddle + listOf("footer")

            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x1B, 0x40))

            if (openCashDrawer) {
                out.write(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()))
            }

            normalizedOrder.forEach { key ->
                if (key == "digital") {
                    if (showDigitalQr && !digitalReceiptUrl.isNullOrBlank()) {
                        out.write(textBytes(listOf(
                            thin,
                            center("DIGITAL RECEIPT", width)
                        )))
                        out.write(escPosQrCode(digitalReceiptUrl, if (paperWidth == 58) 4 else 6))
                        out.write(textBytes(listOf(
                            center("Scan to view your digital receipt", width),
                            center("Available for 3 days only.", width)
                        )))
                    }
                } else {
                    val lines = sections[key].orEmpty()
                    if (lines.isNotEmpty()) out.write(textBytes(lines))
                }
            }

            out.write("\n\n".toByteArray(charset))
            out.write(byteArrayOf(0x1D, 0x56, 0x00))
            return out.toByteArray()
        }

        private fun escPosQrCode(value: String, moduleSize: Int): ByteArray {
            val data = value.toByteArray(Charsets.UTF_8)
            val storeLength = data.size + 3
            val pL = (storeLength and 0xFF).toByte()
            val pH = ((storeLength shr 8) and 0xFF).toByte()

            val centerAlign = byteArrayOf(0x1B, 0x61, 0x01)
            val model2 = byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)
            val size = byteArrayOf(
                0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43,
                moduleSize.coerceIn(1, 16).toByte()
            )
            val errorCorrectionM = byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31)
            val store = byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30) + data
            val print = byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30)
            val leftAlign = byteArrayOf(0x1B, 0x61, 0x00)
            val lineFeed = byteArrayOf(0x0A)

            return centerAlign + model2 + size + errorCorrectionM + store + print + lineFeed + leftAlign
        }

        private fun fitPair(left: String, right: String, width: Int): String {
            if (left.length + right.length + 1 > width) {
                val leftMax = (width - right.length - 1).coerceAtLeast(1)
                return left.take(leftMax) + " " + right.takeLast((width - leftMax - 1).coerceAtLeast(1))
            }
            return left + " ".repeat((width - left.length - right.length).coerceAtLeast(1)) + right
        }

        fun testReceipt(shopName: String, paperWidth: Int = 80): ByteArray {
            val charset = Charset.forName("CP437")
            val width = if (paperWidth == 58) 32 else 48
            val lines = mutableListOf<String>()
            lines += center(shopName, width)
            lines += center("StorePOS Printer Test", width)
            lines += "-".repeat(width)
            lines += "Bluetooth ESC/POS connected"
            lines += "Paper width: ${paperWidth}mm"
            lines += "Status: OK"
            lines += "-".repeat(width)
            lines += center("Ride safe!", width)
            val body = lines.joinToString("\n", postfix = "\n\n\n").toByteArray(charset)

            val init = byteArrayOf(0x1B, 0x40)
            val cut = byteArrayOf(0x1D, 0x56, 0x00)
            return init + body + cut
        }

        private fun center(value: String, width: Int): String {
            val text = value.take(width)
            val left = ((width - text.length) / 2).coerceAtLeast(0)
            return " ".repeat(left) + text
        }
    }
}
