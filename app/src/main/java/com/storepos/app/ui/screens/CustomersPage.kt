package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.Customer
import com.storepos.app.data.model.CustomerInsert
import com.storepos.app.data.model.ShopContext
import com.storepos.app.ui.components.EmptyView
import com.storepos.app.ui.components.LoadingView
import com.storepos.app.ui.components.MotoCard
import com.storepos.app.ui.components.PageHeader
import com.storepos.app.ui.components.money
import kotlinx.coroutines.launch

@Composable
fun CustomersPage(context: ShopContext) {
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addCustomer by remember { mutableStateOf(false) }
    var rewardsCustomer by remember { mutableStateOf<Customer?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        customers = StoreRepository.customers(context.shop.id)
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading customers…")
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader(
            "Customers",
            "${customers.size} customer(s) · loyalty and store credit",
            action = {
                Button(onClick = { addCustomer = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Customer")
                }
            }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (customers.isEmpty()) {
            EmptyView(
                "No customers",
                "Create your first retail customer record.",
                Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(customers, key = { it.id }) { customer ->
                    MotoCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, fontWeight = FontWeight.Bold)
                                Text(
                                    customer.phone ?: customer.email ?: customer.address ?: "No contact info",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Loyalty ${customer.loyaltyPoints} pts · Credit ${money(customer.storeCreditBalance)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = { rewardsCustomer = customer }) {
                                Text("Rewards")
                            }
                        }
                    }
                }
            }
        }
    }

    if (addCustomer) {
        AddRetailCustomerDialog(
            onDismiss = { addCustomer = false },
            onSave = { name, phone, email, address, notes ->
                scope.launch {
                    error = null
                    runCatching {
                        StoreRepository.addCustomer(
                            CustomerInsert(
                                shopId = context.shop.id,
                                name = name,
                                phone = phone,
                                email = email,
                                address = address,
                                notes = notes,
                                createdBy = context.userId
                            )
                        )
                    }.onSuccess {
                        addCustomer = false
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    rewardsCustomer?.let { customer ->
        CustomerRewardsDialog(
            customer = customer,
            onDismiss = { rewardsCustomer = null },
            onSave = { points, credit, reason ->
                scope.launch {
                    error = null
                    runCatching {
                        if (points != 0) StoreRepository.adjustCustomerLoyalty(customer.id, points, reason)
                        if (credit != 0.0) StoreRepository.adjustCustomerStoreCredit(customer.id, credit, reason)
                    }.onSuccess {
                        rewardsCustomer = null
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }
}

@Composable
private fun AddRetailCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        phone.trim().takeIf { it.isNotBlank() },
                        email.trim().takeIf { it.isNotBlank() },
                        address.trim().takeIf { it.isNotBlank() },
                        notes.trim().takeIf { it.isNotBlank() }
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CustomerRewardsDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onSave: (Int, Double, String) -> Unit
) {
    var points by remember { mutableStateOf("0") }
    var credit by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("Manual customer adjustment") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(customer.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current: ${customer.loyaltyPoints} pts · ${money(customer.storeCreditBalance)} credit")
                OutlinedTextField(points, { points = it }, label = { Text("Points adjustment") }, singleLine = true)
                OutlinedTextField(credit, { credit = it }, label = { Text("Store credit adjustment") }, singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        points.toIntOrNull() ?: 0,
                        credit.toDoubleOrNull() ?: 0.0,
                        reason.trim().ifBlank { "Manual adjustment" }
                    )
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
