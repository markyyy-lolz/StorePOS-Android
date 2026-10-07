package com.storepos.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.BuildConfig
import com.storepos.app.data.AdminRepository
import com.storepos.app.data.RetailOpsRepository
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.local.OfflineStore
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Date

@Composable
fun AdminCenterPage(context: ShopContext) {
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    val offlineStore = remember { OfflineStore(androidContext) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    var pending by remember { mutableStateOf<List<PendingOfflineSale>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<AuditLog>>(emptyList()) }
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<ShopAlert>>(emptyList()) }
    var health by remember { mutableStateOf<JsonObject?>(null) }
    var latestVersion by remember { mutableStateOf<AppVersion?>(null) }
    var auditQuery by remember { mutableStateOf("") }

    suspend fun refresh() = coroutineScope {
        pending = offlineStore.pendingSales()
        val a = async { runCatching { AdminRepository.auditLogs(context.shop.id) }.getOrDefault(emptyList()) }
        val p = async { runCatching { StoreRepository.userProfiles() }.getOrDefault(emptyList()) }
        val al = async { runCatching { StoreRepository.shopAlerts(context.shop.id) }.getOrDefault(emptyList()) }
        val h = async { runCatching { RetailOpsRepository.dataHealth(context.shop.id) }.getOrNull() }
        val v = async { runCatching { StoreRepository.latestVersion() }.getOrNull() }

        auditLogs = a.await()
        profiles = p.await()
        alerts = al.await()
        health = h.await()
        latestVersion = v.await()
    }

    suspend fun syncOne(row: PendingOfflineSale): Boolean {
        return runCatching {
            StoreRepository.completeOfflineSale(row.payload)
            offlineStore.removePendingSale(row.id)
            true
        }.getOrElse {
            offlineStore.setPendingError(row.id, StoreRepository.userMessage(it))
            false
        }
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }
            .onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading Admin Center…")
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PageHeader(
            "Admin Center",
            "Sync recovery • audit trail • system diagnostics",
            action = {
                IconButton(onClick = {
                    scope.launch {
                        error = null
                        notice = null
                        runCatching { refresh() }
                            .onFailure { error = StoreRepository.userMessage(it) }
                    }
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Sync Center") },
                icon = { Icon(Icons.Rounded.Sync, null) }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Audit Trail") },
                icon = { Icon(Icons.Rounded.History, null) }
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { Text("Diagnostics") },
                icon = { Icon(Icons.Rounded.HealthAndSafety, null) }
            )
            Tab(
                selected = tab == 3,
                onClick = { tab = 3 },
                text = { Text("Backup") },
                icon = { Icon(Icons.Rounded.SaveAlt, null) }
            )
        }

        when (tab) {
            0 -> SyncCenterTab(
                pending = pending,
                syncing = syncing,
                onRetryOne = { row ->
                    scope.launch {
                        syncing = true
                        error = null
                        notice = null
                        val ok = syncOne(row)
                        pending = offlineStore.pendingSales()
                        notice = if (ok) "Queued sale synced successfully." else null
                        if (!ok) error = pending.firstOrNull { it.id == row.id }?.lastError ?: "Sync failed."
                        syncing = false
                    }
                },
                onRetryAll = {
                    scope.launch {
                        syncing = true
                        error = null
                        notice = null
                        val queued = offlineStore.pendingSales()
                        var success = 0
                        queued.forEach { if (syncOne(it)) success++ }
                        pending = offlineStore.pendingSales()
                        notice = success.toString() + " of " + queued.size + " queued sale(s) synced."
                        if (pending.isNotEmpty()) {
                            error = "Some sales still need attention. Open each row to review the last error."
                        }
                        syncing = false
                    }
                }
            )
            1 -> AuditTrailTab(
                logs = auditLogs,
                profiles = profiles,
                query = auditQuery,
                onQuery = { auditQuery = it }
            )
            2 -> DiagnosticsTab(
                pendingCount = pending.size,
                alerts = alerts,
                health = health,
                latestVersion = latestVersion
            )
            else -> BackupExportTab(context)
        }
    }
}

@Composable
private fun SyncCenterTab(
    pending: List<PendingOfflineSale>,
    syncing: Boolean,
    onRetryOne: (PendingOfflineSale) -> Unit,
    onRetryAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MotoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (pending.isEmpty()) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = if (pending.isEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Offline Sync Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (pending.isEmpty())
                            "All offline transactions are synced."
                        else
                            pending.size.toString() + " transaction(s) waiting for StorePOS Cloud.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onRetryAll,
                    enabled = pending.isNotEmpty() && !syncing
                ) {
                    Icon(Icons.Rounded.Sync, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (syncing) "Syncing…" else "Retry all")
                }
            }
        }

        if (pending.isEmpty()) {
            EmptyView(
                "Sync queue is clear",
                "Offline sales will appear here if the internet goes down during checkout.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pending, key = { it.id }) { row ->
                    MotoCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (row.lastError == null) Icons.Rounded.Schedule else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (row.lastError == null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Offline sale • " + row.payload.items.size + " line(s)",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    DateFormat.getDateTimeInstance().format(Date(row.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                row.lastError?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onRetryOne(row) },
                                enabled = !syncing
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditTrailTab(
    logs: List<AuditLog>,
    profiles: List<UserProfile>,
    query: String,
    onQuery: (String) -> Unit
) {
    val filtered = remember(logs, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) logs
        else logs.filter {
            it.action.lowercase().contains(q) ||
                it.entityType.lowercase().contains(q) ||
                it.deviceName.orEmpty().lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search action, entity or device") }
        )

        if (filtered.isEmpty()) {
            EmptyView(
                "No audit events",
                "Price edits, stock changes, payments, approvals and other controlled actions will appear here.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filtered.take(250), key = { it.id }) { log ->
                    val actor = profiles.firstOrNull { it.id == log.actorId }?.displayName
                    MotoCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    log.action.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    listOfNotNull(
                                        log.entityType.replace("_", " "),
                                        actor,
                                        log.deviceName
                                    ).joinToString(" • "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    log.createdAt.replace("T", " ").take(19),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val detail = log.newData ?: log.oldData
                                detail?.let {
                                    Text(
                                        it.toString().take(420),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupExportTab(context: ShopContext) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportContent by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun writeExport(uri: android.net.Uri?) {
        if (uri == null) {
            exportContent = null
            return
        }
        val content = exportContent ?: return
        runCatching {
            androidContext.contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open the selected file.")
        }.onSuccess {
            message = "Export saved successfully."
        }.onFailure {
            message = StoreRepository.userMessage(it)
        }
        exportContent = null
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> writeExport(uri) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> writeExport(uri) }

    fun exportProducts() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                val rows = StoreRepository.products(context.shop.id)
                buildString {
                    appendLine("sku,barcode,name,brand,cost_price,selling_price,stock,unit,reorder_level,shelf_location")
                    rows.forEach { p ->
                        appendLine(
                            listOf(
                                p.sku,
                                p.barcode.orEmpty(),
                                p.name,
                                p.brand.orEmpty(),
                                p.costPrice.toString(),
                                p.sellingPrice.toString(),
                                p.stockQuantity.toString(),
                                p.unit,
                                p.reorderLevel.toString(),
                                p.shelfLocation.orEmpty()
                            ).joinToString(",") { csvCell(it) }
                        )
                    }
                }
            }.onSuccess {
                exportContent = it
                csvLauncher.launch("StorePOS-products-" + LocalDate.now() + ".csv")
            }.onFailure { message = StoreRepository.userMessage(it) }
            busy = false
        }
    }

    fun exportCustomers() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                val rows = StoreRepository.customers(context.shop.id)
                buildString {
                    appendLine("name,phone,email,address,loyalty_points,credit_limit,store_credit_balance")
                    rows.forEach { row ->
                        appendLine(
                            listOf(
                                row.name,
                                row.phone.orEmpty(),
                                row.email.orEmpty(),
                                row.address.orEmpty(),
                                row.loyaltyPoints.toString(),
                                row.creditLimit.toString(),
                                row.storeCreditBalance.toString()
                            ).joinToString(",") { csvCell(it) }
                        )
                    }
                }
            }.onSuccess {
                exportContent = it
                csvLauncher.launch("StorePOS-customers-" + LocalDate.now() + ".csv")
            }.onFailure { message = StoreRepository.userMessage(it) }
            busy = false
        }
    }

    fun exportSales() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                val rows = StoreRepository.sales(context.shop.id)
                buildString {
                    appendLine("sale_number,status,subtotal,discount,tax,total,amount_tendered,change_due,created_at")
                    rows.forEach { row ->
                        appendLine(
                            listOf(
                                row.saleNumber,
                                row.status,
                                row.subtotal.toString(),
                                row.discountAmount.toString(),
                                row.taxAmount.toString(),
                                row.totalAmount.toString(),
                                row.amountTendered?.toString().orEmpty(),
                                row.changeDue?.toString().orEmpty(),
                                row.createdAt.orEmpty()
                            ).joinToString(",") { csvCell(it) }
                        )
                    }
                }
            }.onSuccess {
                exportContent = it
                csvLauncher.launch("StorePOS-sales-" + LocalDate.now() + ".csv")
            }.onFailure { message = StoreRepository.userMessage(it) }
            busy = false
        }
    }

    fun exportFullBackup() {
        scope.launch {
            busy = true
            message = null
            runCatching {
                coroutineScope {
                    val products = async { StoreRepository.products(context.shop.id) }
                    val customers = async { StoreRepository.customers(context.shop.id) }
                    val suppliers = async { StoreRepository.suppliers(context.shop.id) }
                    val sales = async { StoreRepository.sales(context.shop.id) }
                    val expenses = async { StoreRepository.expenses(context.shop.id) }
                    val backup = StoreBackup(
                        exportedAt = Instant.now().toString(),
                        shop = context.shop,
                        products = products.await(),
                        customers = customers.await(),
                        suppliers = suppliers.await(),
                        sales = sales.await(),
                        expenses = expenses.await()
                    )
                    Json {
                        prettyPrint = true
                        encodeDefaults = true
                    }.encodeToString(backup)
                }
            }.onSuccess {
                exportContent = it
                jsonLauncher.launch("StorePOS-backup-" + LocalDate.now() + ".json")
            }.onFailure { message = StoreRepository.userMessage(it) }
            busy = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Backup, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Local Backup & Export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Save StorePOS data directly to this Android device without opening the web dashboard.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("CSV exports", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportProducts() }, enabled = !busy) {
                        Icon(Icons.Rounded.Inventory2, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Products")
                    }
                    OutlinedButton(onClick = { exportCustomers() }, enabled = !busy) {
                        Icon(Icons.Rounded.Groups, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Customers")
                    }
                    OutlinedButton(onClick = { exportSales() }, enabled = !busy) {
                        Icon(Icons.Rounded.ReceiptLong, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Sales")
                    }
                }
            }
        }

        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Full JSON shop backup", fontWeight = FontWeight.Bold)
                Text(
                    "Includes shop profile, products, customers, suppliers, sales and expenses. Keep the file private because it contains business data.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { exportFullBackup() }, enabled = !busy) {
                    Icon(Icons.Rounded.SaveAlt, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (busy) "Preparing…" else "Save full backup")
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("Export saved")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Restore safety", fontWeight = FontWeight.Bold)
                Text(
                    "Backups are export-only on Android. Restore remains in StorePOS Cloud so imports can be validated before replacing production records.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun csvCell(value: String): String {
    val safe = value.replace(""", """")
    return """ + safe + """
}

@Composable
private fun DiagnosticsTab(
    pendingCount: Int,
    alerts: List<ShopAlert>,
    health: JsonObject?,
    latestVersion: AppVersion?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("System Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ListItem(
                    headlineContent = { Text("Installed StorePOS") },
                    supportingContent = {
                        Text("Version " + BuildConfig.VERSION_NAME + " • code " + BuildConfig.VERSION_CODE)
                    },
                    leadingContent = { Icon(Icons.Rounded.Android, null) }
                )
                ListItem(
                    headlineContent = { Text("Latest published version") },
                    supportingContent = {
                        Text(
                            latestVersion?.let {
                                "v" + it.versionName + " • code " + it.versionCode
                            } ?: "Unable to check"
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) }
                )
                ListItem(
                    headlineContent = { Text("Offline queue") },
                    supportingContent = { Text(pendingCount.toString() + " pending transaction(s)") },
                    leadingContent = { Icon(Icons.Rounded.CloudQueue, null) }
                )
            }
        }

        item {
            MotoCard(Modifier.fillMaxWidth()) {
                Text("Data Health", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (health == null) {
                    Text("Data Health is temporarily unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (health.isEmpty()) {
                    Text("No health issues reported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    health.entries.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.key.replace("_", " ").replaceFirstChar { it.uppercase() })
                            Text(entry.value.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("Current alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (alerts.isEmpty()) {
            item { EmptyView("No active alerts", "StorePOS Cloud did not report any current shop warnings.") }
        } else {
            items(alerts.take(30), key = { it.code }) { alert ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (alert.severity.lowercase()) {
                                "critical" -> Icons.Rounded.Error
                                "warning" -> Icons.Rounded.Warning
                                else -> Icons.Rounded.Info
                            },
                            null,
                            tint = when (alert.severity.lowercase()) {
                                "critical" -> MaterialTheme.colorScheme.error
                                "warning" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(alert.title, fontWeight = FontWeight.Bold)
                            Text(alert.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
