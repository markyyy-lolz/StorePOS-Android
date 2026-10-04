package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun ServicePage(context: ShopContext) {
    var jobs by remember { mutableStateOf<List<JobOrder>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var motorcycles by remember { mutableStateOf<List<Motorcycle>>(emptyList()) }
    var timers by remember { mutableStateOf<List<TechnicianTimeEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var addOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() = coroutineScope {
        val j = async { StoreRepository.jobs(context.shop.id) }
        val c = async { StoreRepository.customers(context.shop.id) }
        val m = async { StoreRepository.motorcycles(context.shop.id) }
        val t = async { StoreRepository.technicianTimers(context.shop.id) }
        jobs = j.await()
        customers = c.await()
        motorcycles = m.await()
        timers = t.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading workshop…")
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeader(
            "Service & Job Orders",
            "${jobs.count { it.status !in listOf("released", "cancelled") }} active job(s)",
            action = {
                Button(
                    onClick = { addOpen = true },
                    enabled = customers.isNotEmpty() && motorcycles.isNotEmpty()
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New job")
                }
            }
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (jobs.isEmpty()) {
            EmptyView(
                "No service jobs",
                "Create a customer and motorcycle first, then add a job order.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(jobs, key = { it.id }) { job ->
                    JobCard(
                        job = job,
                        customer = customers.firstOrNull { it.id == job.customerId },
                        motorcycle = motorcycles.firstOrNull { it.id == job.motorcycleId },
                        timer = timers.firstOrNull { it.jobOrderId == job.id && it.technicianId == context.userId && it.endedAt == null },
                        onToggleTimer = { current ->
                            scope.launch {
                                error = null
                                val op = if (current == null) {
                                    runCatching { StoreRepository.startTechnicianTimer(job.id) }
                                } else {
                                    runCatching { StoreRepository.stopTechnicianTimer(current.id) }
                                }
                                op.onSuccess { refresh() }
                                    .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        },
                        onNextStatus = {
                            val next = nextJobStatus(job.status)
                            scope.launch {
                                runCatching { StoreRepository.updateJobStatus(job.id, next) }
                                    .onSuccess { refresh() }
                                    .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (addOpen) {
        AddJobDialog(
            context = context,
            customers = customers,
            motorcycles = motorcycles,
            onDismiss = { addOpen = false },
            onSave = { input ->
                scope.launch {
                    runCatching { StoreRepository.addJob(input) }
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

private fun nextJobStatus(status: String): String = when (status) {
    "waiting" -> "inspection"
    "inspection" -> "repairing"
    "repairing" -> "testing"
    "testing" -> "ready"
    "ready" -> "released"
    else -> status
}

@Composable
private fun JobCard(
    job: JobOrder,
    customer: Customer?,
    motorcycle: Motorcycle?,
    timer: TechnicianTimeEntry?,
    onToggleTimer: (TechnicianTimeEntry?) -> Unit,
    onNextStatus: () -> Unit
) {
    MotoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(job.jobNumber, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text(customer?.name ?: "Customer", fontWeight = FontWeight.SemiBold)
                Text(
                    motorcycle?.let {
                        "${it.make} ${it.model}${it.plateNumber?.let { p -> " • $p" } ?: ""}"
                    } ?: "Motorcycle",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(job.status)
        }

        job.complaint?.let {
            Text("Complaint: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (job.status !in listOf("released", "cancelled")) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                OutlinedButton(onClick = { onToggleTimer(timer) }) {
                    Icon(if (timer == null) Icons.Rounded.Timer else Icons.Rounded.StopCircle, null)
                    Spacer(Modifier.width(5.dp))
                    Text(if (timer == null) "Start timer" else "Stop timer")
                }
                Button(onClick = onNextStatus) {
                Text(
                    when (job.status) {
                        "waiting" -> "Start inspection"
                        "inspection" -> "Start repair"
                        "repairing" -> "Send to testing"
                        "testing" -> "Mark ready"
                        "ready" -> "Release"
                        else -> "Update"
                    }
                )
                }
            }
        }
    }
}

@Composable
private fun AddJobDialog(
    context: ShopContext,
    customers: List<Customer>,
    motorcycles: List<Motorcycle>,
    onDismiss: () -> Unit,
    onSave: (JobOrderInsert) -> Unit
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    var bike by remember { mutableStateOf<Motorcycle?>(null) }
    var customerMenu by remember { mutableStateOf(false) }
    var bikeMenu by remember { mutableStateOf(false) }
    var complaint by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("normal") }
    var priorityMenu by remember { mutableStateOf(false) }

    val bikes = motorcycles.filter { it.customerId == customer?.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New job order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(customer?.name ?: "Select customer")
                    }
                    DropdownMenu(customerMenu, { customerMenu = false }) {
                        customers.forEach {
                            DropdownMenuItem(
                                text = { Text(it.name) },
                                onClick = {
                                    customer = it
                                    bike = null
                                    customerMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    OutlinedButton(
                        onClick = { bikeMenu = true },
                        enabled = customer != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(bike?.let { "${it.make} ${it.model} ${it.plateNumber ?: ""}" } ?: "Select motorcycle")
                    }
                    DropdownMenu(bikeMenu, { bikeMenu = false }) {
                        bikes.forEach {
                            DropdownMenuItem(
                                text = { Text("${it.make} ${it.model} ${it.plateNumber ?: ""}") },
                                onClick = {
                                    bike = it
                                    bikeMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    complaint,
                    { complaint = it },
                    label = { Text("Customer complaint / requested service") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    odo,
                    { odo = it },
                    label = { Text("Odometer km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Box {
                    OutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Priority: ${priority.uppercase()}")
                    }
                    DropdownMenu(priorityMenu, { priorityMenu = false }) {
                        listOf("low", "normal", "high", "urgent").forEach {
                            DropdownMenuItem(
                                text = { Text(it.uppercase()) },
                                onClick = {
                                    priority = it
                                    priorityMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        JobOrderInsert(
                            shopId = context.shop.id,
                            customerId = customer!!.id,
                            motorcycleId = bike!!.id,
                            complaint = complaint.trim().ifBlank { null },
                            odometerIn = odo.toDoubleOrNull(),
                            priority = priority,
                            createdBy = context.userId
                        )
                    )
                },
                enabled = customer != null && bike != null
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
