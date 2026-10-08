package com.storepos.app.printing

import android.content.ClipData
import android.content.Context
import android.content.Intent
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.CheckoutPayment
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.Sale
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ShopSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.roundToInt

data class PdfReceiptLine(
    val name: String,
    val sku: String?,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double
)

/**
 * Pixel-independent, receipt-width PDF of the SAME ESC/POS payload that the
 * Bluetooth/USB printer receives.  No second PDF receipt template exists.
 */
object ReceiptPdfExporter {
    fun fileName(receiptNumber: String): String =
        "StorePOS-Receipt-" + receiptNumber.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) + ".pdf"

    fun render(
        shop: Shop,
        sale: Sale,
        settings: ShopSettings,
        lines: List<PdfReceiptLine>,
        payments: List<CheckoutPayment> = emptyList(),
        cashierLabel: String? = null,
        digitalReceiptUrl: String? = null,
        paperWidth: Int = settings.printerPaperWidthMm
    ): ByteArray {
        require(lines.isNotEmpty()) { "Cannot export a PDF without the original sale items." }
        require(sale.shopId == shop.id) { "Receipt does not belong to this store." }
        require(paperWidth == 58 || paperWidth == 80) { "Choose 58mm or 80mm receipt paper." }

        // Adapter only: the exact official thermal receipt formatter handles
        // line order, prices, tax, discounts, tender, footer and QR codes.
        val cart = lines.mapIndexed { index, entry ->
            require(entry.name.isNotBlank()) { "Receipt item name cannot be empty." }
            CartLine(
                product = Product(
                    id = "pdf-item-" + index,
                    shopId = shop.id,
                    sku = entry.sku.orEmpty(),
                    name = entry.name,
                    sellingPrice = entry.unitPrice
                ),
                quantity = entry.quantity,
                unitPriceOverride = entry.unitPrice
            )
        }

        val samePrinterBytes = BluetoothReceiptPrinter.saleReceipt(
            shopName = shop.name,
            sale = sale,
            cart = cart,
            paperWidth = paperWidth,
            receiptHeader = settings.receiptHeader,
            receiptFooter = settings.receiptFooter,
            payments = payments,
            cashierLabel = if (settings.receiptShowCashier) cashierLabel else null,
            openCashDrawer = false, // Nonvisual hardware command.
            digitalReceiptUrl = digitalReceiptUrl,
            shopAddress = shop.address,
            shopPhone = shop.phone,
            shopTin = shop.tin,
            receiptTitle = settings.receiptTitle,
            showAddress = settings.receiptShowAddress,
            showPhone = settings.receiptShowPhone,
            showTin = settings.receiptShowTin,
            showReceiptNumber = settings.receiptShowReceiptNumber,
            showDate = settings.receiptShowDate,
            showPaymentReference = settings.receiptShowPaymentReference,
            showDigitalQr = settings.receiptShowDigitalQr,
            compactMode = settings.receiptCompactMode,
            sectionOrder = settings.receiptSectionOrder
        )
        return renderThermalBytes(samePrinterBytes, paperWidth)
    }

    /** An exact thermal stream may also be sent directly for parity tests. */
    internal fun renderThermalBytes(bytes: ByteArray, paperWidth: Int): ByteArray {
        require(paperWidth == 58 || paperWidth == 80) { "Unsupported receipt paper width." }
        val elements = ThermalReceiptDecoder.decode(bytes)
        require(elements.isNotEmpty()) { "Receipt was empty." }

        // PDF pages have the selected physical roll width, not A4 dimensions.
        val pageWidth = (paperWidth * 72f / 25.4f).roundToInt()
        val columns = if (paperWidth == 58) 32 else 48
        val margin = if (paperWidth == 58) 7f else 8f
        val contentWidth = pageWidth - 2 * margin
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            color = Color.BLACK
            textSize = 9f
            textSize *= contentWidth / (columns * measureText("0"))
        }
        val charWidth = paint.measureText("0")
        val lineHeight = ceil(paint.textSize * 1.2f)
        val maxPageHeight = 4096f // Long receipts become multiple narrow pages.
        val qrHints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
        val qrSizes = elements.map { element ->
            if (element is ThermalReceiptElement.QrCode) {
                val matrix = QRCodeWriter().encode(element.content, BarcodeFormat.QR_CODE, 1, 1, qrHints)
                (matrix.width * (if (paperWidth == 58) 4f else 6f) * 72f / 203f)
                    .coerceAtMost(contentWidth)
            } else 0f
        }

        fun blockHeight(index: Int): Float =
            if (elements[index] is ThermalReceiptElement.Text) lineHeight else qrSizes[index] + lineHeight

        val pages = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var usedHeight = 0f
        elements.indices.forEach { index ->
            val block = blockHeight(index)
            if (current.isNotEmpty() && usedHeight + block + margin * 2 > maxPageHeight) {
                pages += current
                current = mutableListOf()
                usedHeight = 0f
            }
            current += index
            usedHeight += block
        }
        if (current.isNotEmpty()) pages += current

        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, indices ->
                val height = (indices.sumOf { blockHeight(it).toDouble() }.toFloat() + margin * 2)
                    .roundToInt().coerceAtLeast(40)
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, height, pageIndex + 1).create()
                )
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                var y = margin
                indices.forEach { index ->
                    when (val element = elements[index]) {
                        is ThermalReceiptElement.Text -> {
                            // No reflow, extra fields, extra footers or altered spacing.
                            canvas.drawText(element.line, margin, y + paint.textSize, paint)
                            y += lineHeight
                        }
                        is ThermalReceiptElement.QrCode -> {
                            val matrix = QRCodeWriter().encode(
                                element.content, BarcodeFormat.QR_CODE, 1, 1, qrHints
                            )
                            val side = qrSizes[index]
                            val module = side / matrix.width
                            val left = (pageWidth - side) / 2f
                            val pixels = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                            for (row in 0 until matrix.height) {
                                for (col in 0 until matrix.width) {
                                    if (matrix.get(col, row)) {
                                        canvas.drawRect(
                                            left + col * module, y + row * module,
                                            left + (col + 1) * module, y + (row + 1) * module, pixels
                                        )
                                    }
                                }
                            }
                            y += side + lineHeight
                        }
                    }
                }
                document.finishPage(page)
            }
            return ByteArrayOutputStream().use { stream ->
                document.writeTo(stream)
                stream.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    fun save(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
            ?: error("Cannot open the selected PDF destination.")
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
        context.startActivity(Intent.createChooser(intent, "Share thermal-size receipt PDF"))
    }

    fun print(context: Context, bytes: ByteArray, receiptNumber: String, paperWidth: Int = 80) {
        require(paperWidth == 58 || paperWidth == 80)
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
        val paperWidthMils = (paperWidth * 1000f / 25.4f).roundToInt()
        manager.print(
            fileName(receiptNumber), adapter,
            PrintAttributes.Builder()
                .setMediaSize(
                    PrintAttributes.MediaSize(
                        "STOREPOS_" + paperWidth + "MM",
                        paperWidth.toString() + "mm receipt roll",
                        paperWidthMils,
                        11000 // Service may substitute its supported roll paper height.
                    )
                )
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()
        )
    }
}
