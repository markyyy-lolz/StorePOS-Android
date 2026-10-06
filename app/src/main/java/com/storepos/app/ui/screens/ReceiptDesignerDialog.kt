package com.storepos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.storepos.app.data.model.Shop
import com.storepos.app.data.model.ShopSettings

private val receiptEditableSections = listOf("meta", "items", "totals", "payment", "digital")

private fun normalizedReceiptOrder(raw: List<String>): List<String> {
    val ordered = raw.filter { it in receiptEditableSections }.distinct().toMutableList()
    receiptEditableSections.filterNot { it in ordered }.forEach(ordered::add)
    return ordered
}

private fun sectionLabel(key: String): String = when (key) {
    "meta" -> "Receipt details"
    "items" -> "Items"
    "totals" -> "Totals"
    "payment" -> "Payment"
    "digital" -> "Digital receipt QR"
    else -> key
}

@Composable
fun ReceiptDesignerDialog(
    shop: Shop,
    settings: ShopSettings,
    paperWidthMm: Int,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShopSettings) -> Unit
) {
    var title by remember(settings.shopId) { mutableStateOf(settings.receiptTitle) }
    var header by remember(settings.shopId) { mutableStateOf(settings.receiptHeader.orEmpty()) }
    var footer by remember(settings.shopId) { mutableStateOf(settings.receiptFooter.orEmpty()) }
    var showLogo by remember(settings.shopId) { mutableStateOf(settings.receiptShowLogo) }
    var showAddress by remember(settings.shopId) { mutableStateOf(settings.receiptShowAddress) }
    var showPhone by remember(settings.shopId) { mutableStateOf(settings.receiptShowPhone) }
    var showTin by remember(settings.shopId) { mutableStateOf(settings.receiptShowTin) }
    var showReceiptNumber by remember(settings.shopId) { mutableStateOf(settings.receiptShowReceiptNumber) }
    var showDate by remember(settings.shopId) { mutableStateOf(settings.receiptShowDate) }
    var showCashier by remember(settings.shopId) { mutableStateOf(settings.receiptShowCashier) }
    var showPaymentReference by remember(settings.shopId) { mutableStateOf(settings.receiptShowPaymentReference) }
    var showDigitalQr by remember(settings.shopId) { mutableStateOf(settings.receiptShowDigitalQr) }
    var compactMode by remember(settings.shopId) { mutableStateOf(settings.receiptCompactMode) }
    var sectionOrder by remember(settings.shopId) {
        mutableStateOf(normalizedReceiptOrder(settings.receiptSectionOrder))
    }

    fun moveSection(index: Int, delta: Int) {
        val next = index + delta
        if (index !in sectionOrder.indices || next !in sectionOrder.indices) return
        val list = sectionOrder.toMutableList()
        val value = list.removeAt(index)
        list.add(next, value)
        sectionOrder = list
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Receipt Designer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            "Customize the customer receipt and preview it before saving.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("${if (paperWidthMm == 58) "58" else "80"}mm preview") }
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 820.dp

                    if (wide) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            ReceiptDesignerControls(
                                modifier = Modifier.weight(0.46f),
                                shop = shop,
                                title = title,
                                onTitle = { title = it },
                                header = header,
                                onHeader = { header = it },
                                footer = footer,
                                onFooter = { footer = it },
                                showLogo = showLogo,
                                onShowLogo = { showLogo = it },
                                showAddress = showAddress,
                                onShowAddress = { showAddress = it },
                                showPhone = showPhone,
                                onShowPhone = { showPhone = it },
                                showTin = showTin,
                                onShowTin = { showTin = it },
                                showReceiptNumber = showReceiptNumber,
                                onShowReceiptNumber = { showReceiptNumber = it },
                                showDate = showDate,
                                onShowDate = { showDate = it },
                                showCashier = showCashier,
                                onShowCashier = { showCashier = it },
                                showPaymentReference = showPaymentReference,
                                onShowPaymentReference = { showPaymentReference = it },
                                showDigitalQr = showDigitalQr,
                                onShowDigitalQr = { showDigitalQr = it },
                                compactMode = compactMode,
                                onCompactMode = { compactMode = it },
                                order = sectionOrder,
                                onMove = ::moveSection
                            )
                            ReceiptDesignerPreview(
                                modifier = Modifier.weight(0.54f),
                                shop = shop,
                                paperWidthMm = paperWidthMm,
                                title = title,
                                header = header,
                                footer = footer,
                                showAddress = showAddress,
                                showPhone = showPhone,
                                showTin = showTin,
                                showReceiptNumber = showReceiptNumber,
                                showDate = showDate,
                                showCashier = showCashier,
                                showPaymentReference = showPaymentReference,
                                showDigitalQr = showDigitalQr,
                                compactMode = compactMode,
                                order = sectionOrder
                            )
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ReceiptDesignerPreview(
                                modifier = Modifier.fillMaxWidth(),
                                shop = shop,
                                paperWidthMm = paperWidthMm,
                                title = title,
                                header = header,
                                footer = footer,
                                showAddress = showAddress,
                                showPhone = showPhone,
                                showTin = showTin,
                                showReceiptNumber = showReceiptNumber,
                                showDate = showDate,
                                showCashier = showCashier,
                                showPaymentReference = showPaymentReference,
                                showDigitalQr = showDigitalQr,
                                compactMode = compactMode,
                                order = sectionOrder
                            )
                            ReceiptDesignerControls(
                                modifier = Modifier.fillMaxWidth(),
                                shop = shop,
                                title = title,
                                onTitle = { title = it },
                                header = header,
                                onHeader = { header = it },
                                footer = footer,
                                onFooter = { footer = it },
                                showLogo = showLogo,
                                onShowLogo = { showLogo = it },
                                showAddress = showAddress,
                                onShowAddress = { showAddress = it },
                                showPhone = showPhone,
                                onShowPhone = { showPhone = it },
                                showTin = showTin,
                                onShowTin = { showTin = it },
                                showReceiptNumber = showReceiptNumber,
                                onShowReceiptNumber = { showReceiptNumber = it },
                                showDate = showDate,
                                onShowDate = { showDate = it },
                                showCashier = showCashier,
                                onShowCashier = { showCashier = it },
                                showPaymentReference = showPaymentReference,
                                onShowPaymentReference = { showPaymentReference = it },
                                showDigitalQr = showDigitalQr,
                                onShowDigitalQr = { showDigitalQr = it },
                                compactMode = compactMode,
                                onCompactMode = { compactMode = it },
                                order = sectionOrder,
                                onMove = ::moveSection
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                settings.copy(
                                    receiptTitle = title.trim().ifBlank { "SALES RECEIPT" }.take(48),
                                    receiptHeader = header.trim().ifBlank { null },
                                    receiptFooter = footer.trim().ifBlank { null },
                                    receiptShowLogo = showLogo,
                                    receiptShowAddress = showAddress,
                                    receiptShowPhone = showPhone,
                                    receiptShowTin = showTin,
                                    receiptShowReceiptNumber = showReceiptNumber,
                                    receiptShowDate = showDate,
                                    receiptShowCashier = showCashier,
                                    receiptShowPaymentReference = showPaymentReference,
                                    receiptShowDigitalQr = showDigitalQr,
                                    receiptCompactMode = compactMode,
                                    receiptSectionOrder = listOf("store") + sectionOrder + listOf("footer")
                                )
                            )
                        },
                        enabled = !saving
                    ) {
                        Text(if (saving) "Saving…" else "Save receipt design")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptDesignerControls(
    modifier: Modifier,
    shop: Shop,
    title: String,
    onTitle: (String) -> Unit,
    header: String,
    onHeader: (String) -> Unit,
    footer: String,
    onFooter: (String) -> Unit,
    showLogo: Boolean,
    onShowLogo: (Boolean) -> Unit,
    showAddress: Boolean,
    onShowAddress: (Boolean) -> Unit,
    showPhone: Boolean,
    onShowPhone: (Boolean) -> Unit,
    showTin: Boolean,
    onShowTin: (Boolean) -> Unit,
    showReceiptNumber: Boolean,
    onShowReceiptNumber: (Boolean) -> Unit,
    showDate: Boolean,
    onShowDate: (Boolean) -> Unit,
    showCashier: Boolean,
    onShowCashier: (Boolean) -> Unit,
    showPaymentReference: Boolean,
    onShowPaymentReference: (Boolean) -> Unit,
    showDigitalQr: Boolean,
    onShowDigitalQr: (Boolean) -> Unit,
    compactMode: Boolean,
    onCompactMode: (Boolean) -> Unit,
    order: List<String>,
    onMove: (Int, Int) -> Unit
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            label = { Text("Receipt title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = header,
            onValueChange = onHeader,
            label = { Text("Custom header") },
            supportingText = { Text("Optional text shown below the store details.") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = footer,
            onValueChange = onFooter,
            label = { Text("Thank-you / footer message") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()
        Text("Visible information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        ReceiptToggle("Store address", showAddress, shop.address?.isNotBlank() == true, onShowAddress)
        ReceiptToggle("Store contact number", showPhone, shop.phone?.isNotBlank() == true, onShowPhone)
        ReceiptToggle("TIN", showTin, shop.tin?.isNotBlank() == true, onShowTin)
        ReceiptToggle("Receipt number", showReceiptNumber, true, onShowReceiptNumber)
        ReceiptToggle("Date & time", showDate, true, onShowDate)
        ReceiptToggle("Cashier", showCashier, true, onShowCashier)
        ReceiptToggle("Payment reference", showPaymentReference, true, onShowPaymentReference)
        ReceiptToggle("Digital receipt QR", showDigitalQr, true, onShowDigitalQr)
        ReceiptToggle("Compact thermal layout", compactMode, true, onCompactMode)

        ReceiptToggle(
            label = "Store logo on digital receipt",
            checked = showLogo,
            enabled = !shop.logoUrl.isNullOrBlank(),
            onChecked = onShowLogo
        )
        if (shop.logoUrl.isNullOrBlank()) {
            Text(
                "No store logo is configured yet. Upload one in StorePOS Cloud to enable it on the digital receipt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()
        Text("Section order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Store identity is pinned at the top and Powered by StorePOS is pinned at the bottom.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        order.forEachIndexed { index, key ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sectionLabel(key), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.ArrowUpward, "Move up")
                    }
                    IconButton(onClick = { onMove(index, 1) }, enabled = index < order.lastIndex) {
                        Icon(Icons.Rounded.ArrowDownward, "Move down")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(checked = checked && enabled, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun ReceiptDesignerPreview(
    modifier: Modifier,
    shop: Shop,
    paperWidthMm: Int,
    title: String,
    header: String,
    footer: String,
    showAddress: Boolean,
    showPhone: Boolean,
    showTin: Boolean,
    showReceiptNumber: Boolean,
    showDate: Boolean,
    showCashier: Boolean,
    showPaymentReference: Boolean,
    showDigitalQr: Boolean,
    compactMode: Boolean,
    order: List<String>
) {
    val chars = if (paperWidthMm == 58) 32 else 48
    val divider = if (compactMode) "-".repeat(chars) else "=".repeat(chars)
    val thin = "-".repeat(chars)

    fun center(value: String): String {
        val v = value.take(chars)
        return " ".repeat(((chars - v.length) / 2).coerceAtLeast(0)) + v
    }

    fun pair(left: String, right: String): String {
        val gap = (chars - left.length - right.length).coerceAtLeast(1)
        return (left + " ".repeat(gap) + right).take(chars)
    }

    val storeLines = buildList {
        add(center(shop.name))
        if (showAddress) shop.address?.takeIf { it.isNotBlank() }?.let { add(center(it)) }
        if (showPhone) shop.phone?.takeIf { it.isNotBlank() }?.let { add(center("Contact: $it")) }
        if (showTin) shop.tin?.takeIf { it.isNotBlank() }?.let { add(center("TIN: $it")) }
        header.trim().takeIf { it.isNotBlank() }?.lines()?.forEach { add(center(it)) }
        add(center(title.trim().ifBlank { "SALES RECEIPT" }))
        add(center("NOT AN OFFICIAL TAX RECEIPT"))
        add(divider)
    }

    val sections = mapOf(
        "meta" to buildList {
            if (showReceiptNumber) add("Receipt No: S-2026-000128")
            if (showDate) add("Date: Oct 06, 2026 10:55 AM")
            if (showCashier) add("Cashier: Sample Cashier")
            add(thin)
        },
        "items" to listOf(
            "Coca-Cola 500ml",
            pair("2 x PHP 25.00", "PHP 50.00"),
            "Piattos Cheese",
            pair("1 x PHP 20.00", "PHP 20.00"),
            thin
        ),
        "totals" to listOf(
            pair("Subtotal", "PHP 70.00"),
            pair("TOTAL", "PHP 70.00"),
            divider
        ),
        "payment" to buildList {
            add("PAYMENT DETAILS")
            add(pair("ORPH", "PHP 70.00"))
            add(pair("Status", "PAID"))
            if (showPaymentReference) add("Ref: pay_sample123456789")
        },
        "digital" to if (showDigitalQr) listOf(
            thin,
            center("DIGITAL RECEIPT"),
            center("[ QR CODE ]"),
            center("Scan to view your receipt"),
            center("Available for 3 days only.")
        ) else emptyList()
    )

    val previewLines = buildList {
        addAll(storeLines)
        order.forEach { addAll(sections[it].orEmpty()) }
        add(thin)
        footer.trim().takeIf { it.isNotBlank() }?.lines()?.forEach { add(center(it)) } ?: run {
            add(center("Thank you for your purchase."))
            add(center("Please come again."))
        }
        add(thin)
        add(center("Powered by StorePOS"))
        add(center("Retail Management & POS System"))
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QrCode2, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Live thermal preview", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    previewLines.joinToString("\n"),
                    modifier = Modifier.padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
