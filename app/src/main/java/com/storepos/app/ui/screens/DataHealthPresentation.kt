package com.storepos.app.ui.screens

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal data class HealthIssueGroup(val key: String, val label: String, val results: JsonArray?)

internal fun healthGroups(result: JsonObject): List<HealthIssueGroup> = listOf(
    "missing_cost" to "Missing cost",
    "negative_stock" to "Negative stock",
    "expired_batches" to "Expired batches",
    "duplicate_barcodes" to "Duplicate barcodes",
    "stale_scheduled_prices" to "Stale scheduled prices"
).map { (key, label) -> HealthIssueGroup(key, label, result[key] as? JsonArray) }

/** Display one health item without exposing an unformatted JSON payload. */
internal fun healthRecordTitle(item: JsonElement): String {
    val fields = item as? JsonObject ?: return "Record requiring review"
    return listOf("name", "product_name", "batch_name", "barcode", "sku", "id")
        .firstNotNullOfOrNull { key ->
            (fields[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }?.take(130) ?: "Unidentified record"
}

internal fun healthRecordDescription(item: JsonElement): String {
    val fields = item as? JsonObject ?: return ""
    val sku = (fields["sku"] as? JsonPrimitive)?.content
    val sellingPrice = (fields["selling_price"] as? JsonPrimitive)?.content
    return listOfNotNull(
        sku?.let { "SKU " + it.take(60) },
        sellingPrice?.let { "Selling price: " + it.take(35) }
    ).joinToString("  •  ")
}
