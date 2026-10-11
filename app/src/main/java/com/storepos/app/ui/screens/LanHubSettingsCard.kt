package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.storepos.app.data.local.LanHubConfig
import com.storepos.app.data.local.LanStockHub
import com.storepos.app.ui.components.MotoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LanHubSettingsCard(shopId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored = remember(shopId) { LanStockHub.load(context, shopId) }
    var mode by remember(shopId) { mutableStateOf(stored.mode) }
    var host by remember(shopId) { mutableStateOf(stored.host) }
    var secret by remember(shopId) { mutableStateOf(stored.secret) }
    var showKey by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var testRunning by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }

    MotoCard(Modifier.fillMaxWidth()) {
        Text("Offline LAN Hub - Two Tablet Shared Stock",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Both tablets must stay connected to the SAME trusted Wi-Fi router or hotspot, " +
                "even when the internet is down. One tablet acts as the inventory host.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("off" to "Standalone", "host" to "Host tablet", "client" to "Second tablet").forEach { (code, name) ->
                FilterChip(
                    selected = mode == code,
                    onClick = { mode = code; feedback = "" },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        if (mode == "host") {
            Text("This tablet's LAN IPv4: " + LanStockHub.localAddress(), fontWeight = FontWeight.SemiBold)
            Text("Port: " + LanStockHub.PORT)
            Text("Keep StorePOS open on the host tablet to share stock with the other tablet.")
        }
        if (mode == "client") {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("Host tablet IPv4, e.g. 192.168.1.120") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (mode != "off") {
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                    else PasswordVisualTransformation(),
                label = { Text("LAN pairing key (copy from host to second tablet)") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == "host") OutlinedButton(onClick = { secret = LanStockHub.newPairingKey() }) {
                    Text("Generate secure key")
                }
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Hide key" else "Show key")
                }
            }
        }
        Button(onClick = {
            runCatching {
                LanStockHub.save(context, shopId, LanHubConfig(mode, host, secret))
                LanStockHub.ensureRunning(context, shopId)
            }.onSuccess {
                feedback = "Saved. " +
                    if (mode == "off") "Standalone mode." else "LAN Hub settings active."
            }.onFailure {
                feedback = "Unable to save LAN settings: " + (it.localizedMessage ?: "Check pairing details")
            }
        }) { Text("Save LAN Hub Settings") }
        if (mode != "off") {
            OutlinedButton(onClick = {
                scope.launch {
                    testRunning = true
                    testResult = ""
                    runCatching {
                        withContext(Dispatchers.IO) { LanStockHub.stock(context, shopId) }
                    }.onSuccess {
                        testResult = "LAN Hub connected. Received stock counts for " + it.size + " products."
                    }.onFailure {
                        testResult = "LAN Hub unavailable: " + (it.localizedMessage ?: "Connection failed")
                    }
                    testRunning = false
                }
            }, enabled = !testRunning) {
                Text(if (testRunning) "Testing..." else "Test LAN Inventory Connection")
            }
        }
        if (feedback.isNotBlank()) Text(feedback)
        if (testResult.isNotBlank()) Text(testResult)
        Text(
            "Important: When joined to a LAN hub, a disconnected second tablet cannot " +
                "approve stock-tracked sales without reaching that host. " +
                "This prevents duplicate sale of the last item. Internet-connected " +
                "PayMongo settlement still requires provider verification.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
