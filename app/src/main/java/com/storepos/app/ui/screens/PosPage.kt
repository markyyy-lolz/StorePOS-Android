package com.storepos.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.RetailRepository
import com.storepos.app.data.PayMongoRepository
import com.storepos.app.data.RetailOpsRepository
import com.storepos.app.data.local.OfflineStore
import com.storepos.app.display.CustomerDisplayController
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import com.storepos.app.printing.BluetoothReceiptPrinter
import com.storepos.app.printing.PrinterDevice
import com.storepos.app.printing.PdfReceiptLine
import com.storepos.app.printing.ReceiptPdfExporter
import com.storepos.app.printing.ReceiptPrinter
import com.storepos.app.printing.UsbReceiptPrinter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private data class PendingPayMongoSale(
    val sessionId: String,
    val qrImageUrl: String,
    val expiresAt: String?,
    val requestId: String,
    val saleClientKey: String,
    val customerId: String?,
    val soldCart: List<CartLine>,
    val receiptCart: List<CartLine>,
    val discount: Double,
    val managerPin: String?,
    val charges: List<RetailCharge>,
    val dueDate: String?,
    val amount: Double,
    val livemode: Boolean
)

private fun decodePayMongoQr(value: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val encoded = value?.substringAfter("base64,", value)?.trim().orEmpty()
    if (encoded.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

@Composable
fun PosPage(context: ShopContext, entitlements: PlanEntitlements) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var motorcycles by remember { mutableStateOf<List<Motorcycle>>(emptyList()) }
    var settings by remember { mutableStateOf(ShopSettings(shopId = context.shop.id)) }
    var shifts by remember { mutableStateOf<List<CashierShift>>(emptyList()) }
    var heldSales by remember { mutableStateOf<List<HeldSale>>(emptyList()) }
    var retailSerials by remember { mutableStateOf<List<RetailSerial>>(emptyList()) }
    var retailPromos by remember { mutableStateOf<List<RetailPromo>>(emptyList()) }
    var retailFavorites by remember { mutableStateOf<List<RetailFavorite>>(emptyList()) }
    var paymongoIntegration by remember { mutableStateOf<PayMongoIntegration?>(null) }

    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkout by remember { mutableStateOf(false) }
    var retailQuote by remember { mutableStateOf<RetailQuote?>(null) }
    var preparingCheckout by remember { mutableStateOf(false) }
    var serialCheckoutProduct by remember { mutableStateOf<Product?>(null) }
    var quantityProduct by remember { mutableStateOf<Product?>(null) }
    var priceProduct by remember { mutableStateOf<Product?>(null) }
    var cartEditorOpen by remember { mutableStateOf(false) }
    var unknownBarcode by remember { mutableStateOf<String?>(null) }
    var holdOpen by remember { mutableStateOf(false) }
    var recallOpen by remember { mutableStateOf(false) }
    var startShiftOpen by remember { mutableStateOf(false) }

    var lastSale by remember { mutableStateOf<Sale?>(null) }
    var lastSaleTraining by remember { mutableStateOf(false) }
    var lastReceiptCart by remember { mutableStateOf<List<CartLine>>(emptyList()) }
    var lastPayments by remember { mutableStateOf<List<CheckoutPayment>>(emptyList()) }
    var lastReceiptToken by remember { mutableStateOf<String?>(null) }
    var receiptPrintedOnce by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    var printMessage by remember { mutableStateOf<String?>(null) }

    var pendingPayMongo by remember { mutableStateOf<PendingPayMongoSale?>(null) }
    var paymongoFinalizeError by remember { mutableStateOf<String?>(null) }
    var paymongoPaymentReceived by remember { mutableStateOf(false) }
    var paymongoRetryNonce by remember { mutableIntStateOf(0) }
    var paymongoCancelBusy by remember { mutableStateOf(false) }
    var paymongoCancelMessage by remember { mutableStateOf<String?>(null) }

    var offlineMode by remember { mutableStateOf(false) }
    var customerDisplayEnabled by remember { mutableStateOf(false) }
    var offlineQueued by remember { mutableStateOf<Double?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    var syncingOffline by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val prefs = remember { androidContext.getSharedPreferences("motopos_settings", 0) }
    val trainingMode = remember(context.shop.id) {
        prefs.getBoolean("training_mode_" + context.shop.id, false)
    }
    val offlineStore = remember { OfflineStore(androidContext) }
    val customerDisplay = remember { CustomerDisplayController(androidContext) }
    var pendingReceiptPdf by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    val saveReceiptPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val pending = pendingReceiptPdf
        pendingReceiptPdf = null
        if (uri != null && pending != null) {
            runCatching { ReceiptPdfExporter.save(androidContext, uri, pending.second) }
                .onSuccess { printMessage = "Receipt PDF saved. You can reopen and print it from Files anytime." }
                .onFailure { error = "Unable to save receipt PDF: " + (it.localizedMessage ?: "Storage error") }
        }
    }

    fun completedReceiptPdf(sale: Sale): ByteArray =
        ReceiptPdfExporter.render(
            shop = context.shop,
            sale = sale,
            settings = settings,
            lines = lastReceiptCart.map { line ->
                PdfReceiptLine(
                    name = line.product.name,
                    sku = line.product.sku,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal
                )
            },
            payments = lastPayments,
            cashierLabel = StoreRepository.currentUserEmail() ?: context.member.role,
            digitalReceiptUrl = lastReceiptToken?.let {
                "https://markyyy-lolz.github.io/StorePOS-Web/#/receipt/" + it
            },
            paperWidth = prefs.getInt("paper_width", settings.printerPaperWidthMm)
        )

    val scanTone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 75) }
    val vibrator = remember { androidContext.getSystemService(Vibrator::class.java) }
    DisposableEffect(Unit) {
        onDispose {
            scanTone.release()
            customerDisplay.dismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (
            prefs.getBoolean("customer_display_auto", false) &&
            customerDisplay.hasExternalDisplay()
        ) {
            customerDisplayEnabled = true
        }
    }

    LaunchedEffect(
        customerDisplayEnabled,
        cart,
        lastSale,
        lastReceiptToken,
        pendingPayMongo,
        paymongoFinalizeError,
        paymongoPaymentReceived,
        context.shop.name
    ) {
        if (customerDisplayEnabled) {
            val pendingQr = pendingPayMongo?.takeIf {
                !paymongoPaymentReceived && paymongoFinalizeError == null
            }
            val receiptUrl = lastReceiptToken?.let {
                "https://markyyy-lolz.github.io/StorePOS-Web/#/receipt/" + it
            }
            val shown = customerDisplay.show(
                shopName = context.shop.name,
                cart = cart,
                completedSale = lastSale,
                paymentQrImage = pendingQr?.qrImageUrl,
                paymentAmount = pendingQr?.amount,
                receiptUrl = receiptUrl
            )
            if (!shown) {
                customerDisplayEnabled = false
                error = "No external customer display detected. Connect an HDMI / presentation display and try again."
            }
        } else {
            customerDisplay.dismiss()
        }
    }

    fun scanSuccessFeedback() {
        scanTone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        vibrator?.vibrate(
            VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    fun submitScannedCode(rawCode: String) {
        val code = rawCode.trim()
        if (code.isBlank()) return
        val product = products.firstOrNull {
            it.barcode.equals(code, ignoreCase = true) || it.sku.equals(code, ignoreCase = true)
        }
        if (product != null) {
            if (product.isWeighed) quantityProduct = product
            else cart = addLine(cart, product)
            scanSuccessFeedback()
            query = ""
            unknownBarcode = null
            error = null
        } else {
            query = code
            unknownBarcode = code
            error = "Barcode / SKU not found. Create it now or scan another item: " + code
        }
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        submitScannedCode(result.contents.orEmpty())
    }

    fun scanBarcode() {
        barcodeLauncher.launch(
            ScanOptions()
                .setPrompt("Scan product barcode")
                .setBeepEnabled(true)
                .setOrientationLocked(true)
        )
    }

    suspend fun refresh() {
        try {
            coroutineScope {
                val p = async { StoreRepository.products(context.shop.id) }
                val c = async { StoreRepository.customers(context.shop.id) }
                val m = async { StoreRepository.motorcycles(context.shop.id) }
                val s = async { StoreRepository.shopSettings(context.shop.id) }
                val sh = async { StoreRepository.cashierShifts(context.shop.id) }
                val h = async { StoreRepository.heldSales(context.shop.id) }
                val rs = async { RetailRepository.serials(context.shop.id) }
                val rp = async { RetailRepository.promos(context.shop.id) }
                val rf = async { RetailRepository.favorites(context.shop.id) }
                products = p.await()
                customers = c.await()
                motorcycles = m.await()
                settings = s.await()
                shifts = sh.await()
                heldSales = h.await()
                retailSerials = rs.await()
                retailPromos = rp.await()
                retailFavorites = rf.await()
            }
            offlineStore.saveProducts(context.shop.id, products)
            offlineStore.saveCustomers(context.shop.id, customers)
            offlineStore.saveMotorcycles(context.shop.id, motorcycles)

            val open = shifts.firstOrNull { it.userId == context.userId && it.status == "open" }
            prefs.edit().apply {
                if (open != null) putString("open_shift_" + context.shop.id, open.id)
                else remove("open_shift_" + context.shop.id)
            }.apply()
            offlineMode = false
        } catch (cloudError: Throwable) {
            products = offlineStore.loadProducts(context.shop.id)
            customers = offlineStore.loadCustomers(context.shop.id)
            motorcycles = offlineStore.loadMotorcycles(context.shop.id)
            offlineMode = true
            if (products.isEmpty()) throw cloudError
        }
        paymongoIntegration = if (
            !offlineMode &&
            entitlements.valid &&
            entitlements.features.contains("paymongo_payments")
        ) {
            runCatching { PayMongoRepository.integration(context.shop.id) }.getOrNull()
        } else null
        pendingCount = offlineStore.pendingSales().size
    }

    fun requiresRetailOnline(lines: List<CartLine> = cart): Boolean =
        lines.any { line ->
            val base = line.product.retailParentId?.let { parentId -> products.firstOrNull { it.id == parentId } }
            line.product.retailParentId != null ||
                line.product.wholesaleMin > 0 ||
                line.product.isWeighed ||
                line.product.batchTracked ||
                line.product.serialTracked ||
                base?.batchTracked == true ||
                base?.serialTracked == true
        } || retailPromos.any { it.isActive }

    fun beginCheckout() {
        if (cart.isEmpty() || preparingCheckout) return
        if (trainingMode) {
            retailQuote = null
            checkout = true
            return
        }
        val serialLine = cart.firstOrNull { line ->
            val base = line.product.retailParentId?.let { parentId -> products.firstOrNull { it.id == parentId } } ?: line.product
            if (!base.serialTracked) false
            else {
                val required = line.quantity * if (line.product.retailParentId != null) line.product.retailMultiplier else 1.0
                required % 1.0 != 0.0 || line.serials.size != required.toInt()
            }
        }
        if (serialLine != null) {
            serialCheckoutProduct = serialLine.product
            return
        }
        if (offlineMode) {
            if (requiresRetailOnline()) {
                error = "Packs, promos, weighed items, batches and serialized stock require StorePOS Cloud checkout. Reconnect before completing this cart."
                return
            }
            retailQuote = null
            checkout = true
            return
        }

        scope.launch {
            preparingCheckout = true
            error = null
            val previewCart = cart.map { it.copy(unitPriceOverride = null) }
            runCatching { RetailRepository.quote(context.shop.id, previewCart) }
                .onSuccess { quote ->
                    var adjustedSubtotal = quote.subtotal
                    cart.filter { it.unitPriceOverride != null }.forEach { line ->
                        val quotedLine = quote.items.firstOrNull { it.productId == line.product.id }
                        if (quotedLine != null) {
                            adjustedSubtotal += (line.unitPriceOverride ?: quotedLine.unitPrice) * line.quantity - quotedLine.lineTotal
                        }
                    }
                    retailQuote = quote.copy(subtotal = adjustedSubtotal)
                    checkout = true
                }
                .onFailure { error = StoreRepository.userMessage(it) }
            preparingCheckout = false
        }
    }

    suspend fun syncOfflineSales() {
        syncingOffline = true
        val pending = offlineStore.pendingSales()
        pending.forEach { queued ->
            runCatching { StoreRepository.completeOfflineSale(queued.payload) }
                .onSuccess { offlineStore.removePendingSale(queued.id) }
                .onFailure { offlineStore.setPendingError(queued.id, StoreRepository.userMessage(it)) }
        }
        pendingCount = offlineStore.pendingSales().size
        syncingOffline = false
        if (pendingCount == 0 && pending.isNotEmpty()) {
            error = null
            runCatching { refresh() }
        }
    }

    suspend fun printSale(
        sale: Sale,
        soldCart: List<CartLine>,
        payments: List<CheckoutPayment>,
        receiptToken: String? = null
    ): String {
        val paperWidth = prefs.getInt("paper_width", settings.printerPaperWidthMm)
        val payload = BluetoothReceiptPrinter.saleReceipt(
            shopName = context.shop.name,
            sale = sale,
            cart = soldCart,
            paperWidth = paperWidth,
            receiptHeader = settings.receiptHeader,
            receiptFooter = settings.receiptFooter,
            payments = payments,
            cashierLabel = if (settings.receiptShowCashier) (StoreRepository.currentUserEmail() ?: context.member.role) else null,
            openCashDrawer = settings.cashDrawerEnabled && payments.any { it.method == "cash" },
            digitalReceiptUrl = receiptToken?.let {
                "https://markyyy-lolz.github.io/StorePOS-Web/#/receipt/" + it
            },
            shopAddress = context.shop.address,
            shopPhone = context.shop.phone,
            shopTin = context.shop.tin,
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
        if (com.storepos.app.printing.SharedPrintRepository.mode(androidContext) != "direct") {
            val printerQueue = com.storepos.app.printing.SharedPrintRepository
            val jobKey = printerQueue.requestKey(sale.id,receiptPrintedOnce)
            val deviceId = printerQueue.deviceId(androidContext)
            val kind = if (receiptPrintedOnce) "reprint" else "sale"
            return runCatching {
                val jobId = printerQueue.enqueue(
                    context.shop.id, deviceId, kind, payload, jobKey, sale.id, sale.saleNumber
                )
                "Receipt sent to shared queue (" + jobId.take(8) + ")."
            }.getOrElse { failure ->
                runCatching {
                    com.storepos.app.printing.SharedPrintOutbox.save(
                        androidContext,
                        com.storepos.app.printing.PendingSharedPrint(
                            context.shop.id, deviceId, jobKey, kind, sale.id, sale.saleNumber,
                            android.util.Base64.encodeToString(payload,android.util.Base64.NO_WRAP)
                        )
                    )
                    "Receipt saved to local print queue. StorePOS will send it after reconnection."
                }.getOrElse { saveError ->
                    "Print request not saved. Sale remains recorded; manually print once connected. " +
                        (saveError.message ?: failure.message ?: "Try again.")
                }
            }
        }
        val address = prefs.getString("printer_address", null)
            ?: return "No receipt printer selected."
        val name = prefs.getString("printer_name", "Receipt printer") ?: "Receipt printer"
        val transport = prefs.getString("printer_transport", "bluetooth") ?: "bluetooth"
        if (transport == "usb" && !UsbReceiptPrinter.hasPermission(androidContext, address)) {
            UsbReceiptPrinter.requestPermission(androidContext, address)
            return "USB printer permission required. Approve it, then print again."
        }
        val printer: ReceiptPrinter = if (transport == "usb") UsbReceiptPrinter(androidContext)
            else BluetoothReceiptPrinter(androidContext)
        val result = printer.connect(PrinterDevice(name, address, transport)).fold(
            onSuccess = { printer.printReceipt(payload) },
            onFailure = { Result.failure(it) }
        )
        printer.disconnect()
        return result.fold(
            onSuccess = { "Receipt sent to printer." },
            onFailure = { it.message ?: "Unable to print receipt." }
        )
    }

    LaunchedEffect(context.shop.id) {
        while (true) {
            if (com.storepos.app.printing.SharedPrintRepository.mode(androidContext) != "direct") {
                runCatching {
                    com.storepos.app.printing.SharedPrintOutbox.flush(androidContext,context.shop.id)
                }
            }
            delay(30_000)
        }
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        if (!offlineMode && pendingCount > 0) runCatching { syncOfflineSales() }
        loading = false
    }

    LaunchedEffect(pendingPayMongo?.sessionId, paymongoRetryNonce) {
        val pending = pendingPayMongo ?: return@LaunchedEffect
        paymongoFinalizeError = null

        while (pendingPayMongo?.sessionId == pending.sessionId) {
            val remote = runCatching {
                PayMongoRepository.syncCheckout(context.shop.id, pending.sessionId)
            }.getOrNull()

            if (remote == null) {
                delay(2000)
                continue
            }

            when (remote.status.lowercase()) {
                "paid" -> {
                    paymongoPaymentReceived = true
                    if (kotlin.math.abs((remote.paidAmount ?: pending.amount) - pending.amount) > 0.01) {
                        paymongoFinalizeError =
                            "PayMongo verified a different amount. Expected " + money(pending.amount) +
                                ", received " + money(remote.paidAmount ?: 0.0) +
                                ". Do not finalize this sale until reviewed."
                        return@LaunchedEffect
                    }

                    val storeMethod = when (remote.paymentMethod?.lowercase()) {
                        "gcash" -> "gcash"
                        "paymaya", "maya" -> "maya"
                        "card" -> "card"
                        else -> "other"
                    }
                    val reference = "PayMongo " + (
                        remote.paymentId
                            ?: remote.payMongoCheckoutSessionId
                            ?: remote.clientReference
                    )
                    val verifiedPayment = CheckoutPayment(
                        method = storeMethod,
                        amount = pending.amount,
                        referenceNumber = reference
                    )

                    delay(650)
                    val checkoutResult = runCatching {
                        RetailRepository.finalizePayMongoCheckout(
                            shopId = context.shop.id,
                            cart = pending.soldCart,
                            customerId = pending.customerId,
                            discount = pending.discount,
                            payments = listOf(verifiedPayment),
                            managerPin = pending.managerPin,
                            charges = pending.charges,
                            dueDate = pending.dueDate,
                            clientKey = pending.saleClientKey
                        )
                    }

                    checkoutResult.onSuccess { result ->
                        lastReceiptToken = result.receiptToken
                        lastReceiptCart = pending.receiptCart
                        lastPayments = listOf(verifiedPayment)
                        lastSale = result.sale
                        lastSaleTraining = false
                        receiptPrintedOnce = false
                        cart = emptyList()
                        pendingPayMongo = null
                        paymongoFinalizeError = null
                        paymongoPaymentReceived = false
                        error = null
                        refresh()

                        if (settings.autoPrintReceipt && (prefs.getString("printer_address", null) != null || com.storepos.app.printing.SharedPrintRepository.mode(androidContext) != "direct")) {
                            printing = true
                            printMessage = printSale(result.sale, lastReceiptCart, lastPayments, lastReceiptToken)
                            if (printMessage?.startsWith("Receipt sent") == true) receiptPrintedOnce = true
                            printing = false
                        }
                    }.onFailure { failure ->
                        paymongoFinalizeError =
                            "Payment is VERIFIED by PayMongo, but StorePOS could not finalize the sale: " +
                                StoreRepository.userMessage(failure) +
                                ". Fix the issue and tap Retry finalization. The same transaction key is reused to prevent duplicate sales."
                    }
                    return@LaunchedEffect
                }
                "failed", "expired", "cancelled" -> {
                    runCatching {
                        RetailRepository.releasePayMongoStock(context.shop.id, pending.saleClientKey)
                    }
                    paymongoFinalizeError =
                        "PayMongo checkout is " + remote.status.uppercase() +
                            ". Reserved stock was released and no StorePOS sale was created."
                    return@LaunchedEffect
                }
                else -> delay(2000)
            }
        }
    }

    if (loading) return LoadingView("Opening POS…")

    val visible = products.filter {
        it.isActive && (query.isBlank() ||
            it.name.contains(query, true) ||
            it.sku.contains(query, true) ||
            it.barcode?.contains(query, true) == true ||
            it.brand?.contains(query, true) == true)
    }
    val favoriteProducts = retailFavorites.mapNotNull { favorite -> products.firstOrNull { it.id == favorite.productId && it.isActive } }
    val openShift = shifts.firstOrNull { it.userId == context.userId && it.status == "open" }
    val cachedOpenShift = prefs.getString("open_shift_" + context.shop.id, null)
    val registerOpen = trainingMode ||
        !settings.requireCashierShift ||
        openShift != null ||
        (offlineMode && !cachedOpenShift.isNullOrBlank())
    val cashierRole = context.member.role.lowercase() == "cashier"

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(
            "Point of Sale",
            when {
                trainingMode -> "TRAINING MODE • practice only • no real sale, stock or cloud posting"
                offlineMode -> "Offline mode • cached catalog • queued transactions sync when online"
                else -> "Production register • scan, hold, split tender & receipt printing"
            },
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (customerDisplayEnabled) {
                                customerDisplayEnabled = false
                            } else if (customerDisplay.hasExternalDisplay()) {
                                customerDisplayEnabled = true
                            } else {
                                error = "No external customer display detected. Connect an HDMI / presentation display and try again."
                            }
                        }
                    ) {
                        Icon(
                            if (customerDisplayEnabled) Icons.Rounded.DesktopWindows else Icons.Rounded.ScreenshotMonitor,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (customerDisplayEnabled) "Display on" else "Customer display")
                    }
                    if (offlineMode) {
                        AssistChip(onClick = {}, label = { Text("OFFLINE") })
                    } else {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (registerOpen) "REGISTER OPEN" else "SHIFT REQUIRED") },
                            leadingIcon = {
                                Icon(
                                    if (registerOpen) Icons.Rounded.PointOfSale else Icons.Rounded.LockClock,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    if (heldSales.isNotEmpty()) {
                        OutlinedButton(onClick = { recallOpen = true }) {
                            Icon(Icons.Rounded.Restore, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Held " + heldSales.size)
                        }
                    }
                    if (pendingCount > 0) {
                        Button(
                            onClick = { scope.launch { syncOfflineSales() } },
                            enabled = !syncingOffline && registerOpen
                        ) {
                            Text(if (syncingOffline) "Syncing…" else "Sync " + pendingCount)
                        }
                    }
                }
            }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (trainingMode) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.School, null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TRAINING MODE", fontWeight = FontWeight.Black)
                        Text(
                            "Checkout is simulated locally. Inventory, sales, cash drawer and reports will not change.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (favoriteProducts.isNotEmpty()) {
            MotoCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Quick favorites", fontWeight = FontWeight.Black)
                            Text(
                                "Tap a saved item to add it to the cart.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favoriteProducts, key = { it.id }) { product ->
                            AssistChip(
                                onClick = {
                                    if (product.isWeighed) quantityProduct = product
                                    else cart = addLine(cart, product)
                                },
                                label = {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            money(product.sellingPrice),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Rounded.AddShoppingCart, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }

        if (!registerOpen) {
            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LockClock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cashier shift required", fontWeight = FontWeight.Black)
                        Text(
                            "Open a register shift before checkout so cash, refunds, variance, and Z-report stay accountable.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { startShiftOpen = true }, enabled = !offlineMode) {
                        Text("Start shift")
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth >= 800.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProductList(
                        visible,
                        query,
                        { query = it },
                        {
                            if (it.isWeighed) quantityProduct = it
                            else cart = addLine(cart, it)
                        },
                        { submitScannedCode(it) },
                        { scanBarcode() },
                        Modifier.weight(1.3f)
                    )
                    CartCard(
                        cart = cart,
                        onQty = { p, d -> cart = changeQty(cart, p, d) },
                        onOverride = { priceProduct = it },
                        onHold = { if (cart.isNotEmpty()) holdOpen = true },
                        onCheckout = { beginCheckout() },
                        checkoutEnabled = cart.isNotEmpty() && registerOpen && !preparingCheckout,
                        holdEnabled = !trainingMode && settings.allowHoldSales && cart.isNotEmpty() && !offlineMode,
                        modifier = Modifier.weight(.9f)
                    )
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductList(
                        visible,
                        query,
                        { query = it },
                        {
                            if (it.isWeighed) quantityProduct = it
                            else cart = addLine(cart, it)
                        },
                        { submitScannedCode(it) },
                        { scanBarcode() },
                        Modifier.weight(1f)
                    )
                    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 3.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cart.sumOf { it.quantity }.toInt().toString() + " item(s)")
                                Text(money(cart.sumOf { it.lineTotal }), fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { cartEditorOpen = true }, enabled = cart.isNotEmpty()) {
                                Text("Cart")
                            }
                            if (!trainingMode && settings.allowHoldSales && !offlineMode) {
                                OutlinedButton(onClick = { holdOpen = true }, enabled = cart.isNotEmpty()) {
                                    Text("Hold")
                                }
                            }
                            Button(
                                onClick = { beginCheckout() },
                                enabled = cart.isNotEmpty() && registerOpen
                            ) {
                                Text("Checkout")
                            }
                        }
                    }
                }
            }
        }
    }

    quantityProduct?.let { product ->
        QuantityEntryDialog(
            product = product,
            onDismiss = { quantityProduct = null },
            onApply = { quantity ->
                cart = setProductQuantity(cart, product, quantity)
                quantityProduct = null
            }
        )
    }

    serialCheckoutProduct?.let { product ->
        val line = cart.firstOrNull { it.product.id == product.id }
        val base = product.retailParentId?.let { parentId -> products.firstOrNull { it.id == parentId } } ?: product
        val required = line?.let {
            (it.quantity * if (product.retailParentId != null) product.retailMultiplier else 1.0).toInt()
        } ?: 0
        SerialSelectionDialog(
            product = product,
            baseProduct = base,
            required = required,
            available = retailSerials.filter { it.productId == base.id && it.status == "available" },
            selected = line?.serials.orEmpty(),
            onDismiss = { serialCheckoutProduct = null },
            onApply = { selected ->
                cart = cart.map {
                    if (it.product.id == product.id) it.copy(serials = selected) else it
                }
                serialCheckoutProduct = null
                beginCheckout()
            }
        )
    }

    priceProduct?.let { product ->
        val line = cart.firstOrNull { it.product.id == product.id }
        PriceOverrideDialog(
            product = product,
            currentPrice = line?.unitPrice ?: product.sellingPrice,
            onDismiss = { priceProduct = null },
            onApply = { newPrice ->
                cart = cart.map {
                    if (it.product.id == product.id) {
                        it.copy(
                            unitPriceOverride = if (kotlin.math.abs(newPrice - product.sellingPrice) < .01)
                                null else newPrice
                        )
                    } else it
                }
                priceProduct = null
            }
        )
    }

    if (cartEditorOpen) {
        CartEditorDialog(
            cart = cart,
            onDismiss = { cartEditorOpen = false },
            onQty = { product, delta -> cart = changeQty(cart, product, delta) },
            onOverride = { product ->
                cartEditorOpen = false
                priceProduct = product
            }
        )
    }

    unknownBarcode?.let { code ->
        QuickCreateScannedProductDialog(
            code = code,
            onDismiss = { unknownBarcode = null },
            onSave = { name, cost, price, openingStock ->
                scope.launch {
                    error = null
                    runCatching {
                        StoreRepository.addProduct(
                            ProductInsert(
                                shopId = context.shop.id,
                                sku = code,
                                barcode = code,
                                name = name,
                                costPrice = cost,
                                sellingPrice = price,
                                stockQuantity = openingStock,
                                reorderLevel = 5.0
                            )
                        )
                    }.onSuccess { created ->
                        products = (products + created).distinctBy { it.id }
                        if (created.isWeighed) quantityProduct = created else cart = addLine(cart, created)
                        scanSuccessFeedback()
                        query = ""
                        unknownBarcode = null
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (startShiftOpen) {
        PosStartShiftDialog(
            onDismiss = { startShiftOpen = false },
            onStart = { opening ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.startCashierShift(context.shop.id, opening) }
                        .onSuccess { shift ->
                            prefs.edit().putString("open_shift_" + context.shop.id, shift.id).apply()
                            startShiftOpen = false
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (holdOpen) {
        HoldCartDialog(
            onDismiss = { holdOpen = false },
            onHold = { label, notes ->
                scope.launch {
                    error = null
                    runCatching {
                        StoreRepository.holdSale(
                            shopId = context.shop.id,
                            cart = cart,
                            label = label,
                            notes = notes
                        )
                    }.onSuccess {
                        cart = emptyList()
                        holdOpen = false
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (recallOpen) {
        RecallHeldSaleDialog(
            heldSales = heldSales,
            onDismiss = { recallOpen = false },
            onRecall = { held ->
                val restored = held.items.mapNotNull { item ->
                    products.firstOrNull { it.id == item.productId }?.let {
                        CartLine(it, item.quantity, item.unitPriceOverride)
                    }
                }
                if (restored.isEmpty()) {
                    error = "This held sale no longer has available products."
                } else {
                    cart = restored
                    recallOpen = false
                    scope.launch {
                        runCatching { StoreRepository.deleteHeldSale(held.id) }
                            .onFailure { error = StoreRepository.userMessage(it) }
                        refresh()
                    }
                }
            },
            onDelete = { held ->
                scope.launch {
                    runCatching { StoreRepository.deleteHeldSale(held.id) }
                        .onSuccess { refresh() }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    lastSale?.let { sale ->
        AlertDialog(
            onDismissRequest = { lastSale = null },
            icon = {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    if (lastSaleTraining) "Training Transaction Complete" else "Transaction Complete",
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (lastSaleTraining)
                            "Practice checkout complete. No sale, payment, stock movement or report entry was created."
                        else
                            "Payment, retail pricing, and inventory were committed successfully."
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sale number")
                                Text(sale.saleNumber, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total")
                                Text(
                                    money(sale.totalAmount),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (sale.changeDue != null && sale.changeDue > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Change")
                                    Text(money(sale.changeDue), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    lastReceiptToken?.let { token ->
                        val link = "https://markyyy-lolz.github.io/StorePOS-Web/#/receipt/" + token
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Digital receipt: " + link,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Available for 3 days from purchase.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    printMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                val clearCompletedSale = {
                    lastSale = null
                    lastSaleTraining = false
                    lastReceiptCart = emptyList()
                    lastPayments = emptyList()
                    lastReceiptToken = null
                    receiptPrintedOnce = false
                    printMessage = null
                }
                if (lastSaleTraining) {
                    Button(onClick = clearCompletedSale, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                } else {
                    val printerAddress = prefs.getString("printer_address", null)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching { completedReceiptPdf(sale) }
                                        .onSuccess { bytes ->
                                            pendingReceiptPdf = sale.saleNumber to bytes
                                            saveReceiptPdf.launch(ReceiptPdfExporter.fileName(sale.saleNumber))
                                        }
                                        .onFailure { error = "PDF error: " + (it.localizedMessage ?: "Cannot export receipt") }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        ReceiptPdfExporter.share(androidContext, completedReceiptPdf(sale), sale.saleNumber)
                                    }.onFailure { error = "Share failed: " + (it.localizedMessage ?: "Cannot share PDF") }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Share PDF")
                            }
                        }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val isReprint = receiptPrintedOnce
                                printing = true
                                printMessage = null
                                scope.launch {
                                    val result = printSale(sale, lastReceiptCart, lastPayments, lastReceiptToken)
                                    printMessage = result
                                    if (result.startsWith("Receipt sent")) {
                                        if (isReprint) {
                                            runCatching {
                                                RetailOpsRepository.recordReceiptReprint(
                                                    context.shop.id,
                                                    sale.id,
                                                    "Android POS completed-sale reprint"
                                                )
                                            }.onFailure {
                                                printMessage = result + " Reprint audit could not sync: " + StoreRepository.userMessage(it)
                                            }
                                        }
                                        receiptPrintedOnce = true
                                    }
                                    printing = false
                                }
                            },
                            enabled = (printerAddress != null || com.storepos.app.printing.SharedPrintRepository.mode(androidContext) != "direct") && !printing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Print, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (printing) "Printing…" else "Print receipt")
                        }
                        Button(onClick = clearCompletedSale, modifier = Modifier.weight(1f)) {
                            Text("Done")
                        }
                    }
                    }
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    offlineQueued?.let { provisional ->
        AlertDialog(
            onDismissRequest = { offlineQueued = null },
            icon = { Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(44.dp)) },
            title = { Text("Sale saved offline", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stored on this device. It becomes a final StorePOS transaction only after cloud sync succeeds.")
                    Text("Provisional total: " + money(provisional), fontWeight = FontWeight.Bold)
                    Text(
                        "Pending sync: " + pendingCount + ". Keep this cashier shift open until all queued sales sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { Button(onClick = { offlineQueued = null }) { Text("Done") } }
        )
    }

    pendingPayMongo?.let { pending ->
        val verifiedButNotFinalized = paymongoFinalizeError?.startsWith("Payment is VERIFIED") == true
        val terminalError = paymongoFinalizeError != null && !verifiedButNotFinalized
        val qrBitmap = remember(pending.sessionId, pending.qrImageUrl) {
            decodePayMongoQr(pending.qrImageUrl)
        }
        var remainingSeconds by remember(pending.sessionId, pending.expiresAt) {
            mutableIntStateOf(
                pending.expiresAt?.let {
                    runCatching {
                        ((Instant.parse(it).toEpochMilli() - System.currentTimeMillis() + 999L) / 1000L)
                            .coerceAtLeast(0L)
                            .toInt()
                    }.getOrDefault(0)
                } ?: 0
            )
        }

        LaunchedEffect(pending.sessionId, pending.expiresAt, paymongoPaymentReceived) {
            while (
                pendingPayMongo?.sessionId == pending.sessionId &&
                !paymongoPaymentReceived &&
                !terminalError
            ) {
                remainingSeconds = pending.expiresAt?.let {
                    runCatching {
                        ((Instant.parse(it).toEpochMilli() - System.currentTimeMillis() + 999L) / 1000L)
                            .coerceAtLeast(0L)
                            .toInt()
                    }.getOrDefault(0)
                } ?: remainingSeconds
                if (remainingSeconds <= 0) break
                delay(1000)
            }
        }

        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    if (paymongoPaymentReceived) Icons.Rounded.CheckCircle else Icons.Rounded.QrCode2,
                    null,
                    modifier = Modifier.size(42.dp)
                )
            },
            title = {
                Text(
                    when {
                        paymongoPaymentReceived -> "Payment received"
                        verifiedButNotFinalized -> "PayMongo verified • finalize sale"
                        terminalError -> "QR Ph payment stopped"
                        else -> "Scan to pay"
                    },
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        money(pending.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )

                    when {
                        paymongoPaymentReceived -> {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Payment received. Finalizing sale…",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        !verifiedButNotFinalized && !terminalError -> {
                            if (qrBitmap != null) {
                                Surface(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "QR Ph payment code",
                                        modifier = Modifier
                                            .size(280.dp)
                                            .padding(12.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "QR image could not be rendered. Cancel this QR and generate a new one.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Text(
                                "Scan with GCash, Maya, or a QR Ph-enabled banking app.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Waiting for payment…")
                            }

                            if (remainingSeconds > 0) {
                                Text(
                                    "QR expires in %d:%02d".format(
                                        remainingSeconds / 60,
                                        remainingSeconds % 60
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        "Reference: " + pending.requestId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AssistChip(
                        onClick = {},
                        label = {
                            Text(if (pending.livemode) "PAYMONGO LIVE MODE" else "PAYMONGO TEST MODE")
                        }
                    )

                    paymongoFinalizeError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    paymongoCancelMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!paymongoPaymentReceived && !verifiedButNotFinalized && !terminalError) {
                        Text(
                            "The sale is not completed until StorePOS receives a verified PayMongo payment status.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    verifiedButNotFinalized -> {
                        Button(onClick = {
                            paymongoFinalizeError = null
                            paymongoRetryNonce += 1
                        }) { Text("Retry finalization") }
                    }
                    terminalError -> {
                        Button(onClick = {
                            error = paymongoFinalizeError
                            pendingPayMongo = null
                            paymongoFinalizeError = null
                            paymongoPaymentReceived = false
                        }) { Text("Close") }
                    }
                    !paymongoPaymentReceived -> {
                        TextButton(
                            onClick = {
                                paymongoCancelMessage = null
                                paymongoRetryNonce += 1
                            },
                            enabled = !paymongoCancelBusy
                        ) {
                            Text(if (paymongoCancelBusy) "Checking…" else "Check now")
                        }
                    }
                }
            },
            dismissButton = {
                if (!paymongoPaymentReceived && !verifiedButNotFinalized && !terminalError) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (paymongoCancelBusy) return@launch
                                paymongoCancelBusy = true
                                paymongoCancelMessage = "Checking the latest payment status before cancelling…"
                                error = null

                                suspend fun closeAsCancelled() {
                                    runCatching {
                                        RetailRepository.releasePayMongoStock(
                                            context.shop.id,
                                            pending.saleClientKey
                                        )
                                    }
                                    pendingPayMongo = null
                                    paymongoFinalizeError = null
                                    paymongoPaymentReceived = false
                                    paymongoCancelMessage = null
                                }

                                fun handleStillPaid() {
                                    paymongoCancelMessage =
                                        "Payment was already confirmed. StorePOS will finalize this sale instead of cancelling it."
                                    paymongoRetryNonce += 1
                                }

                                val beforeCancel = runCatching {
                                    PayMongoRepository.syncCheckout(
                                        context.shop.id,
                                        pending.sessionId
                                    )
                                }.getOrNull()

                                when (beforeCancel?.status?.lowercase()) {
                                    "paid" -> handleStillPaid()
                                    "failed", "expired", "cancelled" -> closeAsCancelled()
                                    else -> {
                                        val cancelledRemote = runCatching {
                                            PayMongoRepository.cancelCheckout(
                                                context.shop.id,
                                                pending.sessionId
                                            )
                                        }.getOrNull()

                                        if (cancelledRemote != null) {
                                            when (cancelledRemote.status.lowercase()) {
                                                "paid" -> handleStillPaid()
                                                "failed", "expired", "cancelled" -> closeAsCancelled()
                                                else -> {
                                                    paymongoCancelMessage =
                                                        "Cancellation was requested. StorePOS is confirming the final QR status before closing it."
                                                    paymongoRetryNonce += 1
                                                }
                                            }
                                        } else {
                                            val afterCancel = runCatching {
                                                PayMongoRepository.syncCheckout(
                                                    context.shop.id,
                                                    pending.sessionId
                                                )
                                            }.getOrNull()

                                            when (afterCancel?.status?.lowercase()) {
                                                "paid" -> handleStillPaid()
                                                "failed", "expired", "cancelled" -> closeAsCancelled()
                                                else -> {
                                                    paymongoCancelMessage =
                                                        "Cancellation could not be confirmed yet. This QR remains active while StorePOS keeps checking. Do not create another QR for this sale."
                                                    paymongoRetryNonce += 1
                                                }
                                            }
                                        }
                                    }
                                }

                                paymongoCancelBusy = false
                            }
                        },
                        enabled = !paymongoCancelBusy
                    ) {
                        Text(if (paymongoCancelBusy) "Cancelling…" else "Cancel QR")
                    }
                }
            }
        )
    }

    if (checkout) {
        PosCheckoutDialog(
            cart = cart,
            customers = customers,
            motorcycles = motorcycles,
            settings = settings,
            cashierRole = cashierRole,
            hasPriceOverride = cart.any { it.hasPriceOverride },
            offlineMode = offlineMode,
            paymongoAvailable = !trainingMode && !offlineMode && paymongoIntegration?.enabled == true &&
                entitlements.valid && entitlements.features.contains("paymongo_payments"),
            pricingSubtotal = if (offlineMode) null else retailQuote?.subtotal,
            pricingNote = if (!offlineMode && retailQuote != null) "StorePOS Retail pricing is active • wholesale and eligible promos are already applied." else null,
            onDismiss = { checkout = false },
            onComplete = { customerId, bikeId, payments, discount, tax, managerPin, charges, dueDate ->
                scope.launch {
                    error = null
                    val soldCart = cart
                    val receiptCart = if (!offlineMode && retailQuote != null) {
                        soldCart.map { line ->
                            retailQuote!!.items.firstOrNull { it.productId == line.product.id }?.let { quoted ->
                                line.copy(unitPriceOverride = quoted.unitPrice)
                            } ?: line
                        }
                    } else soldCart

                    if (trainingMode) {
                        val subtotal = receiptCart.sumOf { it.lineTotal }
                        val chargesTotal = charges.sumOf { it.amount }
                        val total = (subtotal - discount + tax + chargesTotal).coerceAtLeast(0.0)
                        val tendered = payments.sumOf { it.tendered ?: it.amount }
                        val trainingSale = Sale(
                            id = "training-" + UUID.randomUUID().toString(),
                            shopId = context.shop.id,
                            saleNumber = "TRAINING-" + System.currentTimeMillis().toString().takeLast(8),
                            customerId = customerId,
                            cashierId = context.userId,
                            subtotal = subtotal,
                            discountAmount = discount,
                            taxAmount = tax,
                            totalAmount = total,
                            amountTendered = tendered,
                            changeDue = (tendered - total).coerceAtLeast(0.0),
                            status = "completed",
                            createdAt = Instant.now().toString()
                        )
                        lastReceiptToken = null
                        lastReceiptCart = receiptCart
                        lastPayments = payments
                        lastSale = trainingSale
                        lastSaleTraining = true
                        receiptPrintedOnce = false
                        printMessage = "Training transaction only • nothing was saved to StorePOS Cloud."
                        cart = emptyList()
                        checkout = false
                        return@launch
                    }

                    val paymongoPayment = payments.singleOrNull()?.takeIf { it.method == "paymongo" }
                    if (paymongoPayment != null) {
                        if (offlineMode || paymongoIntegration?.enabled != true) {
                            error = "PayMongo automatic payments require an active online PayMongo connection for this shop."
                            return@launch
                        }

                        val requestId = "SP-" + UUID.randomUUID().toString()
                        val saleClientKey = UUID.randomUUID().toString()

                        try {
                            RetailRepository.holdPayMongoStock(
                                shopId = context.shop.id,
                                clientKey = saleClientKey,
                                cart = soldCart,
                                holdSeconds = 900
                            )
                        } catch (failure: Throwable) {
                            error = "Cannot start QR Ph payment: " + StoreRepository.userMessage(failure) +
                                ". Check stock availability before asking the customer to pay."
                            return@launch
                        }

                        val started = try {
                            PayMongoRepository.createCheckout(
                                shopId = context.shop.id,
                                amount = paymongoPayment.amount,
                                requestId = requestId,
                                description = context.shop.name + " StorePOS sale",
                                flow = "qrph",
                                expirySeconds = 300
                            )
                        } catch (failure: Throwable) {
                            runCatching {
                                RetailRepository.releasePayMongoStock(context.shop.id, saleClientKey)
                            }
                            error = "Unable to generate QR Ph: " + StoreRepository.userMessage(failure)
                            return@launch
                        }

                        val qrImage = started.qrImageUrl
                        if (qrImage.isNullOrBlank()) {
                            runCatching {
                                RetailRepository.releasePayMongoStock(context.shop.id, saleClientKey)
                            }
                            error = "PayMongo did not return a QR Ph image. Reserved stock was released. Retry the payment."
                            return@launch
                        }

                        paymongoCancelBusy = false
                        paymongoCancelMessage = null
                        pendingPayMongo = PendingPayMongoSale(
                            sessionId = started.id,
                            qrImageUrl = qrImage,
                            expiresAt = started.expiresAt,
                            requestId = started.clientReference,
                            saleClientKey = saleClientKey,
                            customerId = customerId,
                            soldCart = soldCart,
                            receiptCart = receiptCart,
                            discount = discount,
                            managerPin = managerPin,
                            charges = charges,
                            dueDate = dueDate,
                            amount = paymongoPayment.amount,
                            livemode = started.livemode
                        )
                        paymongoFinalizeError = null
                        paymongoPaymentReceived = false
                        checkout = false
                        return@launch
                    }

                    runCatching {
                        if (offlineMode) {
                            StoreRepository.completeSaleV3(
                                shopId = context.shop.id,
                                customerId = customerId,
                                motorcycleId = bikeId,
                                jobOrderId = null,
                                cart = soldCart,
                                discount = discount,
                                tax = tax,
                                payments = payments,
                                managerPin = managerPin
                            ) to null
                        } else {
                            RetailRepository.checkout(
                                shopId = context.shop.id,
                                cart = soldCart,
                                customerId = customerId,
                                discount = discount,
                                payments = payments,
                                managerPin = managerPin,
                                charges = charges,
                                dueDate = dueDate
                            ).let { it.sale to it.receiptToken }
                        }
                    }.onSuccess { result ->
                        val sale = result.first
                        lastReceiptToken = result.second
                        lastReceiptCart = receiptCart
                        lastPayments = payments
                        lastSale = sale
                        lastSaleTraining = false
                        receiptPrintedOnce = false
                        cart = emptyList()
                        checkout = false
                        refresh()

                        if (settings.autoPrintReceipt && (prefs.getString("printer_address", null) != null || com.storepos.app.printing.SharedPrintRepository.mode(androidContext) != "direct")) {
                            printing = true
                            printMessage = printSale(sale, lastReceiptCart, payments, lastReceiptToken)
                            if (printMessage?.startsWith("Receipt sent") == true) receiptPrintedOnce = true
                            printing = false
                        }
                    }.onFailure { failure ->
                        val lower = failure.message.orEmpty().lowercase()
                        val networkFailure = offlineMode ||
                            "network" in lower ||
                            "timeout" in lower ||
                            "unable to resolve host" in lower ||
                            "failed to connect" in lower ||
                            "connectexception" in lower

                        if (networkFailure) {
                            if (managerPin != null) {
                                error = "Manager-approved transactions cannot be queued offline. Reconnect and retry."
                                return@onFailure
                            }
                            if (payments.any { it.method in listOf("store_credit","credit") }) {
                                error = "Store credit and customer credit require an online connection."
                                return@onFailure
                            }

                            if (charges.isNotEmpty() || dueDate != null || requiresRetailOnline(soldCart)) {
                                error = "This retail transaction needs an online connection and cannot be queued offline."
                                return@onFailure
                            }

                            val provisionalTotal = soldCart.sumOf { it.lineTotal } - discount + tax
                            val first = payments.first()
                            offlineStore.enqueueSale(
                                OfflineSalePayload(
                                    clientKey = UUID.randomUUID().toString(),
                                    shopId = context.shop.id,
                                    customerId = customerId,
                                    motorcycleId = bikeId,
                                    discountAmount = discount,
                                    taxAmount = tax,
                                    amountTendered = first.tendered,
                                    paymentMethod = first.method,
                                    referenceNumber = first.referenceNumber,
                                    items = soldCart.map {
                                        SaleRpcItem(it.product.id, it.quantity, it.unitPriceOverride)
                                    },
                                    payments = payments
                                )
                            )

                            products = products.map { product ->
                                val line = soldCart.firstOrNull { it.product.id == product.id }
                                if (line != null && product.trackStock) {
                                    product.copy(
                                        stockQuantity = (product.stockQuantity - line.quantity).coerceAtLeast(0.0)
                                    )
                                } else product
                            }
                            offlineStore.saveProducts(context.shop.id, products)

                            offlineQueued = provisionalTotal
                            pendingCount = offlineStore.pendingSales().size
                            cart = emptyList()
                            checkout = false
                            offlineMode = true
                            error = null
                        } else {
                            error = StoreRepository.userMessage(failure)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun QuantityEntryDialog(
    product: Product,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit
) {
    var value by remember(product.id) { mutableStateOf("1") }
    val quantity = value.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter quantity • " + product.name, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This is a weighed product. Decimal quantities are allowed, for example 0.25 kg or 1.5 kg.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value,
                    { value = it },
                    label = { Text("Quantity (" + product.unit + ")") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Line amount: " + money(quantity * product.sellingPrice), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = { onApply(quantity) }, enabled = quantity > 0) { Text("Add to cart") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SerialSelectionDialog(
    product: Product,
    baseProduct: Product,
    required: Int,
    available: List<RetailSerial>,
    selected: List<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    val picked = remember(product.id, required) {
        mutableStateListOf<String>().apply { addAll(selected.filter { serial -> available.any { it.serialNumber == serial } }.take(required)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select serial numbers", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name)
                if (baseProduct.id != product.id) {
                    Text(
                        "Pack uses base SKU " + baseProduct.name + " • " + qtyForPos(product.retailMultiplier) + " base unit(s) each.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Select exactly " + required + " serial(s). " + available.size + " available.",
                    color = if (available.size < required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(available, key = { it.id }) { serial ->
                        val checked = serial.serialNumber in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) picked.remove(serial.serialNumber)
                                    else if (picked.size < required) picked.add(serial.serialNumber)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) {
                                        if (picked.size < required && serial.serialNumber !in picked) picked.add(serial.serialNumber)
                                    } else picked.remove(serial.serialNumber)
                                }
                            )
                            Text(serial.serialNumber, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(picked.toList()) }, enabled = required > 0 && picked.size == required) {
                Text("Use serials")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun setProductQuantity(cart: List<CartLine>, product: Product, quantity: Double): List<CartLine> {
    if (quantity <= 0) return cart
    return if (cart.none { it.product.id == product.id }) {
        cart + CartLine(product = product, quantity = quantity)
    } else {
        cart.map { if (it.product.id == product.id) it.copy(quantity = quantity, serials = emptyList()) else it }
    }
}

private fun qtyForPos(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private fun addLine(cart: List<CartLine>, product: Product): List<CartLine> =
    if (cart.none { it.product.id == product.id }) cart + CartLine(product)
    else cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }

private fun changeQty(cart: List<CartLine>, product: Product, delta: Double): List<CartLine> =
    cart.mapNotNull {
        if (it.product.id != product.id) it
        else (it.quantity + delta).let { q -> if (q <= 0) null else it.copy(quantity = q) }
    }

@Composable
private fun ProductList(
    products: List<Product>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: (Product) -> Unit,
    onSubmit: (String) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier
) {
    // A Bluetooth HID / keyboard-wedge scanner types into the focused field.
    // Focus it when entering POS, and retain focus between successful scans.
    // Never claim the physical Bluetooth device is connected here.
    val scanFocusRequester = remember { FocusRequester() }
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    // The scanner can deliver its final character and Enter in the same
    // frame, before Compose has recomposed the query String parameter.
    val pendingHidInput = remember { mutableStateOf(query) }
    SideEffect { pendingHidInput.value = query }

    fun submitKeyboardScan(code: String) {
        pendingHidInput.value = ""
        scannedCodeOrNull(code)?.let(onSubmit)
        scanFocusRequester.requestFocus()
        softwareKeyboard?.hide()
    }

    LaunchedEffect(Unit) {
        scanFocusRequester.requestFocus()
        softwareKeyboard?.hide()
    }

    LaunchedEffect(query, products) {
        val candidate = query.trim()
        if (candidate.length >= 6) {
            // Scanner types quickly; the debounce also supports models whose
            // Enter/Tab suffix has been disabled in the scanner's firmware.
            delay(200)
            val exactMatch = products.any {
                it.barcode.equals(candidate, ignoreCase = true) ||
                    it.sku.equals(candidate, ignoreCase = true)
            }
            if (exactMatch) submitKeyboardScan(candidate)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { rawValue ->
                val update = parseHidScannerText(rawValue)
                pendingHidInput.value = update.searchText
                onQuery(update.searchText)
                update.completedCode?.let(::submitKeyboardScan)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(scanFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    // Some Bluetooth scanners send ENTER, TAB or ESCAPE after
                    // the barcode. Consume both Down and Up so these suffixes
                    // cannot jump focus or navigate away from the POS register.
                    val suffix = keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.Tab ||
                        keyEvent.key == Key.Escape
                    if (suffix) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            submitKeyboardScan(pendingHidInput.value)
                        }
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            placeholder = { Text("Search name, SKU, barcode or brand") },
            supportingText = { Text("Bluetooth HID: scan repeatedly · Enter/Tab suffix supported") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = {
                            pendingHidInput.value = ""
                            onQuery("")
                            scanFocusRequester.requestFocus()
                            softwareKeyboard?.hide()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear scanner search")
                        }
                    }
                    IconButton(onClick = onScan) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan with camera")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
        if (products.isEmpty()) {
            EmptyView("No products found", "Add products in Inventory or change the search.", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { p ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            onAdd(p)
                            pendingHidInput.value = ""
                            onQuery("")
                            scanFocusRequester.requestFocus()
                            softwareKeyboard?.hide()
                        },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(p.brand, p.sku).joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Stock " + p.stockQuantity + " " + p.unit,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(money(p.sellingPrice), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartCard(
    cart: List<CartLine>,
    onQty: (Product, Double) -> Unit,
    onOverride: (Product) -> Unit,
    onHold: () -> Unit,
    onCheckout: () -> Unit,
    checkoutEnabled: Boolean,
    holdEnabled: Boolean,
    modifier: Modifier
) {
    MotoCard(modifier.fillMaxHeight()) {
        Text("Current sale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (cart.isEmpty()) {
            EmptyView("Cart is empty", "Choose a product to start a transaction.", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(cart, key = { it.product.id }) { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.product.name)
                            Text(
                                money(line.lineTotal) + " • " + money(line.unitPrice) + " each" +
                                    if (line.hasPriceOverride) " • OVERRIDE" else "",
                                color = if (line.hasPriceOverride) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onOverride(line.product) }) {
                            Icon(Icons.Rounded.PriceChange, contentDescription = "Override price")
                        }
                        IconButton(onClick = { onQty(line.product, -1.0) }) {
                            Icon(Icons.Rounded.Remove, null)
                        }
                        Text(line.quantity.toInt().toString(), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onQty(line.product, 1.0) }) {
                            Icon(Icons.Rounded.Add, null)
                        }
                    }
                    HorizontalDivider()
                }
            }
            Text(
                money(cart.sumOf { it.lineTotal }),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onHold,
                    enabled = holdEnabled,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Rounded.PauseCircle, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Hold")
                }
                Button(
                    onClick = onCheckout,
                    enabled = checkoutEnabled,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("Checkout")
                }
            }
        }
    }
}

@Composable
private fun PriceOverrideDialog(
    product: Product,
    currentPrice: Double,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit
) {
    var price by remember(product.id) { mutableStateOf(currentPrice.toString()) }
    val value = price.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price override") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name, fontWeight = FontWeight.Bold)
                Text("Regular price: " + money(product.sellingPrice))
                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text("Selling price for this sale") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Cashier overrides require Manager PIN approval at checkout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { value?.let(onApply) }, enabled = value != null && value >= 0) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(product.sellingPrice) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CartEditorDialog(
    cart: List<CartLine>,
    onDismiss: () -> Unit,
    onQty: (Product, Double) -> Unit,
    onOverride: (Product) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Current sale") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cart, key = { it.product.id }) { line ->
                    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.product.name, fontWeight = FontWeight.Bold)
                                Text(
                                    money(line.unitPrice) + " each" +
                                        if (line.hasPriceOverride) " • OVERRIDE" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onOverride(line.product) }) {
                                Icon(Icons.Rounded.PriceChange, contentDescription = "Override price")
                            }
                            IconButton(onClick = { onQty(line.product, -1.0) }) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                            }
                            Text(line.quantity.toInt().toString(), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onQty(line.product, 1.0) }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun QuickCreateScannedProductDialog(
    code: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double) -> Unit
) {
    var name by remember(code) { mutableStateOf("") }
    var cost by remember(code) { mutableStateOf("") }
    var price by remember(code) { mutableStateOf("") }
    var stock by remember(code) { mutableStateOf("1") }
    val parsedCost = cost.toDoubleOrNull() ?: 0.0
    val parsedPrice = price.toDoubleOrNull()
    val parsedStock = stock.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unknown barcode", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Barcode / SKU: $code")
                Text(
                    "Create this product without leaving the register. It will be added to the cart after saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = { Text("Cost") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Selling") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Opening stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), parsedCost, parsedPrice ?: 0.0, parsedStock ?: 0.0) },
                enabled = name.isNotBlank() && parsedPrice != null && parsedPrice >= 0.0 &&
                    parsedStock != null && parsedStock >= 0.0
            ) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PosStartShiftDialog(
    onDismiss: () -> Unit,
    onStart: (Double) -> Unit
) {
    var opening by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open cashier shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Count the starting cash in the drawer before accepting transactions.")
                OutlinedTextField(
                    opening,
                    { opening = it },
                    label = { Text("Opening cash") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(opening.toDoubleOrNull() ?: 0.0) },
                enabled = (opening.toDoubleOrNull() ?: -1.0) >= 0
            ) { Text("Open register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HoldCartDialog(
    onDismiss: () -> Unit,
    onHold: (String?, String?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hold / park sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The cart will be saved without changing stock until checkout.")
                OutlinedTextField(
                    label,
                    { label = it },
                    label = { Text("Label / customer name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onHold(label.trim().ifBlank { null }, notes.trim().ifBlank { null }) }) {
                Text("Hold sale")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecallHeldSaleDialog(
    heldSales: List<HeldSale>,
    onDismiss: () -> Unit,
    onRecall: (HeldSale) -> Unit,
    onDelete: (HeldSale) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Held sales") },
        text = {
            if (heldSales.isEmpty()) {
                Text("No held sales.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(heldSales, key = { it.id }) { held ->
                        Surface(
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(held.label ?: "Held sale", fontWeight = FontWeight.Bold)
                                    Text(
                                        held.items.sumOf { it.quantity }.toInt().toString() + " item(s)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    held.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                TextButton(onClick = { onRecall(held) }) { Text("Recall") }
                                IconButton(onClick = { onDelete(held) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete held sale")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
