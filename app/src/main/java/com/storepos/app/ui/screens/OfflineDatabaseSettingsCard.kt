package com.storepos.app.ui.screens

import android.content.Context
import android.net.Uri
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
import com.storepos.app.ui.components.MotoCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    var backupInProgress by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupFailed by remember { mutableStateOf(false) }

    LaunchedEffect(shopId) {
        queueCount = withContext(Dispatchers.IO) {
            val local = OfflineStore(appContext)
            try {
                local.pendingSales().count { it.payload.shopId == shopId }
            } finally {
                local.close()
            }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            backupInProgress = true
            backupMessage = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { exportConsistentDatabaseSnapshot(appContext, uri) }
                }
                result.onSuccess {
                    backupFailed = false
                    backupMessage = "Local database backup saved to your chosen folder."
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
                "Local Database & Offline Backup",
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
            "Pending offline sales for this shop: " +
                (if (queueCount < 0) "Checking..." else queueCount.toString()),
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
            "This backup contains cached data and unsynced transactions. Store it securely. " +
                "It does not change the live database or sync status. " +
                "Cloud shop backups are managed separately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * SQLite VACUUM INTO creates a consistent snapshot, including any committed WAL data.
 * Fail rather than falling back to copying the raw live file on older SQLite versions.
 */
private fun exportConsistentDatabaseSnapshot(context: Context, destination: Uri) {
    val local = OfflineStore(context)
    val temporary = File(context.cacheDir, "storepos-offline-snapshot-" + System.nanoTime() + ".db")
    try {
        val sqlPath = temporary.absolutePath.replace("'", "''")
        local.writableDatabase.execSQL("VACUUM INTO '$sqlPath'")
        check(temporary.isFile && temporary.length() > 0) {
            "SQLite snapshot is empty. No backup was saved."
        }
        val copied = context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            temporary.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Cannot write to the selected folder.")
        check(copied.toLong() == temporary.length()) {
            "Incomplete file copy. Delete the partial backup and try again."
        }
    } finally {
        local.close()
        temporary.delete()
    }
}
