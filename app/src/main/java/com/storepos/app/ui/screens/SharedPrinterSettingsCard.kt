package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.model.ShopContext
import com.storepos.app.printing.BluetoothReceiptPrinter
import com.storepos.app.printing.SharedPrintHostService
import com.storepos.app.printing.SharedPrintRepository
import com.storepos.app.printing.SharedPrinterSnapshot
import com.storepos.app.ui.components.MotoCard
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SharedPrinterSettingsCard(shopContext: ShopContext) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = SharedPrintRepository
    val deviceId = remember { repo.deviceId(androidContext) }
    val admin = shopContext.member.role in listOf("owner", "admin")
    val manager = admin || shopContext.member.role == "manager"
    var mode by remember { mutableStateOf(repo.mode(androidContext)) }
    var snapshot by remember { mutableStateOf<SharedPrinterSnapshot?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recoveryJob by remember { mutableStateOf<String?>(null) }
    var recoveryReason by remember { mutableStateOf("Checked paper output before recovery") }

    suspend fun refresh() {
        snapshot = repo.snapshot(shopContext.shop.id)
    }

    LaunchedEffect(shopContext.shop.id) {
        while (true) {
            runCatching { refresh() }.onFailure {
                message = "Print queue unavailable: " + (it.message ?: "check internet")
            }
            delay(4500L)
        }
    }

    val printer = snapshot?.printer
    val online = printer?.hostLastSeenAt?.let {
        runCatching { Instant.parse(it).isAfter(Instant.now().minusSeconds(55)) }.getOrDefault(false)
    } == true
    val onThisDevice = printer?.hostDeviceId == deviceId && printer.hostUserId == shopContext.userId
    val queued = snapshot?.jobs?.count { it.status == "pending" || it.status == "claimed" } ?: 0

    MotoCard(Modifier.fillMaxWidth()) {
        Text("Shared Printer Mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Two cashiers, one VOZY G80. FIFO queue sends each receipt only after the prior transmission ends.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Printer: " + (printer?.name ?: "Not configured") + " • 80mm • " +
            (if (online) "HOST ONLINE" else "HOST OFFLINE"),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text("Pending jobs: " + queued + " • Selected mode: " + mode.uppercase())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == "direct", onClick = {
                repo.setMode(androidContext,"direct")
                SharedPrintHostService.stop(androidContext)
                mode = "direct"
            }, label = { Text("Direct") })
            FilterChip(mode == "client", onClick = {
                repo.setMode(androidContext,"client")
                SharedPrintHostService.stop(androidContext)
                mode = "client"
            }, label = { Text("Remote cashier") })
        }

        if (admin) {
            val prefs = androidContext.getSharedPreferences("motopos_settings",0)
            Button(onClick = {
                busy = true
                message = null
                scope.launch {
                    runCatching {
                        require(!prefs.getString("printer_address",null).isNullOrBlank()) {
                            "Pair and select VOZY G80 in Receipt printer settings above."
                        }
                        val transport = prefs.getString("printer_transport","bluetooth") ?: "bluetooth"
                        repo.configureHost(shopContext.shop.id,deviceId,shopContext.userId,transport)
                        repo.setMode(androidContext,"host")
                        SharedPrintHostService.start(androidContext,shopContext.shop.id)
                        mode = "host"
                        refresh()
                        message = "This tablet is now the assigned print host."
                    }.onFailure { message = it.message ?: "Could not assign print host" }
                    busy = false
                }
            }, enabled = !busy) {
                Text(if (onThisDevice) "Reconnect / Start Host" else "Assign this tablet as host")
            }
        } else if (onThisDevice && mode == "host") {
            Button(onClick = {
                SharedPrintHostService.start(androidContext,shopContext.shop.id)
                message = "Host restarted."
            }) { Text("Reconnect Print Host") }
        }
        if (!admin) Text("Only a shop owner/admin can assign or switch the printer host.",
            style = MaterialTheme.typography.bodySmall)

        if (mode != "direct") {
            OutlinedButton(onClick = {
                busy = true
                scope.launch {
                    runCatching {
                        require(printer != null) { "Configure the shared printer first." }
                        val bytes = BluetoothReceiptPrinter.testReceipt(shopContext.shop.name,80)
                        repo.enqueue(shopContext.shop.id,deviceId,"test",bytes,
                            "test:" + UUID.randomUUID())
                        message = "Test receipt added to the FIFO queue."
                        refresh()
                    }.onFailure { message = it.message ?: "Queue test failed" }
                    busy = false
                }
            }, enabled = !busy) { Text("Queue test print") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider()
        Text("Recent print jobs", fontWeight = FontWeight.Bold)
        Text("SENT means bytes transmitted; physical printing is not confirmed by Bluetooth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot?.jobs?.take(15)?.forEach { job ->
            HorizontalDivider()
            Column(Modifier.fillMaxWidth()) {
                Text((job.receiptNumber?.takeIf { it.isNotBlank() } ?: job.kind.uppercase()) +
                    " • " + job.status.uppercase(), fontWeight = FontWeight.Medium)
                Text(job.id.take(8) + " • " + job.createdAt.take(19) +
                    " • attempts " + job.attempts,
                    style = MaterialTheme.typography.bodySmall)
                if (!job.lastError.isNullOrBlank()) Text(job.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                if (manager && job.status in listOf("failed","needs_review","pending")) {
                    TextButton(onClick = { recoveryJob = job.id }) {
                        Text("Review / recover")
                    }
                }
            }
        }
        if (snapshot?.jobs.isNullOrEmpty()) {
            Text("No print jobs yet.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (recoveryJob != null) {
        AlertDialog(
            onDismissRequest = { recoveryJob = null },
            title = { Text("Manual print recovery") },
            text = {
                Column {
                    Text("Check the physical receipt first. Bluetooth cannot confirm a finished paper print, so retry may produce a duplicate.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = recoveryReason, onValueChange = { recoveryReason = it },
                        label = { Text("Reason (required)") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val jobId = recoveryJob ?: return@TextButton
                    busy = true
                    scope.launch {
                        runCatching {
                            repo.recover(shopContext.shop.id,jobId,true,recoveryReason)
                            refresh()
                            message = "Print job returned to queue; audited."
                            recoveryJob = null
                        }.onFailure { message = it.message }
                        busy = false
                    }
                }, enabled = !busy && recoveryReason.trim().length >= 4) { Text("Retry after review") }
            },
            dismissButton = {
                TextButton(onClick = { recoveryJob = null }) { Text("Cancel") }
            }
        )
    }
}
