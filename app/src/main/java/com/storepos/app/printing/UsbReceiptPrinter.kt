package com.storepos.app.printing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbReceiptPrinter(
    private val context: Context
) : ReceiptPrinter {

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpoint: UsbEndpoint? = null

    override val isConnected: Boolean
        get() = connection != null && endpoint != null

    override suspend fun connect(device: PrinterDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeDirect()
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val usbDevice = findDevice(manager, device.address)
                ?: error("USB printer is not connected.")
            require(manager.hasPermission(usbDevice)) {
                "USB permission required. Tap Allow USB, approve the printer, then try again."
            }

            val match = findBulkOutInterface(usbDevice)
                ?: error("This USB device does not expose an ESC/POS bulk output endpoint.")
            val opened = manager.openDevice(usbDevice)
                ?: error("Unable to open the USB printer.")
            if (!opened.claimInterface(match.first, true)) {
                opened.close()
                error("Unable to claim the USB printer interface.")
            }

            connection = opened
            usbInterface = match.first
            endpoint = match.second
        }
    }

    override suspend fun printReceipt(payload: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val opened = connection ?: error("USB printer is not connected.")
            val out = endpoint ?: error("USB printer output endpoint is unavailable.")
            var offset = 0
            val maxChunk = 4096
            while (offset < payload.size) {
                val size = minOf(maxChunk, payload.size - offset)
                val chunk = payload.copyOfRange(offset, offset + size)
                val written = opened.bulkTransfer(out, chunk, chunk.size, 5000)
                if (written <= 0) error("USB printer stopped responding.")
                offset += written
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) { closeDirect() }
    }

    private fun closeDirect() {
        val opened = connection
        val intf = usbInterface
        if (opened != null && intf != null) runCatching { opened.releaseInterface(intf) }
        runCatching { opened?.close() }
        connection = null
        usbInterface = null
        endpoint = null
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.storepos.app.USB_PERMISSION"

        fun usbPrinters(context: Context): List<PrinterDevice> {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return manager.deviceList.values
                .filter { findBulkOutInterface(it) != null }
                .map { device ->
                    PrinterDevice(
                        name = device.productName?.takeIf { it.isNotBlank() }
                            ?: "USB ESC/POS ${device.vendorId}:${device.productId}",
                        address = addressFor(device),
                        transport = "usb"
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

        fun hasPermission(context: Context, address: String): Boolean {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val device = findDevice(manager, address) ?: return false
            return manager.hasPermission(device)
        }

        fun requestPermission(context: Context, address: String): Boolean {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val device = findDevice(manager, address) ?: return false
            if (manager.hasPermission(device)) return true
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            manager.requestPermission(device, pending)
            return false
        }

        private fun addressFor(device: UsbDevice): String =
            "usb:${device.deviceId}:${device.vendorId}:${device.productId}"

        private fun findDevice(manager: UsbManager, address: String): UsbDevice? {
            val parts = address.split(":")
            if (parts.size < 4 || parts[0] != "usb") return null
            val deviceId = parts[1].toIntOrNull()
            val vendorId = parts[2].toIntOrNull()
            val productId = parts[3].toIntOrNull()
            return manager.deviceList.values.firstOrNull { it.deviceId == deviceId }
                ?: manager.deviceList.values.firstOrNull {
                    it.vendorId == vendorId && it.productId == productId
                }
        }

        private fun findBulkOutInterface(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.direction == UsbConstants.USB_DIR_OUT &&
                        ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    ) {
                        return intf to ep
                    }
                }
            }
            return null
        }
    }
}
