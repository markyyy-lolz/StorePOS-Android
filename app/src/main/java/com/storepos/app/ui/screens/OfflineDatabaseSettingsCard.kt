package com.storepos.app.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.local.OfflineStore
import com.storepos.app.data.local.OfflineDatabaseSnapshotExporter
import com.storepos.app.ui.components.MotoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXISTING_OFFLINE_DATABASE_NAME = "motopos_offline.db"

/**
 * Displays the current live SQLite location, and allows owner/admin users to
 * export a consistent snapshot to a user-selected location.
 */
@Composable
fun OfflineDatabaseSettingsCard(shopId: String) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val dbFile = remember(appContext) { appContext.getDatabasePath(EXISTING_OFFLINE_DATABASE_NAME) }
    var queueCount by remember(shopId) { mutableIntStateOf(-1) }
    var reviewCount by remember(shopId) { mutableIntStateOf(-1) }
    var inventoryCount by remember(shopId) { mutableIntStateOf(-1) }
    var inventoryReviewCount by remember(shopId) { mutableIntStateOf(-1) }
    var backupInProgress by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupFailed by remember { mutableStateOf(false) }

    LaunchedEffect(shopId) {
        val counts = withContext(Dispatchers.IO) {
            val local = OfflineStore(appContext)
            try {
                val pending = local.pendingSales(shopId).size
                val review = local.needsReview(shopId).size
                val pendingInv = local.pendingInventory(shopId).size
                val reviewedInv = local.needsReviewInventory(shopId).size
                Triple(pending, review, Pair(pendingInv, reviewedInv))
            } finally {
                local.close()
            }
        }
        queueCount = counts.first
        reviewCount = counts.second
        inventoryCount = counts.third.first
        inventoryReviewCount = counts.third.second
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            backupInProgress = true
            backupMessage = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { OfflineDatabaseSnapshotExporter.export(appContext, uri) }
                }
                result.onSuccess { receipt ->
                    backupFailed = false
                    backupMessage = "Backup saved and verified: " +
                        (receipt.bytes / 1024) + " KB, " + receipt.tables +
                        " tables. SHA-256: " + receipt.sha256.take(12) + "…"
                }.onFailure {
                    backupFailed = true
                    backupMessage = "Backup failed: " +
                        (it.localizedMessage ?: "Unable to create a SQLite snapshot.")
                }
                backupInProgress = false
            }
        }
    }

    MotoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Manage Offline Database",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "The active SQLite database is stored inside Android app-private storage. " +
                "Its live location cannot be moved to Downloads or an SD card.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Current database", fontWeight = FontWeight.SemiBold)
        SelectionContainer {
            Text(dbFile.absolutePath, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Database size: " +
                (if (dbFile.exists()) (dbFile.length() / 1024).toString() + " KB"
                else "Not created yet"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Pending sales: " +
                (if (queueCount < 0) "Checking..." else queueCount.toString()) +
                " • Needs review: " + (if (reviewCount < 0) "..." else reviewCount.toString()),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Pending inventory changes: " +
                (if (inventoryCount < 0) "Checking..." else inventoryCount.toString()) +
                " • Needs review: " +
                (if (inventoryReviewCount < 0) "..." else inventoryReviewCount.toString()),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Backup location: You choose a folder each time you export.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            enabled = !backupInProgress,
            onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                exportPicker.launch("StorePOS-offline-backup-$stamp.db")
            }
        ) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (backupInProgress) "Creating backup..." else "Save Offline Database Backup As...")
        }
        if (backupInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
        backupMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (backupFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "This verified backup may contain private sales and customer data. Store it securely. " +
                "It does not change the live database or sync status. " +
                "Cloud shop backups are managed separately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

