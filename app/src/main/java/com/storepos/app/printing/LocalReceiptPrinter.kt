package com.storepos.app.printing

import android.content.Context

/** A local Bluetooth / USB path: this never calls Supabase or the shared cloud queue. */
object LocalReceiptPrinter {
    suspend fun print(context: Context, thermalBytes: ByteArray): Result<Unit> {
        return runCatching {
            require(thermalBytes.isNotEmpty()) { "Receipt data is missing." }
            val prefs = context.getSharedPreferences("motopos_settings", Context.MODE_PRIVATE)
            val address = prefs.getString("printer_address", null)
                ?: error("No directly paired receipt printer selected. Go to Settings > Receipt printer.")
            val name = prefs.getString("printer_name", "Receipt printer") ?: "Receipt printer"
            val transport = prefs.getString("printer_transport", "bluetooth") ?: "bluetooth"

            if (transport == "usb" && !UsbReceiptPrinter.hasPermission(context, address)) {
                UsbReceiptPrinter.requestPermission(context, address)
                error("Allow USB printer access, then retry this locally saved receipt.")
            }

            val printer: ReceiptPrinter = if (transport == "usb") {
                UsbReceiptPrinter(context)
            } else {
                BluetoothReceiptPrinter(context)
            }
            try {
                printer.connect(PrinterDevice(name, address, transport)).getOrThrow()
                printer.printReceipt(thermalBytes).getOrThrow()
            } finally {
                printer.disconnect()
            }
        }
    }
}
