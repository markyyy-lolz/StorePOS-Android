package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

private data class DashboardAction(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun DashboardPage(
    context: ShopContext,
    entitlements: PlanEntitlements,
    onOpenPos: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    onOpenService: () -> Unit = {},
    onOpenQuotations: () -> Unit = {},
    onOpenOperations: () -> Unit = {}
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var jobs by remember { mutableStateOf<List<JobOrder>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var heldSales by remember { mutableStateOf<List<HeldSale>>(emptyList()) }
    var receivables by remember { mutableStateOf<List<CustomerReceivable>>(emptyList()) }
    var reminders by remember { mutableStateOf<List<ServiceReminder>>(emptyList()) }
    var inventoryCounts by remember { mutableStateOf<List<InventoryCount>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val role = context.member.role.lowercase()
    val features = entitlements.features.toSet()
    val canSeeFinancials = role in setOf("owner", "admin", "manager")
    val hasService = "service_jobs" in features
    val hasOperations = "operations" in features
    val hasReceivables = "receivables" in features
    val hasCounts = "inventory_counts" in features

    LaunchedEffect(context.shop.id, context.member.role) {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val p = async { StoreRepository.products(context.shop.id) }
                val s = async { StoreRepository.sales(context.shop.id) }
                val j = if (hasService) async { StoreRepository.jobs(context.shop.id) } else null
                val e = if (canSeeFinancials && hasOperations) async { StoreRepository.expenses(context.shop.id) } else null

                val held = async {
                    runCatching { StoreRepository.heldSales(context.shop.id) }.getOrDefault(emptyList())
                }
                val recv = async {
                    if (canSeeFinancials && hasReceivables) {
                        runCatching { StoreRepository.receivables(context.shop.id) }.getOrDefault(emptyList())
                    } else emptyList()
                }
                val rem = async {
                    if (hasService) runCatching { StoreRepository.serviceReminders(context.shop.id) }.getOrDefault(emptyList()) else emptyList()
                }
                val counts = async {
                    if (hasCounts) runCatching { StoreRepository.inventoryCounts(context.shop.id) }.getOrDefault(emptyList()) else emptyList()
                }

                products = p.await()
                sales = s.await()
                jobs = j?.await() ?: emptyList()
                expenses = e?.await() ?: emptyList()
                heldSales = held.await()
                receivables = recv.await()
                reminders = rem.await()
                inventoryCounts = counts.await()
            }
        }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading command center…")
        return
    }

    val today = LocalDate.now().toString()
    val todaySales = sales.filter { (it.createdAt ?: "").startsWith(today) && it.status == "completed" }
    val todayRevenue = todaySales.sumOf { it.totalAmount }
    val lowStock = products.filter { it.trackStock && it.stockQuantity <= it.reorderLevel && it.isActive }
    val activeJobs = jobs.filter { it.status !in listOf("released", "cancelled") }
    val todayExpense = expenses.filter { it.expenseDate == today }.sumOf { it.amount }
    val openReceivables = receivables.filter { it.status in listOf("open", "partial") }
    val outstandingBalance = openReceivables.sumOf { it.balance }
    val dueReminders = reminders.filter {
        it.status == "pending" && !it.dueDate.isNullOrBlank() && it.dueDate!! <= today
    }
    val pendingCounts = inventoryCounts.filter { it.status !in listOf("approved", "cancelled", "completed") }

    val quickActions = buildList {
        if ("pos" in features) add(DashboardAction("New sale", "Open checkout", Icons.Rounded.PointOfSale, onOpenPos))
        if (hasService) add(DashboardAction("Service job", "Workshop queue", Icons.Rounded.Build, onOpenService))
        if ("inventory" in features) add(DashboardAction("Inventory", "Stock & counts", Icons.Rounded.Inventory2, onOpenInventory))
        if ("quotations" in features) add(DashboardAction("Quotation", "Create estimate", Icons.Rounded.RequestQuote, onOpenQuotations))
        if (hasOperations) add(DashboardAction("Operations", "Shifts & controls", Icons.Rounded.Tune, onOpenOperations))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            PageHeader(
                title = "Command Center",
                subtitle = "${context.shop.name} • ${context.member.role.replaceFirstChar { it.uppercase() }}"
            )
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item {
            BoxWithConstraints {
                val cols = if (maxWidth >= 720.dp) 4 else 2
                val metrics = listOf(
                    Triple("Today's sales", money(todayRevenue), Icons.Rounded.PointOfSale),
                    Triple("Transactions", todaySales.size.toString(), Icons.Rounded.TrendingUp),
                    Triple(if (hasService) "Active jobs" else "Products", if (hasService) activeJobs.size.toString() else products.size.toString(), if (hasService) Icons.Rounded.Build else Icons.Rounded.Inventory2),
                    Triple("Low stock", lowStock.size.toString(), Icons.Rounded.Inventory2)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.chunked(cols).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { (label, value, icon) ->
                                MetricTile(label, value, icon, Modifier.weight(1f))
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        item {
            PageHeader("Quick actions", "Jump straight into the most-used StorePOS workflows")
        }

        item {
            BoxWithConstraints {
                val cols = if (maxWidth >= 920.dp) 5 else if (maxWidth >= 620.dp) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickActions.chunked(cols).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { action ->
                                QuickActionButton(action, Modifier.weight(1f))
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        item {
            PageHeader("Attention center", "Items that may need action today")
        }

        item {
            BoxWithConstraints {
                val cols = if (maxWidth >= 720.dp) 4 else 2
                val attention = buildList {
                    add(Triple("Held sales", heldSales.size.toString(), Icons.Rounded.PauseCircle))
                    if (hasService) add(Triple("Due reminders", dueReminders.size.toString(), Icons.Rounded.NotificationsActive))
                    if (hasCounts) add(Triple("Pending counts", pendingCounts.size.toString(), Icons.Rounded.Inventory2))
                    if (canSeeFinancials && hasReceivables) {
                        add(Triple("Receivables", money(outstandingBalance), Icons.Rounded.AccountBalanceWallet))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    attention.chunked(cols).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { (label, value, icon) ->
                                MetricTile(label, value, icon, Modifier.weight(1f))
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MotoCard(Modifier.weight(1f)) {
                    Text("Revenue today", style = MaterialTheme.typography.labelLarge)
                    Text(
                        money(todayRevenue),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${todaySales.size} completed transaction(s)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MotoCard(Modifier.weight(1f)) {
                    Text(
                        if (canSeeFinancials && hasOperations) "Net today" else "Shop activity",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (canSeeFinancials && hasOperations) money(todayRevenue - todayExpense) else if (hasService) "${activeJobs.size} active jobs" else "${products.size} products",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (canSeeFinancials && hasOperations) "Expenses ${money(todayExpense)}"
                        else "${lowStock.size} low-stock item(s)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (hasService) {
            item { PageHeader("Workshop queue", "Recent active service jobs") }

        if (activeJobs.isEmpty()) {
            item { EmptyView("No active service jobs", "New job orders will appear here.") }
        } else {
            items(activeJobs.take(6), key = { it.id }) { job ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                job.jobNumber,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(job.complaint ?: "No complaint recorded", maxLines = 2)
                        }
                        StatusPill(job.status)
                    }
                }
            }
        }

        if (dueReminders.isNotEmpty()) {
            item { PageHeader("Service reminders due", "${dueReminders.size} customer reminder(s) need attention") }
            items(dueReminders.take(6), key = { it.id }) { reminder ->
                ListItem(
                    headlineContent = { Text(reminder.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                reminder.dueDate?.let { "Due $it" },
                                reminder.dueOdometerKm?.let { "${it.toInt()} km" }
                            ).joinToString(" • ")
                        )
                    },
                    trailingContent = { StatusPill(reminder.status) }
                )
            }
        }
        }

        if (lowStock.isNotEmpty()) {
            item { PageHeader("Low-stock attention", "${lowStock.size} item(s) need restocking") }
            items(lowStock.take(8), key = { it.id }) { product ->
                ListItem(
                    headlineContent = { Text(product.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${product.sku} • Reorder at ${product.reorderLevel}") },
                    trailingContent = {
                        Text(
                            "${product.stockQuantity} ${product.unit}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    action: DashboardAction,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = 88.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(action.label, fontWeight = FontWeight.Bold)
            Text(
                action.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    MotoCard(modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}
