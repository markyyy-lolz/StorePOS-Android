package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class QuoteDraftLine(
    val key: String,
    val itemType: String,
    val productId: String? = null,
    val serviceId: String? = null,
    val description: String,
    val quantity: Double,
    val unitPrice: Double
) {
    val total: Double get() = quantity * unitPrice
}

@Composable
fun QuotationsPage(context: ShopContext) {
    var quotes by remember { mutableStateOf<List<Quotation>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var motorcycles by remember { mutableStateOf<List<Motorcycle>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var services by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var addOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() = coroutineScope {
        val q = async { StoreRepository.quotations(context.shop.id) }
        val c = async { StoreRepository.customers(context.shop.id) }
        val m = async { StoreRepository.motorcycles(context.shop.id) }
        val p = async { StoreRepository.products(context.shop.id) }
        val s = async { StoreRepository.services(context.shop.id) }
        quotes = q.await()
        customers = c.await()
        motorcycles = m.await()
        products = p.await()
        services = s.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) return LoadingView("Loading quotations…")

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            PageHeader(
                "Quotations & Estimates",
                "Prepare parts + labor estimates and convert approved quotes to job orders",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                            }
                        }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                        Button(onClick = { addOpen = true }) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("New quote")
                        }
                    }
                }
            )
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        if (quotes.isEmpty()) {
            item { EmptyView("No quotations yet", "Create an estimate for parts, labor, or service work.") }
        } else {
            items(quotes, key = { it.id }) { quote ->
                val customer = customers.firstOrNull { it.id == quote.customerId }
                val bike = motorcycles.firstOrNull { it.id == quote.motorcycleId }
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(quote.quoteNumber, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text(customer?.name ?: "Walk-in / no customer", fontWeight = FontWeight.SemiBold)
                            if (bike != null) {
                                Text(
                                    bike.make + " " + bike.model + (bike.plateNumber?.let { " • " + it } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(quote.status)
                            Text(money(quote.totalAmount), fontWeight = FontWeight.Black)
                        }
                    }

                    quote.validUntil?.let {
                        Text("Valid until $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (quote.status != "converted" && quote.customerId != null && quote.motorcycleId != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    error = null
                                    runCatching { StoreRepository.convertQuotationToJob(quote.id) }
                                        .onSuccess { refresh() }
                                        .onFailure { error = StoreRepository.userMessage(it) }
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Rounded.Handyman, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Convert to Job Order")
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddQuotationDialog(
            context = context,
            customers = customers,
            motorcycles = motorcycles,
            products = products,
            services = services,
            onDismiss = { addOpen = false },
            onSave = { customerId, bikeId, lines, discount, validUntil, notes ->
                scope.launch {
                    error = null
                    val payload = lines.map {
                        mapOf<String, Any?>(
                            "item_type" to it.itemType,
                            "product_id" to it.productId,
                            "service_id" to it.serviceId,
                            "description" to it.description,
                            "quantity" to it.quantity,
                            "unit_price" to it.unitPrice
                        )
                    }
                    runCatching {
                        StoreRepository.createQuotation(
                            context.shop.id,
                            customerId,
                            bikeId,
                            payload,
                            discount,
                            validUntil,
                            notes
                        )
                    }.onSuccess {
                        addOpen = false
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }
}

@Composable
private fun AddQuotationDialog(
    context: ShopContext,
    customers: List<Customer>,
    motorcycles: List<Motorcycle>,
    products: List<Product>,
    services: List<ServiceItem>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, List<QuoteDraftLine>, Double, String?, String?) -> Unit
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    var bike by remember { mutableStateOf<Motorcycle?>(null) }
    var customerMenu by remember { mutableStateOf(false) }
    var bikeMenu by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("part") }
    var modeMenu by remember { mutableStateOf(false) }
    var product by remember { mutableStateOf<Product?>(null) }
    var productMenu by remember { mutableStateOf(false) }
    var service by remember { mutableStateOf<ServiceItem?>(null) }
    var serviceMenu by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<QuoteDraftLine>>(emptyList()) }
    var discount by remember { mutableStateOf("0") }
    var validUntil by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var notes by remember { mutableStateOf("") }

    val bikes = motorcycles.filter { it.customerId == customer?.id }
    val subtotal = lines.sumOf { it.total }
    val total = (subtotal - (discount.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)

    fun resetLine() {
        product = null
        service = null
        description = ""
        qty = "1"
        price = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New quotation") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 620.dp)
            ) {
                item {
                    Box {
                        OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(customer?.name ?: "Customer (optional)")
                        }
                        DropdownMenu(customerMenu, { customerMenu = false }) {
                            DropdownMenuItem(text = { Text("No customer") }, onClick = { customer = null; bike = null; customerMenu = false })
                            customers.forEach { c ->
                                DropdownMenuItem(text = { Text(c.name) }, onClick = { customer = c; bike = null; customerMenu = false })
                            }
                        }
                    }
                }
                item {
                    Box {
                        OutlinedButton(
                            onClick = { bikeMenu = true },
                            enabled = customer != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(bike?.let { it.make + " " + it.model + " " + (it.plateNumber ?: "") } ?: "Motorcycle (optional)")
                        }
                        DropdownMenu(bikeMenu, { bikeMenu = false }) {
                            bikes.forEach { b ->
                                DropdownMenuItem(text = { Text(b.make + " " + b.model) }, onClick = { bike = b; bikeMenu = false })
                            }
                        }
                    }
                }

                item { HorizontalDivider() }
                item { Text("Add quote line", fontWeight = FontWeight.Bold) }
                item {
                    Box {
                        OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Line type: " + mode.uppercase())
                        }
                        DropdownMenu(modeMenu, { modeMenu = false }) {
                            listOf("part","service","labor","other").forEach { type ->
                                DropdownMenuItem(text = { Text(type.uppercase()) }, onClick = {
                                    mode = type
                                    resetLine()
                                    modeMenu = false
                                })
                            }
                        }
                    }
                }

                if (mode == "part") {
                    item {
                        Box {
                            OutlinedButton(onClick = { productMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(product?.name ?: "Select inventory part")
                            }
                            DropdownMenu(productMenu, { productMenu = false }) {
                                products.filter { it.isActive }.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.name + " • " + p.sku) }, onClick = {
                                        product = p
                                        description = p.name
                                        price = p.sellingPrice.toString()
                                        productMenu = false
                                    })
                                }
                            }
                        }
                    }
                } else if (mode == "service" && services.isNotEmpty()) {
                    item {
                        Box {
                            OutlinedButton(onClick = { serviceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(service?.name ?: "Select service")
                            }
                            DropdownMenu(serviceMenu, { serviceMenu = false }) {
                                services.forEach { s ->
                                    DropdownMenuItem(text = { Text(s.name) }, onClick = {
                                        service = s
                                        description = s.name
                                        price = s.basePrice.toString()
                                        serviceMenu = false
                                    })
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            qty, { qty = it }, label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            price, { price = it }, label = { Text("Unit price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        Button(
                            onClick = {
                                val q = qty.toDoubleOrNull() ?: return@Button
                                val p = price.toDoubleOrNull() ?: return@Button
                                if (description.isNotBlank() && q > 0 && p >= 0) {
                                    lines = lines + QuoteDraftLine(
                                        key = java.util.UUID.randomUUID().toString(),
                                        itemType = mode,
                                        productId = product?.id,
                                        serviceId = service?.id,
                                        description = description.trim(),
                                        quantity = q,
                                        unitPrice = p
                                    )
                                    resetLine()
                                }
                            },
                            enabled = description.isNotBlank() && (qty.toDoubleOrNull() ?: 0.0) > 0 && price.toDoubleOrNull() != null
                        ) { Icon(Icons.Rounded.Add, null) }
                    }
                }

                items(lines, key = { it.key }) { line ->
                    ListItem(
                        headlineContent = { Text(line.description, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(line.itemType.uppercase() + " • " + line.quantity + " × " + money(line.unitPrice)) },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(line.total), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { lines = lines - line }) { Text("Remove") }
                            }
                        }
                    )
                }

                item { HorizontalDivider() }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            discount, { discount = it }, label = { Text("Discount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            validUntil, { validUntil = it }, label = { Text("Valid until") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                }
                item { Text("Subtotal: " + money(subtotal) + "   Total: " + money(total), fontWeight = FontWeight.Black) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        customer?.id,
                        bike?.id,
                        lines,
                        discount.toDoubleOrNull() ?: 0.0,
                        validUntil.trim().ifBlank { null },
                        notes.trim().ifBlank { null }
                    )
                },
                enabled = lines.isNotEmpty()
            ) { Text("Create quotation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
