package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

@Composable
fun ReportsPage(context: ShopContext) {
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var analytics by remember { mutableStateOf<AnalyticsSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var addExpense by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() = coroutineScope {
        val s = async { StoreRepository.sales(context.shop.id) }
        val e = async { StoreRepository.expenses(context.shop.id) }
        val a = async { StoreRepository.analyticsSummary(context.shop.id, 30) }
        sales = s.await()
        expenses = e.await()
        analytics = a.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Preparing reports…")
        return
    }

    val completed = sales.filter { it.status == "completed" }
    val revenue = analytics?.revenue ?: completed.sumOf { it.totalAmount }
    val grossProfit = analytics?.grossProfit ?: 0.0
    val avgTicket = analytics?.avgTicket ?: if (completed.isNotEmpty()) revenue / completed.size else 0.0
    val discounts = completed.sumOf { it.discountAmount }
    val tax = completed.sumOf { it.taxAmount }
    val expenseTotal = analytics?.expenses ?: expenses.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
     ) {
        item {
            PageHeader(
                "Reports",
                "Advanced 30-day financial & product analytics",
                action = {
                    Button(onClick = { addExpense = true }) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Expense")
                    }
                }
            )
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Revenue", money(revenue), Modifier.weight(1f))
                ReportMetric("Expenses", money(expenseTotal), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Gross profit", money(grossProfit), Modifier.weight(1f))
                ReportMetric("Operating net", money(revenue - expenseTotal), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Avg ticket", money(avgTicket), Modifier.weight(1f))
                ReportMetric("Transactions", (analytics?.transactions ?: completed.size).toString(), Modifier.weight(1f))
            }
        }

        if (!analytics?.topProducts.isNullOrEmpty()) {
            item { Text("Top products", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(analytics!!.topProducts, key = { it.name }) { product ->
                ListItem(
                    headlineContent = { Text(product.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(product.qty.toString() + " units sold") },
                    trailingContent = { Text(money(product.sales), fontWeight = FontWeight.Bold) }
                )
            }
        }

        item { Text("Recent sales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (completed.isEmpty()) {
            item { EmptyView("No completed sales", "Sales reports will populate after checkout.") }
        } else {
            items(completed.take(30), key = { it.id }) { sale ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.ReceiptLong, null) },
                    headlineContent = { Text(sale.saleNumber, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(sale.createdAt ?: "") },
                    trailingContent = { Text(money(sale.totalAmount), fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (expenses.isNotEmpty()) {
            item { Text("Recent expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(expenses.take(20), key = { it.id }) { expense ->
                ListItem(
                    headlineContent = { Text(expense.description, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${expense.category} • ${expense.expenseDate}") },
                    trailingContent = { Text(money(expense.amount), color = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }

    if (addExpense) {
        AddExpenseDialog(
            context = context,
            onDismiss = { addExpense = false },
            onSave = { input ->
                scope.launch {
                    runCatching { StoreRepository.addExpense(input) }
                        .onSuccess {
                            addExpense = false
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier) {
    MotoCard(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AddExpenseDialog(
    context: ShopContext,
    onDismiss: () -> Unit,
    onSave: (ExpenseInsert) -> Unit
) {
    var category by remember { mutableStateOf("Shop") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(date, { date = it }, label = { Text("Date YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ExpenseInsert(
                            shopId = context.shop.id,
                            category = category.trim(),
                            description = description.trim(),
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            expenseDate = date.trim(),
                            createdBy = context.userId
                        )
                    )
                },
                enabled = description.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
