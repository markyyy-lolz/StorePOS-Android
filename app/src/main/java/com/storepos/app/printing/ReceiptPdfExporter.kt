package com.storepos.app.printing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.storepos.app.data.model.Sale
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ShopSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Historical receipts use the saved transaction's price, not today's catalog price. */
data class PdfReceiptLine(
    val name: String,
    val sku: String?,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double
)

object ReceiptPdfExporter {
    fun fileName(receiptNumber: String): String =
        "StorePOS-Receipt-" + receiptNumber.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) + ".pdf"

    private fun php(value: Double) = "PHP " + String.format(Locale.US, "%,.2f", value)
    private fun qty(value: Double) =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    fun render(
        shop: Shop,
        sale: Sale,
        settings: ShopSettings,
        lines: List<PdfReceiptLine>,
        customerName: String? = null,
        paymentSummary: String? = null,
        duplicate: Boolean = true
    ): ByteArray {
        require(lines.isNotEmpty()) { "Cannot generate a receipt without its saved sale items." }
        require(sale.shopId == shop.id) { "This sale does not belong to the selected shop." }
        val document = PdfDocument()
        val width = 595
        val height = 842
        val margin = 42f
        val right = width - margin
        val usableWidth = width - margin * 2
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 41, 59); textSize = 11f }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(100, 116, 139); textSize = 10f }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heavy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 148, 136)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var pageNo = 0
        var y = margin
        fun finishPage() {
            canvas.drawLine(margin, height - 45f, right, height - 45f, rule)
            canvas.drawText("Powered by StorePOS  |  PDF receipt copy  |  Page " + pageNo, margin, height - 30f, muted)
            document.finishPage(page)
        }
        fun nextPage() {
            if (pageNo > 0) finishPage()
            pageNo++
            page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
            canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            y = margin
            if (pageNo > 1) {
                canvas.drawText((shop.name + "  |  " + sale.saleNumber + "  (continued)").take(75), margin, y + 13f, heavy)
                y += 30f
            }
        }
        fun ensure(required: Float) {
            if (y + required > height - 66f) nextPage()
        }
        fun line() {
            ensure(18f)
            canvas.drawLine(margin, y + 8f, right, y + 8f, rule)
            y += 20f
        }
        fun wrapped(value: String, paint: Paint = ink, maxWidth: Float = usableWidth, leading: Float = 15f) {
            var remainder = value.trim().replace(Regex("\\s+"), " ")
            while (remainder.isNotEmpty()) {
                var count = paint.breakText(remainder, true, maxWidth, null).coerceAtLeast(1)
                if (count < remainder.length) {
                    val whitespace = remainder.lastIndexOf(' ', count - 1)
                    if (whitespace > 0) count = whitespace
                }
                ensure(leading)
                canvas.drawText(remainder.take(count).trim(), margin, y + leading - 3f, paint)
                y += leading
                remainder = remainder.drop(count).trimStart()
            }
        }
        fun amount(label: String, value: String, bold: Boolean = false) {
            ensure(23f)
            canvas.drawText(label, margin, y + 13f, if (bold) heavy else ink)
            val paint = if (bold) accent else ink
            canvas.drawText(value, right - paint.measureText(value), y + 13f, paint)
            y += 23f
        }
        try {
            nextPage()
            wrapped(shop.name, heading, usableWidth, 25f)
            if (settings.receiptShowAddress) shop.address?.let { wrapped(it, muted) }
            if (settings.receiptShowPhone) shop.phone?.let { wrapped("Contact: " + it, muted) }
            if (settings.receiptShowTin) shop.tin?.let { wrapped("TIN: " + it, muted) }
            settings.receiptHeader?.takeIf { it.isNotBlank() }?.lineSequence()?.forEach { wrapped(it, muted) }
            y += 14f
            wrapped(settings.receiptTitle.ifBlank { "SALES RECEIPT" }, heavy)
            wrapped("NOT AN OFFICIAL TAX RECEIPT", muted)
            if (duplicate) wrapped("DUPLICATE COPY - PDF EXPORT", accent)
            line()
            wrapped("Receipt no.: " + sale.saleNumber, heavy)
            sale.createdAt?.let { wrapped("Sale date: " + it.replace("T", " ").take(19), muted) }
            customerName?.takeIf { it.isNotBlank() }?.let { wrapped("Customer: " + it) }
            wrapped("Status: " + sale.status.uppercase(Locale.US), muted)
            line()
            wrapped("PURCHASED ITEMS", heavy)
            y += 5f
            lines.forEach { item ->
                wrapped(item.name, heavy, usableWidth * 0.86f)
                if (!item.sku.isNullOrBlank()) wrapped("SKU: " + item.sku, muted)
                amount(qty(item.quantity) + " x " + php(item.unitPrice), php(item.lineTotal))
                y += 4f
            }
            line()
            amount("Subtotal", php(sale.subtotal))
            if (sale.discountAmount > 0) amount("Discount", "- " + php(sale.discountAmount))
            if (sale.taxAmount > 0) amount("Tax", php(sale.taxAmount))
            amount("TOTAL", php(sale.totalAmount), true)
            line()
            paymentSummary?.takeIf { it.isNotBlank() }?.let { wrapped("Payment: " + it) }
            sale.amountTendered?.let { amount("Amount tendered", php(it)) }
            sale.changeDue?.takeIf { it > 0 }?.let { amount("Change", php(it)) }
            y += 12f
            val footer = settings.receiptFooter?.takeIf { it.isNotBlank() }
            if (footer != null) footer.lineSequence().forEach { wrapped(it, muted) }
            else wrapped("Thank you for your purchase.", muted)
            wrapped("For refunds or adjustments, check the latest StorePOS sale record.", muted)
            finishPage()
            return ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    fun save(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
            ?: error("Unable to open the selected document location.")
    }

    fun share(context: Context, bytes: ByteArray, receiptNumber: String) {
        val directory = File(context.cacheDir, "receipt_pdfs").apply { mkdirs() }
        val file = File(directory, fileName(receiptNumber))
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "StorePOS receipt", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share receipt PDF"))
    }

    fun print(context: Context, bytes: ByteArray, receiptNumber: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return
                }
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(fileName(receiptNumber))
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build(), true
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal,
                callback: WriteResultCallback
            ) {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(destination).use { it.write(bytes) }
                    if (cancellationSignal.isCanceled) callback.onWriteCancelled()
                    else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (failure: Exception) {
                    callback.onWriteFailed(failure.localizedMessage ?: "Unable to print receipt PDF.")
                }
            }
        }
        manager.print(
            fileName(receiptNumber), adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
        )
    }
}
