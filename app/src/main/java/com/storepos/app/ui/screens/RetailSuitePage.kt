package com.storepos.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.RetailRepository
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.max

private enum class RetailTab(val title: String) {
    Overview("Overview"),
    Catalog("Catalog"),
    Stock("Stock"),
    Pricing("Pricing"),
    Promos("Promos"),
    Orders("Orders"),
    Cash("Cash & Close")
}

@Composable
fun RetailSuitePage(context: ShopContext) {
    var tab by remember { mutableStateOf(RetailTab.Overview) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var batches by remember { mutableStateOf<List<RetailBatch>>(emptyList()) }
    var serials by remember { mutableStateOf<List<RetailSerial>>(emptyList()) }
    var priceHistory by remember { mutableStateOf<List<RetailPriceHistory>>(emptyList()) }
    var schedules by remember { mutableStateOf<List<RetailPriceSchedule>>(emptyList()) }
    var promos by remember { mutableStateOf<List<RetailPromo>>(emptyList()) }
    var orders by remember { mutableStateOf<List<RetailOrder>>(emptyList()) }
    var supplierReturns by remember { mutableStateOf<List<RetailSupplierReturn>>(emptyList()) }
    var checklists by remember { mutableStateOf<List<RetailChecklist>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<RetailFavorite>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var receivables by remember { mutableStateOf<List<CustomerReceivable>>(emptyList()) }
    var analytics by remember { mutableStateOf(AnalyticsSummary()) }
    var insights by remember { mutableStateOf(RetailInsights()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    var configureProduct by remember { mutableStateOf<Product?>(null) }
    var csvImportOpen by remember { mutableStateOf(false) }
    var batchOpen by remember { mutableStateOf(false) }
    var serialOpen by remember { mutableStateOf(false) }
    var stockLossOpen by remember { mutableStateOf(false) }
    var supplierReturnOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var promoOpen by remember { mutableStateOf(false) }
    var reservationOpen by remember { mutableStateOf(false) }
    var creditOpen by remember { mutableStateOf(false) }
    var checklistKind by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val productById = remember(products) { products.associateBy { it.id } }
    val customerById = remember(customers) { customers.associateBy { it.id } }
    val supplierById = remember(suppliers) { suppliers.associateBy { it.id } }

    suspend fun refresh() {
        coroutineScope {
            val p = async { StoreRepository.products(context.shop.id) }
            val b = async { RetailRepository.batches(context.shop.id) }
            val s = async { RetailRepository.serials(context.shop.id) }
            val h = async { RetailRepository.priceHistory(context.shop.id) }
            val ps = async { RetailRepository.priceSchedules(context.shop.id) }
            val pr = async { RetailRepository.promos(context.shop.id) }
            val o = async { RetailRepository.orders(context.shop.id) }
            val sr = async { RetailRepository.supplierReturns(context.shop.id) }
            val cl = async { RetailRepository.checklists(context.shop.id) }
            val f = async { RetailRepository.favorites(context.shop.id) }
            val c = async { StoreRepository.customers(context.shop.id) }
            val sup = async { StoreRepository.suppliers(context.shop.id) }
            val rec = async { StoreRepository.receivables(context.shop.id) }
            val a = async { StoreRepository.analyticsSummary(context.shop.id, 1) }
            val i = async { RetailRepository.insights(context.shop.id, 30) }

            products = p.await()
            batches = b.await()
            serials = s.await()
            priceHistory = h.await()
            schedules = ps.await()
            promos = pr.await()
            orders = o.await()
            supplierReturns = sr.await()
            checklists = cl.await()
            favorites = f.await()
            customers = c.await()
            suppliers = sup.await()
            receivables = rec.await()
            analytics = a.await()
            insights = i.await()
        }
    }

    fun runAction(success: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            notice = null
            runCatching { block() }
                .onSuccess {
                    notice = success
                    runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                }
                .onFailure { error = StoreRepository.userMessage(it) }
            busy = false
        }
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading Retail Suite…")
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(
            "Retail Suite",
            "Advanced StorePOS controls for pricing, packs, promos, tracked stock, reservations, credit and closing."
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        busy = true
                        runCatching { refresh() }
                            .onFailure { error = StoreRepository.userMessage(it) }
                        busy = false
                    }
                },
                enabled = !busy
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }

        error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        notice?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        ScrollableTabRow(selectedTabIndex = tab.ordinal) {
            RetailTab.entries.forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = { Text(item.title) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                RetailTab.Overview -> RetailOverview(
                    analytics = analytics,
                    insights = insights,
                    batches = batches,
                    promos = promos,
                    orders = orders,
                    receivables = receivables,
                    onTab = { tab = it }
                )
                RetailTab.Catalog -> RetailCatalog(
                    products = products,
                    favorites = favorites,
                    busy = busy,
                    onConfigure = { configureProduct = it },
                    onImport = { csvImportOpen = true },
                    onFavorite = { product, remove ->
                        runAction(if (remove) "Removed from POS favorites." else "Added to POS favorites.") {
                            RetailRepository.setFavorite(
                                context.shop.id,
                                product.id,
                                favorites.size,
                                remove
                            )
                        }
                    }
                )
                RetailTab.Stock -> RetailStock(
                    batches = batches,
                    serials = serials,
                    supplierReturns = supplierReturns,
                    productById = productById,
                    supplierById = supplierById,
                    onBatch = { batchOpen = true },
                    onSerial = { serialOpen = true },
                    onLoss = { stockLossOpen = true },
                    onSupplierReturn = { supplierReturnOpen = true },
                    onCreditReturn = { item ->
                        runAction("Supplier return marked credited.") {
                            RetailRepository.markSupplierReturnCredited(context.shop.id, item.id)
                        }
                    }
                )
                RetailTab.Pricing -> RetailPricing(
                    schedules = schedules,
                    history = priceHistory,
                    productById = productById,
                    onSchedule = { scheduleOpen = true }
                )
                RetailTab.Promos -> RetailPromos(
                    promos = promos,
                    onCreate = { promoOpen = true }
                )
                RetailTab.Orders -> RetailOrders(
                    orders = orders,
                    receivables = receivables,
                    customerById = customerById,
                    onReserve = { reservationOpen = true },
                    onCredit = { creditOpen = true },
                    onCancel = { order ->
                        runAction("Reservation cancelled.") {
                            RetailRepository.cancelOrder(
                                context.shop.id,
                                order.id,
                                refundConfirmed = order.deposit <= 0
                            )
                        }
                    }
                )
                RetailTab.Cash -> RetailCashClose(
                    checklists = checklists,
                    onChecklist = { checklistKind = it }
                )
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    configureProduct?.let { product ->
        RetailProductDialog(
            product = product,
            baseProducts = products.filter { it.retailParentId == null && it.id != product.id },
            onDismiss = { configureProduct = null },
            onSave = { parentId, multiplier, wholesalePrice, wholesaleMin, group, variant, unit, weighed, batchTracked, serialTracked ->
                runAction("Retail product settings saved.") {
                    RetailRepository.configureProduct(
                        shopId = context.shop.id,
                        productId = product.id,
                        parentId = parentId,
                        multiplier = multiplier,
                        wholesalePrice = wholesalePrice,
                        wholesaleMin = wholesaleMin,
                        variantGroup = group,
                        variantName = variant,
                        unit = unit,
                        isWeighed = weighed,
                        batchTracked = batchTracked,
                        serialTracked = serialTracked
                    )
                }
                configureProduct = null
            }
        )
    }

    if (csvImportOpen) {
        CsvImportDialog(
            onDismiss = { csvImportOpen = false },
            onImport = { text ->
                runAction("CSV products imported.") {
                    val rows = RetailRepository.parseCsv(text)
                    RetailRepository.importProducts(context.shop.id, rows)
                }
                csvImportOpen = false
            }
        )
    }

    if (batchOpen) {
        BatchDialog(products.filter { it.retailParentId == null }, onDismiss = { batchOpen = false }) {
                product, batchNo, expiry, qty, receive ->
            runAction("Batch stock saved.") {
                RetailRepository.receiveBatch(context.shop.id, product.id, batchNo, expiry, qty, receive)
            }
            batchOpen = false
        }
    }

    if (serialOpen) {
        SerialDialog(products.filter { it.retailParentId == null }, onDismiss = { serialOpen = false }) { product, values ->
            runAction("Serial numbers registered.") {
                RetailRepository.registerSerials(context.shop.id, product.id, values)
            }
            serialOpen = false
        }
    }

    if (stockLossOpen) {
        StockLossDialog(
            products = products.filter { it.retailParentId == null },
            batches = batches,
            serials = serials,
            onDismiss = { stockLossOpen = false }
        ) { product, qty, reason, notes, batchId, selectedSerials ->
            runAction("Stock adjustment recorded.") {
                RetailRepository.recordStockLoss(
                    context.shop.id,
                    product.id,
                    qty,
                    reason,
                    notes,
                    batchId,
                    selectedSerials
                )
            }
            stockLossOpen = false
        }
    }

    if (supplierReturnOpen) {
        SupplierReturnDialog(
            suppliers = suppliers,
            products = products.filter { it.retailParentId == null },
            batches = batches,
            serials = serials,
            onDismiss = { supplierReturnOpen = false }
        ) { supplier, product, qty, credit, reason, batchId, selectedSerials ->
            runAction("Supplier return recorded and stock adjusted.") {
                RetailRepository.supplierReturn(
                    context.shop.id,
                    supplier.id,
                    product.id,
                    qty,
                    credit,
                    reason,
                    batchId,
                    selectedSerials
                )
            }
            supplierReturnOpen = false
        }
    }

    if (scheduleOpen) {
        SchedulePriceDialog(products, onDismiss = { scheduleOpen = false }) { product, price, effective ->
            runAction("Price change scheduled.") {
                RetailRepository.schedulePrice(context.shop.id, product.id, price, effective)
            }
            scheduleOpen = false
        }
    }

    if (promoOpen) {
        PromoDialog(products, onDismiss = { promoOpen = false }) { name, kind, promoItems, percent, buyQty, freeQty, bundle, start, end ->
            runAction("Promotion created.") {
                RetailRepository.createPromo(
                    context.shop.id,
                    name,
                    kind,
                    promoItems,
                    percent,
                    buyQty,
                    freeQty,
                    bundle,
                    start,
                    end
                )
            }
            promoOpen = false
        }
    }

    if (reservationOpen) {
        ReservationDialog(customers, products, onDismiss = { reservationOpen = false }) { customer, product, qty, deposit, method, dueAt, notes ->
            runAction("Reservation created.") {
                RetailRepository.reserveOrder(
                    context.shop.id,
                    customer.id,
                    listOf(CartLine(product, qty)),
                    deposit,
                    method,
                    dueAt,
                    notes
                )
            }
            reservationOpen = false
        }
    }

    if (creditOpen) {
        CreditTermsDialog(customers, receivables, onDismiss = { creditOpen = false }) { customer, limit, receivable, dueDate ->
            runAction("Customer credit terms updated.") {
                RetailRepository.updateCreditTerms(
                    context.shop.id,
                    customer.id,
                    limit,
                    receivable?.id,
                    dueDate
                )
            }
            creditOpen = false
        }
    }

    checklistKind?.let { kind ->
        ChecklistDialog(kind, onDismiss = { checklistKind = null }) { checks ->
            runAction(kind.replaceFirstChar { it.uppercase() } + " checklist saved.") {
                RetailRepository.saveChecklist(context.shop.id, kind, checks)
            }
            checklistKind = null
        }
    }
}

@Composable
private fun RetailOverview(
    analytics: AnalyticsSummary,
    insights: RetailInsights,
    batches: List<RetailBatch>,
    promos: List<RetailPromo>,
    orders: List<RetailOrder>,
    receivables: List<CustomerReceivable>,
    onTab: (RetailTab) -> Unit
) {
    val today = remember { LocalDate.now(ZoneId.of("Asia/Manila")) }
    val expiring = batches.count {
        val expiry = runCatching { it.expiresOn?.let(LocalDate::parse) }.getOrNull()
        expiry != null && !expiry.isBefore(today) && !expiry.isAfter(today.plusDays(30))
    }
    val overdueCredit = receivables.count {
        it.status in listOf("open", "partial") &&
            runCatching { it.dueDate?.let(LocalDate::parse)?.isBefore(today) == true }.getOrDefault(false)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RetailMetric("Today's revenue", money(analytics.revenue), "Gross profit " + money(analytics.grossProfit), Modifier.weight(1f))
                RetailMetric("Reorder", insights.reorder.size.toString(), "Below stock target", Modifier.weight(1f))
                RetailMetric("Expiry ≤30d", expiring.toString(), "Batch-tracked stock", Modifier.weight(1f))
                RetailMetric("Overdue utang", overdueCredit.toString(), "Open/partial accounts", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onTab(RetailTab.Stock) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Inventory2, null); Spacer(Modifier.width(6.dp)); Text("Stock control")
                }
                OutlinedButton(onClick = { onTab(RetailTab.Promos) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.LocalOffer, null); Spacer(Modifier.width(6.dp)); Text("Promos")
                }
                OutlinedButton(onClick = { onTab(RetailTab.Orders) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ShoppingBag, null); Spacer(Modifier.width(6.dp)); Text("Reservations")
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Reorder suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (insights.reorder.isEmpty()) {
                    Text("Stock levels are above reorder points.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    insights.reorder.take(10).forEach { row ->
                        RetailLine(
                            row.name,
                            "Stock " + qty(row.stockQuantity) + " " + row.unit + " • target +" + qty(row.suggestedQuantity),
                            row.sku
                        )
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Fast movers • " + insights.days + " days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (insights.fastMovers.isEmpty()) {
                    Text("No completed-sales movement yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    insights.fastMovers.take(8).forEach { row ->
                        RetailLine(row.name, qty(row.quantity) + " sold", money(row.revenue))
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Dead stock watch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (insights.deadStock.isEmpty()) {
                    Text("No stocked products are currently flagged as dead stock.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    insights.deadStock.take(8).forEach { row ->
                        RetailLine(
                            row.name,
                            qty(row.stockQuantity) + " " + row.unit + " on hand",
                            row.lastSoldAt?.take(10) ?: "Never sold"
                        )
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Retail pulse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                RetailLine("Active promos", promos.count { it.isActive }.toString(), "Pricing")
                RetailLine("Reserved orders", orders.count { it.status == "reserved" }.toString(), "Orders")
                RetailLine("Open receivables", receivables.count { it.status in listOf("open", "partial") }.toString(), "Utang")
            }
        }
    }
}

@Composable
private fun RetailMetric(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RetailLine(title: String, subtitle: String, end: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        end?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun RetailCatalog(
    products: List<Product>,
    favorites: List<RetailFavorite>,
    busy: Boolean,
    onConfigure: (Product) -> Unit,
    onImport: () -> Unit,
    onFavorite: (Product, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val favoriteIds = remember(favorites) { favorites.map { it.productId }.toSet() }
    val visible = products.filter {
        query.isBlank() || it.name.contains(query, true) || it.sku.contains(query, true) ||
            it.barcode?.contains(query, true) == true || it.variantName?.contains(query, true) == true
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("Search products, SKU, barcode or variant") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onImport, enabled = !busy) {
                Icon(Icons.Rounded.UploadFile, null)
                Spacer(Modifier.width(5.dp))
                Text("CSV import")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { product ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    product.sku,
                                    product.variantGroup?.let { group -> group + (product.variantName?.let { " • " + it } ?: "") },
                                    if (product.retailParentId != null) qty(product.retailMultiplier) + "× base" else null
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                money(product.sellingPrice) +
                                    (product.wholesalePrice?.let { " • wholesale " + money(it) + " @ " + qty(product.wholesaleMin) + "+" } ?: ""),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (product.isWeighed) AssistChip(onClick = {}, label = { Text("WEIGHED") })
                                if (product.batchTracked) AssistChip(onClick = {}, label = { Text("BATCH") })
                                if (product.serialTracked) AssistChip(onClick = {}, label = { Text("SERIAL") })
                                if (product.retailParentId != null) AssistChip(onClick = {}, label = { Text(product.unit.uppercase()) })
                            }
                        }
                        IconButton(
                            onClick = { onFavorite(product, product.id in favoriteIds) },
                            enabled = !busy
                        ) {
                            Icon(
                                if (product.id in favoriteIds) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favorite"
                            )
                        }
                        FilledTonalButton(onClick = { onConfigure(product) }, enabled = !busy) {
                            Text("Retail settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailStock(
    batches: List<RetailBatch>,
    serials: List<RetailSerial>,
    supplierReturns: List<RetailSupplierReturn>,
    productById: Map<String, Product>,
    supplierById: Map<String, Supplier>,
    onBatch: () -> Unit,
    onSerial: () -> Unit,
    onLoss: () -> Unit,
    onSupplierReturn: () -> Unit,
    onCreditReturn: (RetailSupplierReturn) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBatch, modifier = Modifier.weight(1f)) { Text("Receive batch") }
                OutlinedButton(onClick = onSerial, modifier = Modifier.weight(1f)) { Text("Register serials") }
                OutlinedButton(onClick = onLoss, modifier = Modifier.weight(1f)) { Text("Damage / loss") }
                OutlinedButton(onClick = onSupplierReturn, modifier = Modifier.weight(1f)) { Text("Supplier return") }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Batch & expiry inventory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (batches.isEmpty()) {
                    Text("No batches recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    batches.take(30).forEach { batch ->
                        RetailLine(
                            productById[batch.productId]?.name ?: batch.productId.take(8),
                            "Batch " + batch.batchNumber + " • " + qty(batch.quantity) + " units",
                            batch.expiresOn ?: "No expiry"
                        )
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Serialized stock", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                val available = serials.filter { it.status == "available" }
                if (available.isEmpty()) {
                    Text("No available serials.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    available.take(30).forEach { serial ->
                        RetailLine(
                            productById[serial.productId]?.name ?: serial.productId.take(8),
                            serial.serialNumber,
                            serial.status.uppercase()
                        )
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Supplier returns", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (supplierReturns.isEmpty()) {
                    Text("No supplier returns.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    supplierReturns.take(20).forEach { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(productById[row.productId]?.name ?: "Product", fontWeight = FontWeight.Bold)
                                Text(
                                    (supplierById[row.supplierId]?.name ?: "Supplier") + " • " +
                                        qty(row.quantity) + " • credit " + money(row.creditAmount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(row.status)
                            if (row.status == "pending") {
                                TextButton(onClick = { onCreditReturn(row) }) { Text("Mark credited") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailPricing(
    schedules: List<RetailPriceSchedule>,
    history: List<RetailPriceHistory>,
    productById: Map<String, Product>,
    onSchedule: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onSchedule) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Schedule price")
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Scheduled price changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (schedules.isEmpty()) {
                    Text("No scheduled changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    schedules.take(20).forEach { item ->
                        RetailLine(
                            productById[item.productId]?.name ?: "Product",
                            "New price " + money(item.price),
                            if (item.appliedAt == null) item.effectiveAt.take(16).replace("T", " ") else "Applied"
                        )
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Price history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (history.isEmpty()) {
                    Text("No price changes recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    history.take(30).forEach { item ->
                        RetailLine(
                            productById[item.productId]?.name ?: "Product",
                            (item.oldPrice?.let(::money) ?: "—") + " → " + money(item.newPrice),
                            item.createdAt.take(10)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailPromos(promos: List<RetailPromo>, onCreate: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("New promo")
                }
            }
        }
        if (promos.isEmpty()) {
            item { EmptyView("No promotions", "Create percentage, buy-get, or bundle pricing.") }
        } else {
            items(promos, key = { it.id }) { promo ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(promo.name, fontWeight = FontWeight.Bold)
                            Text(
                                when (promo.kind) {
                                    "percent" -> qty(promo.discountPercent) + "% off"
                                    "bogo" -> "Buy " + qty(promo.buyQty) + " get " + qty(promo.freeQty)
                                    "bundle" -> "Bundle " + money(promo.bundlePrice)
                                    else -> promo.kind
                                },
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                promo.startsAt.take(10) + " → " + promo.endsAt.take(10),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(if (promo.isActive) "active" else "inactive")
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailOrders(
    orders: List<RetailOrder>,
    receivables: List<CustomerReceivable>,
    customerById: Map<String, Customer>,
    onReserve: () -> Unit,
    onCredit: () -> Unit,
    onCancel: (RetailOrder) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReserve) {
                    Icon(Icons.Rounded.BookmarkAdd, null)
                    Spacer(Modifier.width(5.dp))
                    Text("New reservation")
                }
                OutlinedButton(onClick = onCredit) {
                    Icon(Icons.Rounded.CreditScore, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Utang / due date")
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Reservations & pickup orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (orders.isEmpty()) {
                    Text("No reservations yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    orders.take(30).forEach { order ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(customerById[order.customerId]?.name ?: "Customer", fontWeight = FontWeight.Bold)
                                Text(
                                    money(order.quotedTotal) + " • deposit " + money(order.deposit) +
                                        (order.dueAt?.let { " • due " + it.take(16).replace("T", " ") } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(order.status)
                            if (order.status == "reserved" && order.deposit <= 0) {
                                TextButton(onClick = { onCancel(order) }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Customer receivables", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (receivables.isEmpty()) {
                    Text("No customer balances.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    receivables.filter { it.status in listOf("open", "partial") }.take(30).forEach { row ->
                        RetailLine(
                            customerById[row.customerId]?.name ?: "Customer",
                            "Balance " + money(row.balance),
                            row.dueDate ?: "No due date"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailCashClose(
    checklists: List<RetailChecklist>,
    onChecklist: (String) -> Unit
) {
    val denoms = remember { listOf(1000.0, 500.0, 200.0, 100.0, 50.0, 20.0, 10.0, 5.0, 1.0, .25) }
    val counts = remember { mutableStateMapOf<Double, String>() }
    val total = denoms.sumOf { d -> (counts[d]?.toIntOrNull() ?: 0) * d }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Cash denomination counter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Count the drawer before opening or closing a shift.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                denoms.forEach { denomination ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(money(denomination), modifier = Modifier.width(100.dp))
                        OutlinedTextField(
                            counts[denomination].orEmpty(),
                            { value -> counts[denomination] = value.filter(Char::isDigit).take(5) },
                            label = { Text("Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            money((counts[denomination]?.toIntOrNull() ?: 0) * denomination),
                            modifier = Modifier.width(120.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("COUNTED CASH", fontWeight = FontWeight.Black)
                    Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onChecklist("opening") }, modifier = Modifier.weight(1f)) {
                    Text("Opening checklist")
                }
                OutlinedButton(onClick = { onChecklist("closing") }, modifier = Modifier.weight(1f)) {
                    Text("Closing checklist")
                }
            }
        }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Recent checklist submissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (checklists.isEmpty()) {
                    Text("No checklist submissions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    checklists.take(20).forEach { item ->
                        RetailLine(item.kind.replaceFirstChar { it.uppercase() }, item.day, item.createdAt.take(16).replace("T", " "))
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailProductDialog(
    product: Product,
    baseProducts: List<Product>,
    onDismiss: () -> Unit,
    onSave: (String?, Double, Double?, Double, String?, String?, String, Boolean, Boolean, Boolean) -> Unit
) {
    var parent by remember { mutableStateOf(baseProducts.firstOrNull { it.id == product.retailParentId }) }
    var parentMenu by remember { mutableStateOf(false) }
    var multiplier by remember { mutableStateOf(qty(product.retailMultiplier)) }
    var wholesale by remember { mutableStateOf(product.wholesalePrice?.toString().orEmpty()) }
    var wholesaleMin by remember { mutableStateOf(qty(product.wholesaleMin)) }
    var group by remember { mutableStateOf(product.variantGroup.orEmpty()) }
    var variant by remember { mutableStateOf(product.variantName.orEmpty()) }
    var unit by remember { mutableStateOf(product.unit) }
    var weighed by remember { mutableStateOf(product.isWeighed) }
    var batch by remember { mutableStateOf(product.batchTracked) }
    var serial by remember { mutableStateOf(product.serialTracked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retail settings • " + product.name, fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { parentMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(parent?.let { "Pack of " + it.name } ?: "Base / tingi SKU")
                    }
                    DropdownMenu(parentMenu, { parentMenu = false }) {
                        DropdownMenuItem(text = { Text("Base / tingi SKU") }, onClick = {
                            parent = null
                            parentMenu = false
                        })
                        baseProducts.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name + " • " + p.sku) }, onClick = {
                                parent = p
                                parentMenu = false
                            })
                        }
                    }
                }
                if (parent != null) {
                    NumberField(multiplier, { multiplier = it }, "Base units per " + unit)
                }
                NumberField(wholesale, { wholesale = it }, "Wholesale price (optional)")
                NumberField(wholesaleMin, { wholesaleMin = it }, "Wholesale minimum quantity")
                OutlinedTextField(group, { group = it }, label = { Text("Variant group") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(variant, { variant = it }, label = { Text("Variant name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Selling unit (pc, pack, box, kg…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ToggleRow("Weighed product", "Allow decimal quantities such as 0.25 kg.", weighed) { weighed = it }
                ToggleRow("Batch / expiry tracking", "Use FEFO allocation during checkout.", batch) { batch = it }
                ToggleRow("Serial tracking", "Require one available serial per sold base unit.", serial) { serial = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        parent?.id,
                        multiplier.toDoubleOrNull()?.coerceAtLeast(.0001) ?: 1.0,
                        wholesale.toDoubleOrNull(),
                        wholesaleMin.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        group.ifBlank { null },
                        variant.ifBlank { null },
                        unit,
                        weighed,
                        batch,
                        serial
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CsvImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember {
        mutableStateOf(
            "sku,name,barcode,cost_price,selling_price,unit,reorder_level,opening_stock\n" +
                "SKU-001,Sample Product,480000000001,25,35,pc,5,10"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk CSV import", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste up to 1,000 rows. SKU and name are required.")
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text("CSV data") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
                )
            }
        },
        confirmButton = { Button(onClick = { onImport(text) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BatchDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Product, String, String?, Double, Boolean) -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var batch by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var receiveStock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive / allocate batch", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ProductPicker(products, product, { product = it })
                OutlinedTextField(batch, { batch = it }, label = { Text("Batch / lot number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry YYYY-MM-DD (optional)") }, modifier = Modifier.fillMaxWidth())
                NumberField(quantity, { quantity = it }, "Quantity")
                ToggleRow("Receive into physical stock", "Adds this quantity to inventory before allocating it to the batch.", receiveStock) { receiveStock = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { product?.let { onSave(it, batch, expiry.ifBlank { null }, quantity.toDoubleOrNull() ?: 0.0, receiveStock) } },
                enabled = product != null && batch.isNotBlank() && (quantity.toDoubleOrNull() ?: 0.0) > 0
            ) { Text("Save batch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SerialDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Product, List<String>) -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var serialText by remember { mutableStateOf("") }
    val values = serialText.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register serial numbers", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ProductPicker(products, product, { product = it })
                OutlinedTextField(
                    serialText,
                    { serialText = it },
                    label = { Text("One serial per line") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                )
                Text(values.size.toString() + " unique serial(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { product?.let { onSave(it, values) } }, enabled = product != null && values.isNotEmpty()) {
                Text("Register")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StockLossDialog(
    products: List<Product>,
    batches: List<RetailBatch>,
    serials: List<RetailSerial>,
    onDismiss: () -> Unit,
    onSave: (Product, Double, String, String?, String?, List<String>) -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("damage") }
    var notes by remember { mutableStateOf("") }
    var batchId by remember { mutableStateOf<String?>(null) }
    var serialText by remember { mutableStateOf("") }
    val productBatches = batches.filter { it.productId == product?.id && it.quantity > 0 }
    val availableSerials = serials.filter { it.productId == product?.id && it.status == "available" }
    val selectedSerials = serialText.lineSequence().map(String::trim).filter { value -> availableSerials.any { it.serialNumber == value } }.distinct().toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Damage, theft or personal use", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ProductPicker(products, product, { product = it; batchId = null; serialText = "" })
                NumberField(qtyText, { qtyText = it }, "Quantity")
                ChoiceRow(listOf("damage", "theft", "personal_use"), reason) { reason = it }
                if (product?.batchTracked == true) {
                    SimplePicker(
                        label = "Batch",
                        options = productBatches.map { it.id to (it.batchNumber + " • " + qty(it.quantity)) },
                        selected = batchId,
                        onSelect = { batchId = it }
                    )
                }
                if (product?.serialTracked == true) {
                    OutlinedTextField(
                        serialText,
                        { serialText = it },
                        label = { Text("Available serials, one per line") },
                        supportingText = { Text(availableSerials.take(6).joinToString(" • ") { it.serialNumber }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val q = qtyText.toDoubleOrNull() ?: 0.0
            val serialOk = product?.serialTracked != true || selectedSerials.size.toDouble() == q
            Button(
                onClick = { product?.let { onSave(it, q, reason, notes.ifBlank { null }, batchId, selectedSerials) } },
                enabled = product != null && q > 0 && (product?.batchTracked != true || batchId != null) && serialOk
            ) { Text("Record adjustment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SupplierReturnDialog(
    suppliers: List<Supplier>,
    products: List<Product>,
    batches: List<RetailBatch>,
    serials: List<RetailSerial>,
    onDismiss: () -> Unit,
    onSave: (Supplier, Product, Double, Double, String, String?, List<String>) -> Unit
) {
    var supplier by remember { mutableStateOf<Supplier?>(null) }
    var product by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var creditText by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("") }
    var batchId by remember { mutableStateOf<String?>(null) }
    var serialText by remember { mutableStateOf("") }
    val productBatches = batches.filter { it.productId == product?.id && it.quantity > 0 }
    val availableSerials = serials.filter { it.productId == product?.id && it.status == "available" }
    val selectedSerials = serialText.lineSequence().map(String::trim).filter { value -> availableSerials.any { it.serialNumber == value } }.distinct().toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return stock to supplier", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SupplierPicker(suppliers, supplier) { supplier = it }
                ProductPicker(products, product, { product = it; batchId = null; serialText = "" })
                NumberField(qtyText, { qtyText = it }, "Quantity")
                NumberField(creditText, { creditText = it }, "Expected supplier credit")
                if (product?.batchTracked == true) {
                    SimplePicker(
                        "Batch",
                        productBatches.map { it.id to (it.batchNumber + " • " + qty(it.quantity)) },
                        batchId
                    ) { batchId = it }
                }
                if (product?.serialTracked == true) {
                    OutlinedTextField(
                        serialText,
                        { serialText = it },
                        label = { Text("Serials returned, one per line") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val q = qtyText.toDoubleOrNull() ?: 0.0
            val serialOk = product?.serialTracked != true || selectedSerials.size.toDouble() == q
            Button(
                onClick = {
                    if (supplier != null && product != null) {
                        onSave(supplier!!, product!!, q, creditText.toDoubleOrNull() ?: 0.0, reason, batchId, selectedSerials)
                    }
                },
                enabled = supplier != null && product != null && q > 0 && reason.isNotBlank() &&
                    (product?.batchTracked != true || batchId != null) && serialOk
            ) { Text("Return stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SchedulePriceDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Product, Double, String) -> Unit
) {
    var product by remember { mutableStateOf<Product?>(null) }
    var price by remember { mutableStateOf("") }
    var effective by remember {
        mutableStateOf(
            OffsetDateTime.now(ZoneId.of("Asia/Manila")).plusDays(1).withSecond(0).withNano(0).toString()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule selling price", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ProductPicker(products, product) { product = it }
                NumberField(price, { price = it }, "New selling price")
                OutlinedTextField(
                    effective,
                    { effective = it },
                    label = { Text("Effective ISO date/time") },
                    supportingText = { Text("Example: 2026-10-31T08:00:00+08:00") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { product?.let { onSave(it, price.toDoubleOrNull() ?: 0.0, effective) } },
                enabled = product != null && (price.toDoubleOrNull() ?: -1.0) >= 0 && runCatching { OffsetDateTime.parse(effective) }.isSuccess
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PromoDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<RetailPromoItem>, Double, Double, Double, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("percent") }
    var product by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var percent by remember { mutableStateOf("10") }
    var buyQty by remember { mutableStateOf("1") }
    var freeQty by remember { mutableStateOf("1") }
    var bundlePrice by remember { mutableStateOf("0") }
    val startDefault = remember { OffsetDateTime.now(ZoneId.of("Asia/Manila")).withSecond(0).withNano(0).toString() }
    var starts by remember { mutableStateOf(startDefault) }
    var ends by remember {
        mutableStateOf(OffsetDateTime.now(ZoneId.of("Asia/Manila")).plusDays(30).withSecond(0).withNano(0).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create promotion", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Promo name") }, modifier = Modifier.fillMaxWidth())
                ChoiceRow(listOf("percent", "bogo", "bundle"), kind) { kind = it }
                ProductPicker(products, product) { product = it }
                NumberField(qtyText, { qtyText = it }, if (kind == "bundle") "Bundle quantity" else "Product quantity")
                when (kind) {
                    "percent" -> NumberField(percent, { percent = it }, "Discount %")
                    "bogo" -> {
                        NumberField(buyQty, { buyQty = it }, "Buy quantity")
                        NumberField(freeQty, { freeQty = it }, "Free quantity")
                    }
                    "bundle" -> NumberField(bundlePrice, { bundlePrice = it }, "Bundle price")
                }
                OutlinedTextField(starts, { starts = it }, label = { Text("Starts (ISO date/time)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ends, { ends = it }, label = { Text("Ends (ISO date/time)") }, modifier = Modifier.fillMaxWidth())
                Text("Multi-product bundles can be created from StorePOS Cloud. Android quick-create starts with one SKU.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val q = qtyText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    product?.let {
                        onSave(
                            name,
                            kind,
                            listOf(RetailPromoItem(it.id, q)),
                            percent.toDoubleOrNull() ?: 0.0,
                            buyQty.toDoubleOrNull() ?: 1.0,
                            freeQty.toDoubleOrNull() ?: 1.0,
                            bundlePrice.toDoubleOrNull() ?: 0.0,
                            starts,
                            ends
                        )
                    }
                },
                enabled = name.isNotBlank() && product != null && q > 0 &&
                    runCatching { OffsetDateTime.parse(starts) }.isSuccess &&
                    runCatching { OffsetDateTime.parse(ends) }.isSuccess
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReservationDialog(
    customers: List<Customer>,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Customer, Product, Double, Double, String, String?, String?) -> Unit
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    var product by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var depositText by remember { mutableStateOf("0") }
    var method by remember { mutableStateOf("cash") }
    var dueAt by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reservation / pickup order", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                CustomerPicker(customers, customer) { customer = it }
                ProductPicker(products, product) { product = it }
                NumberField(qtyText, { qtyText = it }, "Quantity")
                NumberField(depositText, { depositText = it }, "Deposit")
                ChoiceRow(listOf("cash", "gcash", "maya", "card", "bank"), method) { method = it }
                OutlinedTextField(
                    dueAt,
                    { dueAt = it },
                    label = { Text("Pickup due ISO date/time (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val q = qtyText.toDoubleOrNull() ?: 0.0
            val dep = depositText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (customer != null && product != null) {
                        onSave(customer!!, product!!, q, dep, method, dueAt.ifBlank { null }, notes.ifBlank { null })
                    }
                },
                enabled = customer != null && product != null && q > 0 && dep >= 0
            ) { Text("Reserve") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreditTermsDialog(
    customers: List<Customer>,
    receivables: List<CustomerReceivable>,
    onDismiss: () -> Unit,
    onSave: (Customer, Double, CustomerReceivable?, String?) -> Unit
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    var limitText by remember { mutableStateOf("0") }
    var selectedReceivable by remember { mutableStateOf<CustomerReceivable?>(null) }
    var dueDate by remember { mutableStateOf("") }
    val open = receivables.filter { it.customerId == customer?.id && it.status in listOf("open", "partial") }

    LaunchedEffect(customer?.id) {
        limitText = customer?.creditLimit?.toString() ?: "0"
        selectedReceivable = null
        dueDate = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Utang & due date", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                CustomerPicker(customers, customer) { customer = it }
                NumberField(limitText, { limitText = it }, "Credit limit")
                if (open.isNotEmpty()) {
                    SimplePicker(
                        "Receivable (optional)",
                        open.map { it.id to (money(it.balance) + " • " + (it.dueDate ?: "no due date")) },
                        selectedReceivable?.id
                    ) { id -> selectedReceivable = open.firstOrNull { it.id == id } }
                    OutlinedTextField(
                        dueDate,
                        { dueDate = it },
                        label = { Text("Due date YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { customer?.let { onSave(it, limitText.toDoubleOrNull() ?: 0.0, selectedReceivable, dueDate.ifBlank { null }) } },
                enabled = customer != null && (limitText.toDoubleOrNull() ?: -1.0) >= 0
            ) { Text("Save terms") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChecklistDialog(
    kind: String,
    onDismiss: () -> Unit,
    onSave: (Map<String, Boolean>) -> Unit
) {
    val labels = if (kind == "opening") {
        listOf("register_ready" to "Register and POS device ready", "cash_counted" to "Opening cash counted", "printer_ready" to "Receipt printer checked", "stock_alerts" to "Low-stock / expiry alerts reviewed")
    } else {
        listOf("cash_counted" to "Closing cash counted", "pending_sales" to "Offline / pending sales synced", "returns_checked" to "Returns and voids reviewed", "register_secured" to "Register and devices secured")
    }
    val checks = remember(kind) { mutableStateMapOf<String, Boolean>().apply { labels.forEach { put(it.first, false) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.replaceFirstChar { it.uppercase() } + " checklist", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                labels.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checks[key] == true, onCheckedChange = { checks[key] = it })
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(checks.toMap()) }, enabled = checks.values.all { it }) { Text("Save completed") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProductPicker(products: List<Product>, selected: Product?, onSelect: (Product) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { it.name + " • " + it.sku } ?: "Select product")
        }
        DropdownMenu(open, { open = false }) {
            products.take(200).forEach { product ->
                DropdownMenuItem(
                    text = { Text(product.name + " • " + product.sku) },
                    onClick = { onSelect(product); open = false }
                )
            }
        }
    }
}

@Composable
private fun CustomerPicker(customers: List<Customer>, selected: Customer?, onSelect: (Customer) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: "Select customer")
        }
        DropdownMenu(open, { open = false }) {
            customers.filter { it.isActive }.take(200).forEach { customer ->
                DropdownMenuItem(text = { Text(customer.name) }, onClick = { onSelect(customer); open = false })
            }
        }
    }
}

@Composable
private fun SupplierPicker(suppliers: List<Supplier>, selected: Supplier?, onSelect: (Supplier) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: "Select supplier")
        }
        DropdownMenu(open, { open = false }) {
            suppliers.filter { it.isActive }.take(200).forEach { supplier ->
                DropdownMenuItem(text = { Text(supplier.name) }, onClick = { onSelect(supplier); open = false })
            }
        }
    }
}

@Composable
private fun SimplePicker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel ?: label)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelect(option.first); open = false })
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option.replace("_", " ").uppercase()) }
            )
        }
    }
}

private fun qty(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
