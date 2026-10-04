package com.storepos.app.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
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
            openCashDrawer: Boolean = false
        ): ByteArray {
            val charset = Charset.forName("CP437")
            val width = if (paperWidth == 58) 32 else 48
            fun amount(value: Double): String =
                "PHP " + String.format(java.util.Locale.US, "%,.2f", value)

            val lines = mutableListOf<String>()
            lines += center(shopName, width)
            receiptHeader?.trim()?.takeIf { it.isNotBlank() }?.lines()?.forEach {
                lines += center(it, width)
            }
            lines += center("SALES RECEIPT", width)
            lines += center("NOT AN OFFICIAL TAX RECEIPT", width)
            lines += "-".repeat(width)
            lines += "Sale: " + sale.saleNumber
            sale.createdAt?.let { lines += "Date: " + it.replace("T", " ").take(19) }
            cashierLabel?.let { lines += "Cashier: " + it.take((width - 9).coerceAtLeast(1)) }
            lines += "-".repeat(width)

            cart.forEach { line ->
                val qty = if (line.quantity % 1.0 == 0.0) {
                    line.quantity.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.3f", line.quantity).trimEnd('0').trimEnd('.')
                }
                lines += line.product.name.take(width)
                val left = qty + " x " + amount(line.unitPrice)
                val right = amount(line.lineTotal)
                val spaces = (width - left.length - right.length).coerceAtLeast(1)
                lines += (left + " ".repeat(spaces) + right).take(width)
            }

            lines += "-".repeat(width)
            lines += fitPair("Subtotal", amount(sale.subtotal), width)
            if (sale.discountAmount > 0) lines += fitPair("Discount", "-" + amount(sale.discountAmount), width)
            if (sale.taxAmount > 0) lines += fitPair("Tax", amount(sale.taxAmount), width)
            lines += fitPair("TOTAL", amount(sale.totalAmount), width)
            lines += "-".repeat(width)

            if (payments.isNotEmpty()) {
                lines += "PAYMENTS"
                payments.forEach { payment ->
                    val label = payment.method.replace("_", " ").uppercase()
                    lines += fitPair(label, amount(payment.amount), width)
                    payment.referenceNumber?.let {
                        lines += ("Ref: " + it).take(width)
                    }
                    if (payment.method == "cash" && payment.tendered != null) {
                        lines += fitPair("Cash tendered", amount(payment.tendered), width)
                    }
                }
            } else {
                sale.amountTendered?.let { lines += fitPair("Tendered", amount(it), width) }
            }
            sale.changeDue?.takeIf { it > 0 }?.let {
                lines += fitPair("CHANGE", amount(it), width)
            }

            lines += "-".repeat(width)
            receiptFooter?.trim()?.takeIf { it.isNotBlank() }?.lines()?.forEach {
                lines += center(it, width)
            } ?: run {
                lines += center("Thank you for choosing us.", width)
            }
            lines += center("Powered by StorePOS", width)

            val init = byteArrayOf(0x1B, 0x40)
            val body = lines.joinToString("\n", postfix = "\n\n\n").toByteArray(charset)
            val drawer = if (openCashDrawer) byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()) else byteArrayOf()
            val cut = byteArrayOf(0x1D, 0x56, 0x00)
            return init + drawer + body + cut
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
