package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.model.ReceiptFreebieRule

/** Owner/admin config synced via a StorePOS-only cloud table. */
@Composable
internal fun ReceiptFreebieSettingsDialog(
    rule: ReceiptFreebieRule,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReceiptFreebieRule) -> Unit
) {
    var enabled by remember(rule.shopId) { mutableStateOf(rule.enabled) }
    var threshold by remember(rule.shopId) { mutableStateOf(rule.minimumTotal.toString()) }
    var itemName by remember(rule.shopId) { mutableStateOf(rule.giftName) }
    var quantity by remember(rule.shopId) { mutableStateOf(rule.quantity.toString()) }
    val amount = threshold.toDoubleOrNull()
    val qty = quantity.toIntOrNull()
    val valid = (amount != null && amount.isFinite() && amount > 0 &&
        amount <= 1_000_000.0 && qty != null && qty in 1..20 &&
        (!enabled || itemName.trim().isNotBlank())) && itemName.trim().length <= 80

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Spend & Get Free Item") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Enable receipt reward")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Minimum total (PHP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount == null || !amount.isFinite() || amount <= 0,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it.take(80) },
                    label = { Text("Free item name (e.g. 1kg Rice)") },
                    supportingText = { Text("Printed as a FREE reward, not part of the payable cart.") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Free quantity (1–20)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Qualifies when the final paid total meets the threshold. Receipt shows a claim notice. " +
                        "Staff must verify and record physical gift issuance; this feature does NOT auto-deduct stock " +
                        "or prevent a receipt being presented twice.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && valid,
                onClick = {
                    if (valid) onSave(
                        rule.copy(
                            enabled = enabled,
                            minimumTotal = amount!!,
                            giftName = itemName.trim(),
                            quantity = qty!!
                        )
                    )
                }
            ) { Text(if (saving) "Saving…" else "Save to Cloud") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
