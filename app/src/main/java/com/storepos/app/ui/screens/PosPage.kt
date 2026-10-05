package com.storepos.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
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
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import com.storepos.app.printing.BluetoothReceiptPrinter
import com.storepos.app.printing.PrinterDevice
import com.storepos.app.printing.ReceiptPrinter
import com.storepos.app.printing.UsbReceiptPrinter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val amount: Double
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
    var holdOpen by remember { mutableStateOf(false) }
    var recallOpen by remember { mutableStateOf(false) }
    var startShiftOpen by remember { mutableStateOf(false) }

    var lastSale by remember { mutableStateOf<Sale?>(null) }
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

    var offlineMode by remember { mutableStateOf(false) }
    var offlineQueued by remember { mutableStateOf<Double?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    var syncingOffline by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val prefs = remember { androidContext.getSharedPreferences("motopos_settings", 0) }
    val offlineStore = remember { OfflineStore(androidContext) }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents?.trim().orEmpty()
        if (code.isNotBlank()) {
            val product = products.firstOrNull {
                it.barcode.equals(code, ignoreCase = true) || it.sku.equals(code, ignoreCase = true)
            }
            if (product != null) {
                if (product.isWeighed) quantityProduct = product
                else cart = addLine(cart, product)
                query = ""
                error = null
            } else {
                query = code
                error = "Barcode not found in inventory: " + code
            }
        }
    }

    fun scanBarcode() {
        barcodeLauncher.launch(
            ScanOptions()
                .setPrompt("Scan product barcode")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
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
        payments: List<CheckoutPayment>
    ): String {
        val address = prefs.getString("printer_address", null)
            ?: return "No receipt printer selected."
        val printerName = prefs.getString("printer_name", "Receipt printer") ?: "Receipt printer"
        val paperWidth = prefs.getInt("paper_width", settings.printerPaperWidthMm)
        val transport = prefs.getString("printer_transport", "bluetooth") ?: "bluetooth"

        if (transport == "usb" && !UsbReceiptPrinter.hasPermission(androidContext, address)) {
            UsbReceiptPrinter.requestPermission(androidContext, address)
            return "USB printer permission is required. Approve it in Android, then print again."
        }

        val printer: ReceiptPrinter = if (transport == "usb") {
            UsbReceiptPrinter(androidContext)
        } else {
            BluetoothReceiptPrinter(androidContext)
        }
        val result = printer.connect(PrinterDevice(printerName, address, transport)).fold(
            onSuccess = {
                printer.printReceipt(
                    BluetoothReceiptPrinter.saleReceipt(
                        shopName = context.shop.name,
                        sale = sale,
                        cart = soldCart,
                        paperWidth = paperWidth,
                        receiptHeader = settings.receiptHeader,
                        receiptFooter = settings.receiptFooter,
                        payments = payments,
                        cashierLabel = if (settings.receiptShowCashier) (StoreRepository.currentUserEmail() ?: context.member.role) else null,
                        openCashDrawer = settings.cashDrawerEnabled && payments.any { it.method == "cash" }
                    )
                )
            },
            onFailure = { Result.failure(it) }
        )
        printer.disconnect()
        return result.fold(
            onSuccess = { "Receipt sent to printer." },
            onFailure = { it.message ?: "Unable to print receipt." }
        )
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
                        RetailRepository.checkout(
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
                        receiptPrintedOnce = false
                        cart = emptyList()
                        pendingPayMongo = null
                        paymongoFinalizeError = null
                        paymongoPaymentReceived = false
                        error = null
                        refresh()

                        if (settings.autoPrintReceipt && prefs.getString("printer_address", null) != null) {
                            printing = true
                            printMessage = printSale(result.sale, lastReceiptCart, lastPayments)
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
                    paymongoFinalizeError =
                        "PayMongo checkout is " + remote.status.uppercase() +
                            ". No StorePOS sale was created."
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
    val registerOpen = !settings.requireCashierShift ||
        openShift != null ||
        (offlineMode && !cachedOpenShift.isNullOrBlank())
    val cashierRole = context.member.role.lowercase() == "cashier"

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(
            "Point of Sale",
            if (offlineMode)
                "Offline mode • cached catalog • queued transactions sync when online"
            else
                "Production register • scan, hold, split tender & receipt printing",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        holdEnabled = settings.allowHoldSales && cart.isNotEmpty() && !offlineMode,
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
                            if (settings.allowHoldSales && !offlineMode) {
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
            title = { Text("Transaction Complete", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Payment, retail pricing, and inventory were committed successfully.")
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
                        Text(
                            "Digital receipt: " + link,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    printMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                val printerAddress = prefs.getString("printer_address", null)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val isReprint = receiptPrintedOnce
                            printing = true
                            printMessage = null
                            scope.launch {
                                val result = printSale(sale, lastReceiptCart, lastPayments)
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
                        enabled = printerAddress != null && !printing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Print, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (printing) "Printing…" else "Print receipt")
                    }
                    Button(
                        onClick = {
                            lastSale = null
                            lastReceiptCart = emptyList()
                            lastPayments = emptyList()
                            lastReceiptToken = null
                            receiptPrintedOnce = false
                            printMessage = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Done") }
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
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Payments, null, modifier = Modifier.size(42.dp)) },
            title = {
                Text(
                    if (verifiedButNotFinalized) "PayMongo verified • finalize sale"
                    else "Waiting for PayMongo",
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (verifiedButNotFinalized)
                            "The customer payment is already verified. StorePOS has not created the sale yet."
                        else
                            "Complete payment in the secure PayMongo checkout. StorePOS is checking the signed webhook automatically."
                    )
                    Text("Amount: " + money(pending.amount), fontWeight = FontWeight.Bold)
                    Text(
                        "Reference: " + pending.requestId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    paymongoFinalizeError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!verifiedButNotFinalized) {
                        Text(
                            "Do not complete the cart manually while this payment is pending. When PayMongo confirms payment, the sale will be finalized automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                if (verifiedButNotFinalized) {
                    Button(onClick = {
                        paymongoFinalizeError = null
                        paymongoRetryNonce += 1
                    }) { Text("Retry finalization") }
                } else {
                    Button(onClick = {
                        runCatching {
                            androidContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(pending.checkoutUrl))
                            )
                        }.onFailure {
                            error = "Unable to open the PayMongo checkout URL on this device."
                        }
                    }) { Text("Open PayMongo") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPayMongo = null
                    paymongoFinalizeError = null
                    error = "Stopped waiting for PayMongo. Before retrying checkout, verify that the customer was not already charged."
                }) { Text("Stop waiting") }
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
            paymongoAvailable = !offlineMode && paymongoIntegration?.enabled == true &&
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

                    val paymongoPayment = payments.singleOrNull()?.takeIf { it.method == "paymongo" }
                    if (paymongoPayment != null) {
                        if (offlineMode || paymongoIntegration?.enabled != true) {
                            error = "PayMongo automatic payments require an active online PayMongo connection for this shop."
                            return@launch
                        }

                        val requestId = "SP-" + UUID.randomUUID().toString()
                        val saleClientKey = UUID.randomUUID().toString()
                        runCatching {
                            PayMongoRepository.createCheckout(
                                shopId = context.shop.id,
                                amount = paymongoPayment.amount,
                                requestId = requestId,
                                description = context.shop.name + " StorePOS sale",
                                flow = "qrph",
                                expirySeconds = 300
                            )
                        }.onSuccess { started ->
                            val qrImage = started.qrImageUrl
                                ?: throw IllegalStateException("PayMongo did not return a QR Ph image.")
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
                                amount = paymongoPayment.amount
                            )
                            paymongoFinalizeError = null
                            paymongoPaymentReceived = false
                            checkout = false
                        }.onFailure { failure ->
                            error = "Unable to generate QR Ph: " + StoreRepository.userMessage(failure)
                        }
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
                        receiptPrintedOnce = false
                        cart = emptyList()
                        checkout = false
                        refresh()

                        if (settings.autoPrintReceipt && prefs.getString("printer_address", null) != null) {
                            printing = true
                            printMessage = printSale(sale, lastReceiptCart, payments)
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
    onScan: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(
            query,
            onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search name, SKU, barcode or brand") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                IconButton(onClick = onScan) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan barcode")
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
                        Modifier.fillMaxWidth().clickable { onAdd(p) },
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
