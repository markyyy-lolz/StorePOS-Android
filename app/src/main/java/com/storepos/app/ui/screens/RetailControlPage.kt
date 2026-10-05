package com.storepos.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.storepos.app.data.RetailOpsRepository
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

private fun JsonObject.text(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.number(key: String): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

private fun JsonObject.array(key: String): JsonArray =
    runCatching { this[key]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())

private fun healthIssueCount(health: JsonObject): Int =
    listOf("duplicate_barcodes", "negative_stock", "missing_cost", "expired_batches", "stale_scheduled_prices")
        .sumOf { health.array(it).size }

@Composable
fun RetailControlPage(context: ShopContext) {
    val role = context.member.role.lowercase()
    val manager = role in listOf("owner", "admin", "manager")
    val purchasing = manager || role == "inventory"
    val scope = rememberCoroutineScope()

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var settings by remember { mutableStateOf(ShopSettings(shopId = context.shop.id)) }
    var approvals by remember { mutableStateOf<List<RetailManagerApproval>>(emptyList()) }
    var reconciliations by remember { mutableStateOf<List<RetailPaymentReconciliation>>(emptyList()) }
    var reprints by remember { mutableStateOf<List<RetailReceiptReprint>>(emptyList()) }
    var supplierPrices by remember { mutableStateOf(JsonArray(emptyList())) }
    var health by remember { mutableStateOf(JsonObject(emptyMap())) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var priceQuery by remember { mutableStateOf("") }
    var reconcileOpen by remember { mutableStateOf(false) }
    var approvalOpen by remember { mutableStateOf(false) }
    var xReport by remember { mutableStateOf<JsonObject?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotBlank() }?.let {
            priceQuery = it
            error = null
        }
    }

    suspend fun refresh() = coroutineScope {
        val p = async { StoreRepository.products(context.shop.id) }
        val s = async { StoreRepository.shopSettings(context.shop.id) }
        val a = async { RetailOpsRepository.approvals(context.shop.id) }
        val r = async { RetailOpsRepository.reconciliations(context.shop.id) }
        val rp = async { RetailOpsRepository.receiptReprints(context.shop.id) }
        val sp = async { if (purchasing) RetailOpsRepository.supplierPrices(context.shop.id) else JsonArray(emptyList()) }
        val h = async { if (purchasing) RetailOpsRepository.dataHealth(context.shop.id) else JsonObject(emptyMap()) }

        products = p.await()
        settings = s.await()
        approvals = a.await()
        reconciliations = r.await()
        reprints = rp.await()
        supplierPrices = sp.await()
        health = h.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) return LoadingView("Opening Retail Control…")

    val priceMatch = products.firstOrNull { product ->
        val q = priceQuery.trim()
        q.isNotBlank() && product.isActive && (
            product.barcode.equals(q, true) ||
                product.sku.equals(q, true) ||
                product.name.contains(q, true) ||
                product.brand?.contains(q, true) == true
            )
    }
    val pending = approvals.filter { it.status == "pending" }
    val walletIssues = reconciliations.count { kotlin.math.abs(it.variance) >= 0.01 }
    val issues = healthIssueCount(health)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            PageHeader(
                "Retail Control",
                "Price checker, purchasing, X/Z, e-wallet, approvals and retail audit",
                action = {
                    IconButton(onClick = {
                        scope.launch {
                            error = null
                            runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                        }
                    }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                }
            )
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        notice?.let {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(it, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlMetric("Health", issues.toString(), "issue(s)", Modifier.weight(1f))
                ControlMetric("Approvals", pending.size.toString(), "pending", Modifier.weight(1f))
                ControlMetric("Wallet", walletIssues.toString(), "variance", Modifier.weight(1f))
            }
        }

        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Price checker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Scan or search without adding the product to the cart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = priceQuery,
                        onValueChange = { priceQuery = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Barcode / SKU / product") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true
                    )
                    FilledIconButton(onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setPrompt("Scan barcode for price check")
                                .setBeepEnabled(true)
                                .setOrientationLocked(false)
                        )
                    }) { Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan") }
                }
                if (priceQuery.isNotBlank()) {
                    if (priceMatch == null) {
                        Text("No matching active product.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(priceMatch.name, fontWeight = FontWeight.Black)
                                    Text(
                                        "${priceMatch.sku}${priceMatch.barcode?.let { " • $it" } ?: ""}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("Stock ${priceMatch.stockQuantity} ${priceMatch.unit}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    money(priceMatch.sellingPrice),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Daily controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching { StoreRepository.startInventoryCount(context.shop.id) }
                                    .onSuccess { notice = "Stocktake started/resumed. Open Inventory to enter physical counts." }
                                    .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        },
                        enabled = purchasing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FactCheck, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stocktake")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { RetailOpsRepository.xReport(context.shop.id) }
                                    .onSuccess { xReport = it }
                                    .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Assessment, null)
                        Spacer(Modifier.width(4.dp))
                        Text("X Report")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { approvalOpen = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Approval, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Approval")
                    }
                    OutlinedButton(onClick = { reconcileOpen = true }, enabled = manager, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reconcile")
                    }
                }
            }
        }

        if (manager) {
            item {
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Negative stock policy", fontWeight = FontWeight.Black)
                            Text(
                                if (settings.allowNegativeStock)
                                    "Allowed by shop policy. Batch/serial stock still needs tracked units."
                                else
                                    "Blocked when stock is insufficient.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.allowNegativeStock,
                            onCheckedChange = { value ->
                                scope.launch {
                                    val next = settings.copy(allowNegativeStock = value)
                                    runCatching { StoreRepository.updateShopSettings(next) }
                                        .onSuccess {
                                            settings = next
                                            notice = if (value) "Negative stock allowed." else "Negative stock blocked."
                                        }
                                        .onFailure { error = StoreRepository.userMessage(it) }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (purchasing) {
            item { Text("Reorder → Purchase Order", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
            val suggestions = supplierPrices.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .filter { it.number("stock_quantity") <= it.number("reorder_level") }
                .take(15)
            if (suggestions.isEmpty()) {
                item { EmptyView("No reorder suggestions", "Stock is currently above reorder levels.") }
            } else {
                items(suggestions, key = { it.text("product_id") }) { item ->
                    val product = products.firstOrNull { it.id == item.text("product_id") }
                    val best = item.array("suppliers").firstOrNull()?.let { runCatching { it.jsonObject }.getOrNull() }
                    val suggested = kotlin.math.max(1.0, item.number("reorder_level") * 2 - item.number("stock_quantity"))
                    MotoCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.text("name"), fontWeight = FontWeight.Bold)
                                Text(
                                    "${item.text("sku")} • stock ${safeControlQty(item.number("stock_quantity"))} • reorder ${safeControlQty(item.number("reorder_level"))}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    if (best != null) "Best: ${best.text("supplier_name")} • ${money(best.number("unit_cost"))}"
                                    else "No supplier price history yet",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = {
                                    if (product == null || best == null) return@Button
                                    scope.launch {
                                        runCatching {
                                            StoreRepository.createPurchaseOrder(
                                                shopId = context.shop.id,
                                                supplierId = best.text("supplier_id"),
                                                lines = listOf(Triple(product, suggested, best.number("unit_cost"))),
                                                notes = "StorePOS v1.3 reorder suggestion"
                                            )
                                        }.onSuccess {
                                            notice = "Draft PO created for ${product.name}."
                                            refresh()
                                        }.onFailure { error = StoreRepository.userMessage(it) }
                                    }
                                },
                                enabled = product != null && best != null
                            ) { Text("PO ${safeControlQty(suggested)}") }
                        }
                    }
                }
            }
        }

        item { Text("Manager approvals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        if (approvals.isEmpty()) {
            item { EmptyView("No approval requests", "Exception requests will appear here.") }
        } else {
            items(approvals.take(15), key = { it.id }) { approval ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(approval.approvalType.replace("_", " ").uppercase(), fontWeight = FontWeight.Bold)
                            Text(approval.reason)
                            Text(
                                "${approval.status.uppercase()} • ${approval.createdAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (manager && approval.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            RetailOpsRepository.reviewApproval(context.shop.id, approval.id, "rejected", "Rejected in Android Retail Control")
                                        }.onSuccess { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                                    }
                                }) { Text("Reject") }
                                Button(onClick = {
                                    scope.launch {
                                        runCatching {
                                            RetailOpsRepository.reviewApproval(context.shop.id, approval.id, "approved", "Approved in Android Retail Control")
                                        }.onSuccess { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                                    }
                                }) { Text("Approve") }
                            }
                        }
                    }
                }
            }
        }

        item { Text("E-wallet reconciliation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        if (reconciliations.isEmpty()) {
            item { EmptyView("No reconciliations", "GCash, Maya, card and bank checks will appear here.") }
        } else {
            items(reconciliations.take(12), key = { it.id }) { row ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.AccountBalanceWallet, null) },
                    headlineContent = { Text("${row.method.uppercase()} • ${row.businessDate}", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Expected ${money(row.expectedAmount)} • Actual ${money(row.actualAmount)}") },
                    trailingContent = {
                        Text(
                            money(row.variance),
                            fontWeight = FontWeight.Black,
                            color = if (kotlin.math.abs(row.variance) < 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }

        if (purchasing) {
            item { Text("Data health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
            val healthGroups = listOf(
                "duplicate_barcodes" to "Duplicate barcodes",
                "negative_stock" to "Negative stock",
                "missing_cost" to "Missing cost",
                "expired_batches" to "Expired batches",
                "stale_scheduled_prices" to "Stale scheduled prices"
            )
            items(healthGroups) { (key, label) ->
                val count = health.array(key).size
                ListItem(
                    leadingContent = {
                        Icon(
                            if (count == 0) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (count == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    },
                    headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
                    trailingContent = { Text(count.toString(), fontWeight = FontWeight.Black) }
                )
            }
        }

        item { Text("Receipt reprint audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        if (reprints.isEmpty()) {
            item { EmptyView("No reprints", "Extra receipt copies are numbered and audited.") }
        } else {
            items(reprints.take(12), key = { it.id }) { row ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Print, null) },
                    headlineContent = { Text("Copy #${row.copyNo}", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(row.reason) },
                    trailingContent = { Text(row.saleId.take(8) + "…") }
                )
            }
        }
    }

    if (reconcileOpen) {
        ReconcileDialog(
            onDismiss = { reconcileOpen = false },
            onSave = { date, method, amount, reference, notes ->
                scope.launch {
                    runCatching { RetailOpsRepository.reconcilePayment(context.shop.id, date, method, amount, reference, notes) }
                        .onSuccess {
                            reconcileOpen = false
                            notice = "E-wallet reconciliation saved."
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (approvalOpen) {
        ApprovalRequestDialog(
            onDismiss = { approvalOpen = false },
            onSave = { type, reason, details ->
                scope.launch {
                    runCatching { RetailOpsRepository.requestApproval(context.shop.id, type, reason, details) }
                        .onSuccess {
                            approvalOpen = false
                            notice = "Manager approval request sent."
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    xReport?.let { report -> XReportDialog(report = report, onDismiss = { xReport = null }) }
}

private fun safeControlQty(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

@Composable
private fun ControlMetric(label: String, value: String, help: String, modifier: Modifier) {
    MotoCard(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReconcileDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String?, String?) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var method by remember { mutableStateOf("gcash") }
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("E-wallet reconciliation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Business date YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("gcash", "maya", "card", "bank").forEach { item ->
                        FilterChip(selected = method == item, onClick = { method = item }, label = { Text(item.uppercase()) })
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Actual provider total") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(reference, { reference = it }, label = { Text("Statement / batch reference") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(date, method, amount.toDoubleOrNull() ?: 0.0, reference.ifBlank { null }, notes.ifBlank { null }) },
                enabled = amount.toDoubleOrNull()?.let { it >= 0 } == true
            ) { Text("Reconcile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApprovalRequestDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var type by remember { mutableStateOf("discount") }
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request manager approval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select an exception type and explain why approval is needed.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("discount", "price_override", "negative_stock").forEach { item ->
                        FilterChip(
                            selected = type == item,
                            onClick = { type = item },
                            label = { Text(item.replace("_", " ")) }
                        )
                    }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(details, { details = it }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(type, reason, details.ifBlank { null }) }, enabled = reason.isNotBlank()) {
                Text("Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun XReportDialog(report: JsonObject, onDismiss: () -> Unit) {
    val payments = runCatching { report["payment_breakdown"]?.jsonObject }.getOrNull() ?: JsonObject(emptyMap())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("X Report") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("Live register snapshot • does not close the shift", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { XRow("Transactions", safeControlQty(report.number("transaction_count"))) }
                item { XRow("Gross sales", money(report.number("gross_sales"))) }
                item { XRow("Cash sales", money(report.number("cash_sales"))) }
                item { XRow("Non-cash", money(report.number("noncash_sales"))) }
                item { XRow("Cash in", money(report.number("cash_in"))) }
                item { XRow("Cash out", money(report.number("cash_out"))) }
                item { XRow("Cash refunds", money(report.number("cash_refunds"))) }
                item { XRow("Expected cash", money(report.number("expected_cash"))) }
                if (payments.isNotEmpty()) {
                    item { HorizontalDivider() }
                    items(payments.entries.toList(), key = { it.key }) { entry ->
                        val amount = entry.value.jsonPrimitive.doubleOrNull ?: 0.0
                        XRow(entry.key.uppercase(), money(amount))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun XRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
