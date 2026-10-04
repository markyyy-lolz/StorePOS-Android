package com.storepos.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.Product
import com.storepos.app.data.model.ProductInsert
import com.storepos.app.data.model.ShopContext
import com.storepos.app.data.model.InventoryCount
import com.storepos.app.data.model.InventoryCountItem
import com.storepos.app.ui.components.*
import kotlinx.coroutines.launch
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun InventoryPage(context: ShopContext) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var addOpen by remember { mutableStateOf(false) }
    var editProduct by remember { mutableStateOf<Product?>(null) }
    var stockProduct by remember { mutableStateOf<Product?>(null) }
    var counts by remember { mutableStateOf<List<InventoryCount>>(emptyList()) }
    var stocktake by remember { mutableStateOf<InventoryCount?>(null) }
    var stocktakeItems by remember { mutableStateOf<List<InventoryCountItem>>(emptyList()) }
    var stocktakeLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val inventoryScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents?.trim().orEmpty()
        if (code.isNotBlank()) {
            query = code
            val match = products.firstOrNull {
                it.barcode.equals(code, ignoreCase = true) || it.sku.equals(code, ignoreCase = true)
            }
            error = if (match == null) "Barcode not found in inventory: $code" else null
        }
    }

    fun scanInventoryBarcode() {
        inventoryScanner.launch(
            ScanOptions()
                .setPrompt("Scan inventory barcode")
                .setBeepEnabled(true)
                .setOrientationLocked(false)
        )
    }

    suspend fun refresh() {
        products = StoreRepository.products(context.shop.id)
        counts = StoreRepository.inventoryCounts(context.shop.id)
    }

    suspend fun openStocktake() {
        stocktakeLoading = true
        val active = counts.firstOrNull { it.status in listOf("open", "submitted") }
            ?: StoreRepository.startInventoryCount(context.shop.id)
        stocktake = active
        stocktakeItems = StoreRepository.inventoryCountItems(active.id)
        stocktakeLoading = false
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading inventory…")
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader(
            "Inventory",
            "${products.size} product(s)",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                error = null
                                runCatching { openStocktake() }
                                    .onFailure {
                                        stocktakeLoading = false
                                        error = StoreRepository.userMessage(it)
                                    }
                            }
                        },
                        enabled = products.any { it.isActive && it.trackStock } && !stocktakeLoading
                    ) {
                        Icon(Icons.Rounded.FactCheck, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (stocktakeLoading) "Loading…" else "Stocktake")
                    }
                    Button(onClick = { addOpen = true }) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add product")
                    }
                }
            }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search SKU, barcode, name or brand") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            FilledIconButton(onClick = { scanInventoryBarcode() }) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan barcode")
            }
        }

        val filtered = products.filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.sku.contains(query, true) ||
                (it.barcode?.contains(query, true) == true) ||
                (it.brand?.contains(query, true) == true)
        }

        if (filtered.isEmpty()) {
            EmptyView("No inventory items", "Add a product to begin tracking stock.", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { product ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${product.sku}${product.brand?.let { " • $it" } ?: ""}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Stock ${product.stockQuantity} ${product.unit} • Reorder ${product.reorderLevel}",
                                    color = if (product.stockQuantity <= product.reorderLevel)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(product.sellingPrice), fontWeight = FontWeight.Black)
                                Text(
                                    "Cost ${money(product.costPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    IconButton(onClick = { editProduct = product }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit product")
                                    }
                                    IconButton(onClick = { stockProduct = product }) {
                                        Icon(Icons.Rounded.SyncAlt, contentDescription = "Adjust stock")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddProductDialog(
            context = context,
            onDismiss = { addOpen = false },
            onSave = { input ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.addProduct(input) }
                        .onSuccess {
                            addOpen = false
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    editProduct?.let { product ->
        EditProductDialog(
            product = product,
            onDismiss = { editProduct = null },
            onSave = { updated ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.updateProduct(updated) }
                        .onSuccess {
                            editProduct = null
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    stockProduct?.let { product ->
        StockAdjustmentDialog(
            product = product,
            onDismiss = { stockProduct = null },
            onSave = { delta, reason, notes ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.adjustInventoryStock(product.id, delta, reason, notes) }
                        .onSuccess {
                            stockProduct = null
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    stocktake?.let { count ->
        StocktakeDialog(
            count = count,
            items = stocktakeItems,
            products = products,
            canApprove = context.member.role.lowercase() in listOf("owner", "admin", "manager"),
            onDismiss = {
                stocktake = null
                stocktakeItems = emptyList()
            },
            onSubmit = { values ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.submitInventoryCount(count.id, values) }
                        .onSuccess { submitted ->
                            stocktake = submitted
                            stocktakeItems = StoreRepository.inventoryCountItems(submitted.id)
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            },
            onApprove = {
                scope.launch {
                    error = null
                    runCatching { StoreRepository.approveInventoryCount(count.id) }
                        .onSuccess {
                            stocktake = null
                            stocktakeItems = emptyList()
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }
}

@Composable
private fun AddProductDialog(
    context: ShopContext,
    onDismiss: () -> Unit,
    onSave: (ProductInsert) -> Unit
) {
    var sku by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var partNumber by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var reorder by remember { mutableStateOf("5") }
    val barcodeScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.trim()?.takeIf { it.isNotBlank() }?.let { barcode = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add product") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 560.dp)
            ) {
                item { OutlinedTextField(sku, { sku = it }, label = { Text("SKU") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            barcode,
                            { barcode = it },
                            label = { Text("Barcode (optional)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledIconButton(onClick = {
                            barcodeScanner.launch(
                                ScanOptions()
                                    .setPrompt("Scan product barcode")
                                    .setBeepEnabled(true)
                                    .setOrientationLocked(false)
                            )
                        }) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan product barcode")
                        }
                    }
                }
                item { OutlinedTextField(name, { name = it }, label = { Text("Product name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(partNumber, { partNumber = it }, label = { Text("Part number") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            cost, { cost = it }, label = { Text("Cost") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            price, { price = it }, label = { Text("Selling") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            stock, { stock = it }, label = { Text("Opening stock") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            reorder, { reorder = it }, label = { Text("Reorder level") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ProductInsert(
                            shopId = context.shop.id,
                            sku = sku.trim(),
                            barcode = barcode.trim().ifBlank { null },
                            name = name.trim(),
                            brand = brand.trim().ifBlank { null },
                            partNumber = partNumber.trim().ifBlank { null },
                            costPrice = cost.toDoubleOrNull() ?: 0.0,
                            sellingPrice = price.toDoubleOrNull() ?: 0.0,
                            stockQuantity = stock.toDoubleOrNull() ?: 0.0,
                            reorderLevel = reorder.toDoubleOrNull() ?: 5.0
                        )
                    )
                },
                enabled = sku.isNotBlank() && name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun EditProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember(product.id) { mutableStateOf(product.name) }
    var brand by remember(product.id) { mutableStateOf(product.brand.orEmpty()) }
    var barcode by remember(product.id) { mutableStateOf(product.barcode.orEmpty()) }
    var partNumber by remember(product.id) { mutableStateOf(product.partNumber.orEmpty()) }
    var cost by remember(product.id) { mutableStateOf(product.costPrice.toString()) }
    var price by remember(product.id) { mutableStateOf(product.sellingPrice.toString()) }
    var reorder by remember(product.id) { mutableStateOf(product.reorderLevel.toString()) }
    var unit by remember(product.id) { mutableStateOf(product.unit) }
    var active by remember(product.id) { mutableStateOf(product.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit product") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 560.dp)
            ) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Product name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(partNumber, { partNumber = it }, label = { Text("Part number") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(cost, { cost = it }, label = { Text("Cost") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(price, { price = it }, label = { Text("Selling") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(reorder, { reorder = it }, label = { Text("Reorder level") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = active, onCheckedChange = { active = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (active) "Active product" else "Archived product")
                    }
                }
                item {
                    Text(
                        "Use Adjust Stock for quantity changes so every manual stock movement is recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        product.copy(
                            name = name.trim(),
                            brand = brand.trim().ifBlank { null },
                            barcode = barcode.trim().ifBlank { null },
                            partNumber = partNumber.trim().ifBlank { null },
                            costPrice = cost.toDoubleOrNull() ?: product.costPrice,
                            sellingPrice = price.toDoubleOrNull() ?: product.sellingPrice,
                            reorderLevel = reorder.toDoubleOrNull() ?: product.reorderLevel,
                            unit = unit.trim().ifBlank { "pc" },
                            isActive = active
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StockAdjustmentDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (Double, String, String?) -> Unit
) {
    var delta by remember(product.id) { mutableStateOf("") }
    var reason by remember(product.id) { mutableStateOf("adjustment") }
    var notes by remember(product.id) { mutableStateOf("") }
    var reasonMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    product.name + " • Current stock " + product.stockQuantity + " " + product.unit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    delta,
                    { delta = it },
                    label = { Text("Quantity change") },
                    supportingText = { Text("Use 10 to add or -2 to deduct") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Box {
                    OutlinedButton(onClick = { reasonMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reason: " + reason.replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(expanded = reasonMenu, onDismissRequest = { reasonMenu = false }) {
                        listOf("adjustment", "opening", "return", "damage", "theft").forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.replaceFirstChar { it.uppercase() }) },
                                onClick = { reason = item; reasonMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val amount = delta.toDoubleOrNull()
            Button(
                onClick = { if (amount != null) onSave(amount, reason, notes.trim().ifBlank { null }) },
                enabled = amount != null && amount != 0.0
            ) { Text("Apply adjustment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun StocktakeDialog(
    count: InventoryCount,
    items: List<InventoryCountItem>,
    products: List<Product>,
    canApprove: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (List<Pair<String, Double>>) -> Unit,
    onApprove: () -> Unit
) {
    var values by remember(count.id, items) {
        mutableStateOf(
            items.associate { item ->
                item.productId to (
                    if (item.countedQuantity % 1.0 == 0.0) item.countedQuantity.toInt().toString()
                    else item.countedQuantity.toString()
                )
            }
        )
    }
    val submitted = count.status == "submitted"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stocktake " + count.countNumber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (submitted)
                        "Review variances before approval. Approval posts inventory adjustment movements."
                    else
                        "Enter the physical quantity counted for every tracked item.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items, key = { it.productId }) { item ->
                        val product = products.firstOrNull { it.id == item.productId }
                        val currentText = values[item.productId].orEmpty()
                        val counted = currentText.toDoubleOrNull() ?: item.countedQuantity
                        val variance = counted - item.systemQuantity
                        Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(product?.name ?: item.productId.take(8), fontWeight = FontWeight.Bold)
                                    Text(
                                        "System " + item.systemQuantity + " • Variance " + variance,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (kotlin.math.abs(variance) < .001)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                                if (submitted) {
                                    Text(counted.toString(), fontWeight = FontWeight.Black)
                                } else {
                                    OutlinedTextField(
                                        value = currentText,
                                        onValueChange = { value ->
                                            values = values.toMutableMap().apply { put(item.productId, value) }
                                        },
                                        label = { Text("Counted") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(130.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (submitted) {
                Button(onClick = onApprove, enabled = canApprove) {
                    Text(if (canApprove) "Approve & post stock" else "Manager approval required")
                }
            } else {
                val parsed = items.mapNotNull { item ->
                    values[item.productId]?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { item.productId to it }
                }
                Button(
                    onClick = { onSubmit(parsed) },
                    enabled = parsed.size == items.size && items.isNotEmpty()
                ) { Text("Submit stocktake") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
