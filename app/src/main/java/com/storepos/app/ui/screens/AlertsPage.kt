package com.storepos.app.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.R
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.ShopAlert
import com.storepos.app.data.model.ShopContext
import com.storepos.app.ui.components.EmptyView
import com.storepos.app.ui.components.LoadingView
import com.storepos.app.ui.components.PageHeader
import kotlinx.coroutines.launch

private const val ALERT_CHANNEL_ID = "motopos_shop_alerts"

@Composable
fun AlertsPage(
    context: ShopContext,
    onNavigate: (String) -> Unit
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<ShopAlert>>(emptyList()) }
    var severityFilter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun publishSummary(items: List<ShopAlert>) {
        if (!canNotify()) return
        val important = items.filter { it.severity.lowercase() in setOf("critical", "warning") }
        if (important.isEmpty()) return
        runCatching {
            val manager = androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "StorePOS Shop Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "License, stock, support and operations alerts from StorePOS."
                    }
                )
            }
            val first = important.first()
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(androidContext, ALERT_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(androidContext)
            }
                .setSmallIcon(R.drawable.ic_storepos_logo)
                .setContentTitle(if (important.size == 1) first.title else "${important.size} StorePOS alerts need attention")
                .setContentText(first.message.take(120))
                .setAutoCancel(true)
                .build()
            manager.notify(220, notification)
        }
    }

    suspend fun refresh() {
        val loaded = StoreRepository.shopAlerts(context.shop.id)
        alerts = loaded
        publishSummary(loaded)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) publishSummary(alerts)
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) {
        LoadingView("Loading alerts…")
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(
            "Alerts",
            "License, inventory, service, billing and support attention center",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!canNotify() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(onClick = {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Icon(Icons.Rounded.NotificationsActive, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Allow alerts")
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            error = null
                            runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                        }
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh alerts")
                    }
                }
            }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (alerts.isNotEmpty()) {
            val criticalCount = alerts.count { it.severity.equals("critical", true) }
            val warningCount = alerts.count { it.severity.equals("warning", true) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = severityFilter == "all",
                    onClick = { severityFilter = "all" },
                    label = { Text("All " + alerts.size) }
                )
                FilterChip(
                    selected = severityFilter == "critical",
                    onClick = { severityFilter = "critical" },
                    label = { Text("Critical " + criticalCount) }
                )
                FilterChip(
                    selected = severityFilter == "warning",
                    onClick = { severityFilter = "warning" },
                    label = { Text("Warnings " + warningCount) }
                )
            }
        }

        if (alerts.isEmpty()) {
            EmptyView(
                "Nothing needs attention",
                "StorePOS will surface license, stock, service, receivable and support alerts here.",
                Modifier.weight(1f)
            )
        } else {
            val visibleAlerts = alerts.filter {
                severityFilter == "all" || it.severity.equals(severityFilter, true)
            }
            if (visibleAlerts.isEmpty()) {
                EmptyView(
                    "No alerts in this filter",
                    "Try All to view the complete StorePOS notification center.",
                    Modifier.weight(1f)
                )
            } else LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(visibleAlerts, key = { it.code }) { alert ->
                    val container = when (alert.severity.lowercase()) {
                        "critical" -> MaterialTheme.colorScheme.errorContainer
                        "warning" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    Card(
                        onClick = { alert.actionPage?.let(onNavigate) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = container)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(alert.title, fontWeight = FontWeight.Black)
                                Text(
                                    alert.severity.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                            alert.actionPage?.let {
                                Text(
                                    "Open " + it.replace("_", " "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
