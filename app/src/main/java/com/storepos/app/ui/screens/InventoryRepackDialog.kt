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
import com.storepos.app.data.model.Product
import java.util.UUID

/** Requires live cloud approval; NEVER makes local optimistic stock changes. */
@Composable
internal fun InventoryRepackDialog(
    products: List<Product>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Double, String) -> Unit
) {
    val candidates = remember(products) {
        products.filter {
            it.isActive && it.trackStock && !it.batchTracked && !it.serialTracked &&
                it.retailParentId == null
        }.sortedBy { it.name.lowercase() }
    }
    var source by remember { mutableStateOf<Product?>(null) }
    var target by remember { mutableStateOf<Product?>(null) }
    var sourceQty by remember { mutableStateOf("1") }
    var targetQty by remember { mutableStateOf("1") }
    var confirmation by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    // Kept stable for retries while dialog stays open: the RPC is idempotent.
    val requestId = remember { UUID.randomUUID().toString() }
    val src = sourceQty.toDoubleOrNull()
    val dst = targetQty.toDoubleOrNull()
    val valid = source != null && target != null && source!!.id != target!!.id &&
        src != null && dst != null &&
        src.isFinite() && dst.isFinite() &&
        src > 0 && dst > 0 && src <= 100000 && dst <= 100000 &&
        src <= (source?.stockQuantity ?: 0.0) &&
        kotlin.math.abs(src * 1000.0 - kotlin.math.round(src * 1000.0)) < 0.00000001 &&
        kotlin.math.abs(dst * 1000.0 - kotlin.math.round(dst * 1000.0)) < 0.00000001

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Repacking & Product Conversion") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select two independently stocked products. Example: convert 1 rice sack into 25 saleable 1kg bags.")
                Box {
                    OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Source: " + (source?.name ?: "Select source SKU"))
                    }
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        candidates.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name + " · " + product.stockQuantity + " " + product.unit) },
                                onClick = { source = product; sourceMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = sourceQty, onValueChange = { sourceQty = it },
                    label = { Text("Quantity of source consumed") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Output: " + (target?.name ?: "Select output SKU"))
                    }
                    DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                        candidates.filter { it.id != source?.id }.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name + " · " + product.stockQuantity + " " + product.unit) },
                                onClick = { target = product; targetMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = targetQty, onValueChange = { targetQty = it },
                    label = { Text("Saleable output units produced") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (valid) {
                    Text(
                        "${source!!.name}: ${source!!.stockQuantity} → ${source!!.stockQuantity - src!!} " +
                            "${source!!.unit}\n" +
                            "${target!!.name}: ${target!!.stockQuantity} → ${target!!.stockQuantity + dst!!} " +
                            "${target!!.unit}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "ONLINE ONLY. Both stock movements are one audited cloud transaction. " +
                        "Offline tablets may still have unsynced stock; sync them before repacking. " +
                        "Serialized, batched and shared-stock virtual packs are excluded.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = confirmation, onCheckedChange = { confirmation = it })
                    Text("Physical repacking completed and quantities verified")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && valid && confirmation,
                onClick = {
                    if (valid) onConfirm(source!!.id, target!!.id, src!!, dst!!, requestId)
                }
            ) { Text(if (saving) "Recording…" else "Record repacking") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
