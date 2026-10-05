package com.storepos.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.BuildConfig
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.AppVersion
import com.storepos.app.data.model.ShopContext
import com.storepos.app.data.model.ShopSettings
import com.storepos.app.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(context: ShopContext) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    var latest by remember { mutableStateOf<AppVersion?>(null) }
    var posSettings by remember { mutableStateOf(ShopSettings(shopId = context.shop.id)) }
    var posSettingsOpen by remember { mutableStateOf(false) }
    var savingSettings by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context.shop.id) {
        runCatching { StoreRepository.shopSettings(context.shop.id) }
            .onSuccess { posSettings = it }
            .onFailure { error = StoreRepository.userMessage(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader("Settings", "Shop, cloud, printer and app configuration")
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Shop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(context.shop.name, style = MaterialTheme.typography.headlineSmall)
            context.shop.address?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            context.shop.phone?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Role: ${context.member.role.uppercase()}", color = MaterialTheme.colorScheme.primary)
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Row {
                Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Supabase Cloud", fontWeight = FontWeight.Bold)
                    Text("Connected • secure RLS • realtime-ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Software information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "StorePOS v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Retail Store POS & Management System", fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Text("Created & Developed by Mark Reymuel Pascual", fontWeight = FontWeight.Bold)
            Text(
                "Project lead • product design • Android development • system architecture",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Kotlin • Jetpack Compose • Material 3 • Supabase Cloud • Bluetooth/USB ESC/POS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "© 2026 Mark Reymuel Pascual • StorePOS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Register & checkout policy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                listOf(
                    if (posSettings.requireCashierShift) "Shift required" else "Shift optional",
                    if (posSettings.taxEnabled) "Tax " + posSettings.defaultTaxRate + "%" else "Tax off",
                    "Cashier discount " + posSettings.cashierDiscountLimitPercent + "%",
                    if (posSettings.autoPrintReceipt) "Auto print" else "Manual print"
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { posSettingsOpen = true }) {
                Text("Configure POS")
            }
        }

        PrinterSettingsCard(context)
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Cloud backup & billing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Export Sales/Inventory/Customers CSV, download a full JSON shop backup, submit license payments and custom orders in StorePOS Cloud.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                androidContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://markyyy-lolz.github.io/StorePOS-Web/#/dashboard/settings")
                    )
                )
            }) {
                Text("Open Cloud Backup & Export")
            }
            OutlinedButton(onClick = {
                androidContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://markyyy-lolz.github.io/StorePOS-Web/#/dashboard/license")
                    )
                )
            }) {
                Text("Manage license & payment")
            }
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("App manual & Help Center", fontWeight = FontWeight.Bold)
                    Text(
                        "Open the full StorePOS guide, troubleshooting and role instructions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = {
                androidContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://markyyy-lolz.github.io/StorePOS-Web/#/manual")
                    )
                )
            }) {
                Text("Open manual")
            }
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Text("App update", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Installed: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            latest?.let { v ->
                Text("Latest: v${v.versionName} (${v.versionCode})")
                if (v.versionCode > BuildConfig.VERSION_CODE) {
                    Text(if (v.mandatory) "Mandatory update available" else "Update available",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        val url = v.apkUrl ?: v.githubReleaseUrl
                        if (!url.isNullOrBlank()) androidContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Rounded.SystemUpdate, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open update")
                    }
                } else Text("You're up to date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { checking = true; error = null }, enabled = !checking) {
                Text(if (checking) "Checking…" else "Check for updates")
            }
            if (checking) LaunchedEffect("version-check") {
                runCatching { StoreRepository.latestVersion() }
                    .onSuccess { latest = it }
                    .onFailure { error = StoreRepository.userMessage(it) }
                checking = false
            }
        }
    if (posSettingsOpen) {
        PosSystemSettingsDialog(
            settings = posSettings,
            saving = savingSettings,
            onDismiss = { if (!savingSettings) posSettingsOpen = false },
            onSave = { updated ->
                savingSettings = true
                error = null
                scope.launch {
                    runCatching { StoreRepository.updateShopSettings(updated) }
                        .onSuccess {
                            posSettings = updated
                            posSettingsOpen = false
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                    savingSettings = false
                }
            }
        )
    }
    }
}


@Composable
private fun PosSystemSettingsDialog(
    settings: ShopSettings,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShopSettings) -> Unit
) {
    var receiptHeader by remember(settings.shopId) { mutableStateOf(settings.receiptHeader.orEmpty()) }
    var receiptFooter by remember(settings.shopId) { mutableStateOf(settings.receiptFooter.orEmpty()) }
    var taxEnabled by remember(settings.shopId) { mutableStateOf(settings.taxEnabled) }
    var taxRate by remember(settings.shopId) { mutableStateOf(settings.defaultTaxRate.toString()) }
    var requireShift by remember(settings.shopId) { mutableStateOf(settings.requireCashierShift) }
    var discountLimit by remember(settings.shopId) { mutableStateOf(settings.cashierDiscountLimitPercent.toString()) }
    var pinForDiscount by remember(settings.shopId) { mutableStateOf(settings.managerPinForDiscount) }
    var allowHold by remember(settings.shopId) { mutableStateOf(settings.allowHoldSales) }
    var autoPrint by remember(settings.shopId) { mutableStateOf(settings.autoPrintReceipt) }
    var cashDrawer by remember(settings.shopId) { mutableStateOf(settings.cashDrawerEnabled) }
    var showCashier by remember(settings.shopId) { mutableStateOf(settings.receiptShowCashier) }
    var allowNegative by remember(settings.shopId) { mutableStateOf(settings.allowNegativeStock) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Production POS Settings") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Register control", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Require open cashier shift", requireShift) { requireShift = it }
                        SettingSwitchRow("Manager PIN for high cashier discounts", pinForDiscount) { pinForDiscount = it }
                        SettingSwitchRow("Allow Hold / Park Sale", allowHold) { allowHold = it }
                    }
                    item {
                        OutlinedTextField(
                            discountLimit,
                            { discountLimit = it },
                            label = { Text("Cashier discount limit (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        HorizontalDivider()
                        Text("Tax", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Enable automatic tax", taxEnabled) { taxEnabled = it }
                    }
                    if (taxEnabled) {
                        item {
                            OutlinedTextField(
                                taxRate,
                                { taxRate = it },
                                label = { Text("Default tax rate (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    item {
                        HorizontalDivider()
                        Text("Receipt & printer", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Auto print after successful sale", autoPrint) { autoPrint = it }
                        SettingSwitchRow("Pulse cash drawer on cash sale", cashDrawer) { cashDrawer = it }
                        SettingSwitchRow("Show cashier on receipt", showCashier) { showCashier = it }
                    }
                    item {
                        OutlinedTextField(
                            receiptHeader,
                            { receiptHeader = it },
                            label = { Text("Receipt header (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            receiptFooter,
                            { receiptFooter = it },
                            label = { Text("Receipt footer") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        HorizontalDivider()
                        Text("Inventory safety", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Allow negative stock", allowNegative) { allowNegative = it }
                        Text(
                            if (allowNegative)
                                "Warning: checkout can continue when tracked stock goes below zero."
                            else
                                "Recommended: block sales that would make tracked stock negative.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allowNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val tax = taxRate.toDoubleOrNull() ?: 0.0
            val limit = discountLimit.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            receiptHeader = receiptHeader.trim().ifBlank { null },
                            receiptFooter = receiptFooter.trim().ifBlank { null },
                            taxEnabled = taxEnabled,
                            defaultTaxRate = tax.coerceIn(0.0, 100.0),
                            requireCashierShift = requireShift,
                            cashierDiscountLimitPercent = limit.coerceIn(0.0, 100.0),
                            managerPinForDiscount = pinForDiscount,
                            allowHoldSales = allowHold,
                            autoPrintReceipt = autoPrint,
                            cashDrawerEnabled = cashDrawer,
                            receiptShowCashier = showCashier,
                            allowNegativeStock = allowNegative
                        )
                    )
                },
                enabled = !saving && tax in 0.0..100.0 && limit in 0.0..100.0
            ) {
                Text(if (saving) "Saving…" else "Save settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
