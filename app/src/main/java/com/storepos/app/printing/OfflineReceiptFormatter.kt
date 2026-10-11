package com.storepos.app.printing

import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.CheckoutPayment
import com.storepos.app.data.model.Sale
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ShopSettings
import java.time.Instant
import java.util.Locale

data class ProvisionalReceipt(
    val number: String,
    val sale: Sale,
    val thermalBytes: ByteArray
)

/**
 * One thermal formatter for offline Bluetooth/USB printing and offline PDF.
 * Never emit the cloud receipt numbering or claim a transaction is synced.
 */
object OfflineReceiptFormatter {
    fun numberFor(clientKey: String): String {
        require(clientKey.isNotBlank()) { "An offline sale key is required" }
        return "OFF-" + clientKey.replace("-", "").take(12).uppercase(Locale.US)
    }

    fun create(
        shop: Shop,
        settings: ShopSettings,
        clientKey: String,
        cashierId: String,
        cart: List<CartLine>,
        payments: List<CheckoutPayment>,
        discount: Double,
        tax: Double,
        paperWidth: Int,
        cashierLabel: String?,
        footerFeedLines: Int = ReceiptCutSettings.DEFAULT_LINES,
        createdAt: String = Instant.now().toString()
    ): ProvisionalReceipt {
        require(cart.isNotEmpty()) { "Cannot print an empty cart." }
        require(payments.isNotEmpty() && payments.all { it.method == "cash" }) {
            "Only offline cash transactions can receive provisional receipts."
        }
        val subtotal = cart.sumOf { it.lineTotal }
        val total = (subtotal - discount + tax).coerceAtLeast(0.0)
        val tendered = payments.sumOf { it.tendered ?: it.amount }
        val sale = Sale(
            id = clientKey, shopId = shop.id, saleNumber = numberFor(clientKey),
            cashierId = cashierId, subtotal = subtotal,
            discountAmount = discount, taxAmount = tax, totalAmount = total,
            amountTendered = tendered, changeDue = (tendered - total).coerceAtLeast(0.0),
            status = "pending_sync", createdAt = createdAt
        )
        val note = listOfNotNull(
            "OFFLINE - PENDING SYNC",
            "NOT POSTED TO STOREPOS CLOUD",
            "Provisional reference only",
            "Final cloud sale no. issued after sync",
            settings.receiptFooter?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString("\n")
        val bytes = BluetoothReceiptPrinter.saleReceipt(
            shopName = shop.name,
            sale = sale,
            cart = cart,
            paperWidth = paperWidth,
            receiptHeader = settings.receiptHeader,
            receiptFooter = note,
            payments = payments,
            cashierLabel = if (settings.receiptShowCashier) cashierLabel else null,
            openCashDrawer = false, // Also safe when reprinting this same stored payload.
            digitalReceiptUrl = null, // No cloud token exists yet.
            shopAddress = shop.address,
            shopPhone = shop.phone,
            shopTin = shop.tin,
            receiptTitle = "OFFLINE CASH RECEIPT",
            showAddress = settings.receiptShowAddress,
            showPhone = settings.receiptShowPhone,
            showTin = settings.receiptShowTin,
            showReceiptNumber = true, // Always expose unique provisional ID.
            showDate = settings.receiptShowDate,
            showPaymentReference = settings.receiptShowPaymentReference,
            showDigitalQr = false,
            compactMode = settings.receiptCompactMode,
            sectionOrder = settings.receiptSectionOrder,
            footerFeedLines = footerFeedLines
        )
        return ProvisionalReceipt(sale.saleNumber, sale, bytes)
    }
}
