package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun SuppliersPage(context: ShopContext) {
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var orders by remember { mutableStateOf<List<PurchaseOrder>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var poOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var addOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() = coroutineScope {
        val s = async { StoreRepository.suppliers(context.shop.id) }
        val o = async { StoreRepository.purchaseOrders(context.shop.id) }
        val p = async { StoreRepository.products(context.shop.id) }
        suppliers = s.await()
        orders = o.await()
        products = p.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading suppliers…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                "Suppliers & Purchasing",
                "${suppliers.size} supplier(s)",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { addOpen = true }) {
                            Icon(Icons.Rounded.AddBusiness, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Supplier")
                        }
                        Button(
                            onClick = { poOpen = true },
                            enabled = suppliers.isNotEmpty() && products.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.ShoppingCartCheckout, null)
                            Spacer(Modifier.width(6.dp))
                            Text("New PO")
                        }
                    }
                }
            )
      }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item {
            Text("Suppliers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (suppliers.isEmpty()) {
            item { EmptyView("No suppliers", "Add suppliers for purchase-order tracking.") }
      } else {
            items(suppliers, key = { it.id }) { supplier ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Text(supplier.name, fontWeight = FontWeight.Bold)
                    supplier.contactPerson?.let { Text(it) }
                    Text(
                        listOfNotNull(supplier.phone, supplier.email).joinToString(" • ")
                            .ifBlank { "No contact information" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Purchase orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        if (orders.isEmpty()) {
            item { Text("No purchase orders yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(orders.take(20), key = { it.id }) { order ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(order.poNumber, fontWeight = FontWeight.Bold)
                            Text(
                                suppliers.firstOrNull { it.id == order.supplierId }?.name ?: "Supplier",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(order.status)
                            Text(money(order.totalAmount), fontWeight = FontWeight.Bold)
                            if (order.status in listOf("draft", "ordered", "partial")) {
                                TextButton(onClick = {
                                    scope.launch {
                                        error = null
                                        runCatching {
                                            val items = StoreRepository.purchaseOrderItems(order.id)
                                            StoreRepository.receivePurchaseOrder(order.id, items)
                                        }.onSuccess { refresh() }
                                            .onFailure { error = StoreRepository.userMessage(it) }
                                    }
                                }) {
                                    Icon(Icons.Rounded.Inventory, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Receive all")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (poOpen) {
        AddPurchaseOrderDialog(
            suppliers = suppliers,
            products = products,
            onDismiss = { poOpen = false },
            onSave = { supplierId, lines, notes, expectedAt ->
                scope.launch {
                    error = null
                    runCatching {
                        StoreRepository.createPurchaseOrder(
                            context.shop.id,
                            supplierId,
                            lines,
                            notes,
                            expectedAt
                        )
                    }.onSuccess {
                        poOpen = false
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (addOpen) {
        AddSupplierDialog(
            context,
            onDismiss = { addOpen = false },
            onSave = { input ->
                scope.launch {
                    runCatching { StoreRepository.addSupplier(input) }
                        .onSuccess {
                            addOpen = false
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }
}

@Composable
private fun AddSupplierDialog(
    context: ShopContext,
    onDismiss: () -> Unit,
    onSave: (SupplierInsert) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Supplier name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact person") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SupplierInsert(
                            shopId = context.shop.id,
                            name = name.trim(),
                            contactPerson = contact.trim().ifBlank { null },
                            phone = phone.trim().ifBlank { null },
                            email = email.trim().ifBlank { null },
                            address = address.trim().ifBlank { null }
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


private data class PoLineDraft(
    val product: Product,
    val quantity: Double,
    val unitCost: Double
)

@Composable
private fun AddPurchaseOrderDialog(
    suppliers: List<Supplier>,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (String, List<Triple<Product, Double, Double>>, String?, String?) -> Unit
) {
    var supplier by remember { mutableStateOf<Supplier?>(suppliers.firstOrNull()) }
    var supplierMenu by remember { mutableStateOf(false) }
    var product by remember { mutableStateOf<Product?>(products.firstOrNull()) }
    var productMenu by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf("1") }
    var cost by remember { mutableStateOf(product?.costPrice?.toString().orEmpty()) }
    var lines by remember { mutableStateOf<List<PoLineDraft>>(emptyList()) }
    var notes by remember { mutableStateOf("") }
    var expectedAt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New purchase order") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.heightIn(max = 580.dp)
            ) {
                item {
                    Box {
                        OutlinedButton(onClick = { supplierMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(supplier?.name ?: "Select supplier")
                        }
                        DropdownMenu(supplierMenu, { supplierMenu = false }) {
                            suppliers.forEach { s ->
                                DropdownMenuItem(text = { Text(s.name) }, onClick = {
                                    supplier = s
                                    supplierMenu = false
                                })
                            }
                        }
                    }
                }
                item { Text("Add items", fontWeight = FontWeight.Bold) }
                item {
                    Box {
                        OutlinedButton(onClick = { productMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(product?.name ?: "Select product")
                        }
                        DropdownMenu(productMenu, { productMenu = false }) {
                            products.filter { it.isActive }.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name + " • " + p.sku) }, onClick = {
                                    product = p
                                    cost = p.costPrice.toString()
                                    productMenu = false
                                })
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            qty, { qty = it }, label = { Text("Qty") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            cost, { cost = it }, label = { Text("Unit cost") },
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        Button(
                            onClick = {
                                val p = product ?: return@Button
                                val q = qty.toDoubleOrNull() ?: return@Button
                                val uc = cost.toDoubleOrNull() ?: return@Button
                                if (q > 0 && uc >= 0) {
                                    lines = lines + PoLineDraft(p, q, uc)
                                    qty = "1"
                                }
                            },
                            enabled = product != null && (qty.toDoubleOrNull() ?: 0.0) > 0
                        ) { Icon(Icons.Rounded.Add, null) }
                    }
                }
                items(lines, key = { it.product.id + it.quantity.toString() + it.unitCost.toString() }) { line ->
                    ListItem(
                        headlineContent = { Text(line.product.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(line.quantity.toString() + " × " + money(line.unitCost)) },
                        trailingContent = {
                            IconButton(onClick = { lines = lines - line }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove")
                            }
                        }
                    )
                }
                item {
                    Text(
                        "PO total: " + money(lines.sumOf { it.quantity * it.unitCost }),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                item {
                    OutlinedTextField(
                        expectedAt, { expectedAt = it },
                        label = { Text("Expected date YYYY-MM-DD (optional)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        notes, { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = supplier ?: return@Button
                    onSave(
                        s.id,
                        lines.map { Triple(it.product, it.quantity, it.unitCost) },
                        notes.trim().ifBlank { null },
                        expectedAt.trim().ifBlank { null }
                    )
                },
                enabled = supplier != null && lines.isNotEmpty()
            ) { Text("Create PO") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
