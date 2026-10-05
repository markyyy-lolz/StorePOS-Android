package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.money
import kotlin.math.abs
import kotlin.math.round

private data class PaymentDraft(
    val key: String = java.util.UUID.randomUUID().toString(),
    val method: String = "cash",
    val amount: String = "",
    val tendered: String = "",
    val reference: String = ""
)

@Composable
fun PosCheckoutDialog(
    cart: List<CartLine>,
    customers: List<Customer>,
    motorcycles: List<Motorcycle>,
    settings: ShopSettings,
    cashierRole: Boolean,
    hasPriceOverride: Boolean,
    offlineMode: Boolean,
    paymongoAvailable: Boolean = false,
    pricingSubtotal: Double? = null,
    pricingNote: String? = null,
    onDismiss: () -> Unit,
    onComplete: (String?, String?, List<CheckoutPayment>, Double, Double, String?, List<RetailCharge>, String?) -> Unit
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    var bike by remember { mutableStateOf<Motorcycle?>(null) }
    var customerMenu by remember { mutableStateOf(false) }
    var bikeMenu by remember { mutableStateOf(false) }
    var discountText by remember { mutableStateOf("0") }
    var managerPin by remember { mutableStateOf("") }
    var payments by remember { mutableStateOf(listOf(PaymentDraft())) }
    var chargeName by remember { mutableStateOf("") }
    var chargeAmountText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }

    val subtotal = pricingSubtotal ?: cart.sumOf { it.lineTotal }
    val chargeAmount = (chargeAmountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val charges = if (!offlineMode && chargeName.isNotBlank() && chargeAmount > 0) {
        listOf(RetailCharge(chargeName.trim(), chargeAmount))
    } else emptyList()
    val beforeDiscount = subtotal + charges.sumOf { it.amount }
    val discount = (discountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0).coerceAtMost(beforeDiscount)
    val taxable = (beforeDiscount - discount).coerceAtLeast(0.0)
    val tax = if (settings.taxEnabled) {
        round((taxable * settings.defaultTaxRate / 100.0) * 100.0) / 100.0
    } else 0.0
    val total = round((taxable + tax) * 100.0) / 100.0
    val discountPercent = if (beforeDiscount > 0) discount / beforeDiscount * 100.0 else 0.0
    val needsManagerPin = cashierRole &&
        settings.managerPinForDiscount &&
        (discountPercent > settings.cashierDiscountLimitPercent || hasPriceOverride)
    val customerBikes = motorcycles.filter { it.customerId == customer?.id }

    LaunchedEffect(total, payments.size) {
        if (payments.size == 1) {
            val p = payments.first()
            val target = String.format(java.util.Locale.US, "%.2f", total)
            if (p.amount != target) {
                payments = listOf(
                    p.copy(
                        amount = target,
                        tendered = if (p.method == "cash" && p.tendered.isBlank()) target else p.tendered
                    )
                )
            }
        }
    }

    val paymentModels = payments.mapNotNull { row ->
        val amount = row.amount.toDoubleOrNull() ?: return@mapNotNull null
        if (amount <= 0) return@mapNotNull null
        CheckoutPayment(
            method = row.method,
            amount = amount,
            tendered = if (row.method == "cash") row.tendered.toDoubleOrNull() else null,
            referenceNumber = row.reference.trim().ifBlank { null }
        )
    }
    val paymentTotal = paymentModels.sumOf { it.amount }
    val change = paymentModels.filter { it.method == "cash" }
        .sumOf { ((it.tendered ?: it.amount) - it.amount).coerceAtLeast(0.0) }

    val refsValid = payments.all {
        it.method !in listOf("gcash","maya","card","bank") || it.reference.trim().isNotBlank()
    }
    val cashValid = payments.all {
        it.method != "cash" || ((it.tendered.toDoubleOrNull() ?: 0.0) >= (it.amount.toDoubleOrNull() ?: Double.MAX_VALUE))
    }
    val customerMethodsValid = payments.all {
        it.method !in listOf("store_credit","credit") || customer != null
    }
    val storeCreditValid = payments.filter { it.method == "store_credit" }
        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 } <= (customer?.storeCreditBalance ?: 0.0) + 0.009
    val managerValid = !needsManagerPin || (!offlineMode && managerPin.length in 4..8)
    val paymongoValid = payments.none { it.method == "paymongo" } ||
        (!offlineMode && paymongoAvailable && payments.size == 1)
    val paymentValid = payments.isNotEmpty() &&
        paymentModels.size == payments.size &&
        abs(paymentTotal - total) < 0.01 &&
        refsValid && cashValid && customerMethodsValid && storeCreditValid && paymongoValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete sale", fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 590.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(subtotal), fontWeight = FontWeight.Bold)
                }
                pricingNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(customer?.name ?: "Walk-in customer")
                    }
                    DropdownMenu(customerMenu, { customerMenu = false }) {
                        DropdownMenuItem(text = { Text("Walk-in customer") }, onClick = {
                            customer = null
                            bike = null
                            customerMenu = false
                            payments = payments.map {
                                if (it.method in listOf("store_credit","credit")) it.copy(method = "cash") else it
                            }
                        })
                        customers.filter { it.isActive }.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                customer = c
                                bike = null
                                customerMenu = false
                            })
                        }
                    }
                }

                if (customer != null && customerBikes.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { bikeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(bike?.let { it.make + " " + it.model + " " + (it.plateNumber ?: "") }
                                ?: "Select motorcycle (optional)")
                        }
                        DropdownMenu(bikeMenu, { bikeMenu = false }) {
                            customerBikes.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b.make + " " + b.model + " " + (b.plateNumber ?: "")) },
                                    onClick = { bike = b; bikeMenu = false }
                                )
                            }
                        }
                    }
                }

                if (!offlineMode) {
                    HorizontalDivider()
                    Text("Additional charge (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        chargeName,
                        { chargeName = it },
                        label = { Text("Charge name, e.g. delivery / bag / handling") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        chargeAmountText,
                        { chargeAmountText = it },
                        label = { Text("Charge amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (charges.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Charges")
                            Text(money(charges.sumOf { it.amount }), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    discountText,
                    { discountText = it },
                    label = { Text("Discount amount") },
                    supportingText = {
                        Text(
                            if (cashierRole)
                                "Cashier limit: ${settings.cashierDiscountLimitPercent}% without manager approval"
                            else "Authorized manager discount"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (needsManagerPin) {
                    OutlinedTextField(
                        managerPin,
                        { managerPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Manager approval PIN") },
                        supportingText = {
                            Text(
                                if (offlineMode)
                                    "Manager-approved discounts require an online connection."
                                else if (hasPriceOverride && discountPercent <= settings.cashierDiscountLimitPercent)
                                    "Required because a product price was overridden."
                                else "Required because this discount or price override needs manager approval."
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !offlineMode
                    )
                }

                if (settings.taxEnabled) {
                    Text(
                        "Tax ${settings.defaultTaxRate}%: ${money(tax)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Payments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val remaining = (total - paymentTotal).coerceAtLeast(0.0)
                        payments = payments + PaymentDraft(
                            amount = if (remaining > 0) String.format(java.util.Locale.US, "%.2f", remaining) else ""
                        )
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Split")
                    }
                }

                payments.forEachIndexed { index, row ->
                    PaymentRow(
                        row = row,
                        customer = customer,
                        paymongoAvailable = paymongoAvailable && !offlineMode,
                        onChange = { updated ->
                            payments = payments.mapIndexed { i, old -> if (i == index) updated else old }
                        },
                        onRemove = if (payments.size > 1) {
                            { payments = payments.filterIndexed { i, _ -> i != index } }
                        } else null
                    )
                }

                if (payments.any { it.method == "credit" }) {
                    OutlinedTextField(
                        dueDate,
                        { dueDate = it },
                        label = { Text("Utang due date YYYY-MM-DD (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !offlineMode
                    )
                }

                if (!storeCreditValid) {
                    Text(
                        "Store credit exceeds the customer's available balance.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!refsValid) {
                    Text(
                        "Reference number is required for manual GCash, Maya, card, and bank payments.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (payments.any { it.method == "paymongo" }) {
                    Text(
                        if (paymongoValid)
                            "StorePOS will generate a dynamic QR Ph code for the exact amount and finalize only after PayMongo confirms payment."
                        else
                            "PayMongo automatic checkout must be the only payment method for this sale and requires an online connection.",
                        color = if (paymongoValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("PAYMENT", fontWeight = FontWeight.Bold)
                    Text(money(paymentTotal), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                if (change > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CHANGE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(money(change), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    }
                }
                if (offlineMode) {
                    Text(
                        "Offline transaction: it will be queued on this device and becomes final only after cloud sync succeeds.",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onComplete(
                        customer?.id,
                        bike?.id,
                        paymentModels,
                        discount,
                        tax,
                        managerPin.trim().ifBlank { null },
                        charges,
                        dueDate.trim().ifBlank { null }
                    )
                },
                enabled = cart.isNotEmpty() && paymentValid && managerValid
            ) {
                Text(if (offlineMode) "Save & queue" else "Complete sale")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PaymentRow(
    row: PaymentDraft,
    customer: Customer?,
    paymongoAvailable: Boolean,
    onChange: (PaymentDraft) -> Unit,
    onRemove: (() -> Unit)?
) {
    var menu by remember(row.key) { mutableStateOf(false) }
    val methods = buildList {
        add("cash")
        if (paymongoAvailable) add("paymongo")
        addAll(listOf("gcash","maya","card","bank","store_credit","credit","other"))
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(row.method.replace("_"," ").uppercase())
                    }
                    DropdownMenu(menu, { menu = false }) {
                        methods.forEach { method ->
                            val needsCustomer = method in listOf("store_credit","credit")
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (method) {
                                            "store_credit" -> "STORE CREDIT" + (customer?.let { " • " + money(it.storeCreditBalance) } ?: "")
                                            "credit" -> "CUSTOMER CREDIT" + (customer?.let { " • limit " + money(it.creditLimit) } ?: "")
                                            "paymongo" -> "QR PH • AUTO VERIFY"
                                            else -> method.uppercase()
                                        }
                                    )
                                },
                                enabled = !needsCustomer || customer != null,
                                onClick = {
                                    onChange(
                                        row.copy(
                                            method = method,
                                            tendered = if (method == "cash") row.amount else "",
                                            reference = ""
                                        )
                                    )
                                    menu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    row.amount,
                    { onChange(row.copy(amount = it)) },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                onRemove?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove payment")
                    }
                }
            }

            if (row.method == "cash") {
                OutlinedTextField(
                    row.tendered,
                    { onChange(row.copy(tendered = it)) },
                    label = { Text("Cash tendered") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else if (row.method == "paymongo") {
                Text(
                    "StorePOS will show a dynamic QR Ph code with the exact sale amount. No manual reference is needed; payment is verified automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (row.method in listOf("gcash","maya","card","bank","other")) {
                OutlinedTextField(
                    row.reference,
                    { onChange(row.copy(reference = it)) },
                    label = { Text(if (row.method == "other") "Reference / note (optional)" else "Reference number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
