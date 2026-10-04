package com.storepos.app.printing

data class PrinterDevice(
    val name: String,
    val address: String,
    val transport: String = "bluetooth"
)

interface ReceiptPrinter {
    suspend fun connect(device: PrinterDevice): Result<Unit>
    suspend fun printReceipt(payload: ByteArray): Result<Unit>
    suspend fun disconnect()
    val isConnected: Boolean
}
