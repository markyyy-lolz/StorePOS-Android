package com.storepos.app.ui.screens

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal data class HealthIssueGroup(val key: String, val label: String, val results: JsonArray?)

internal fun healthGroups(result: JsonObject): List<HealthIssueGroup> = listOf(
    "missing_cost" to "Missing cost",
    "negative_stock" to "Negative stock",
    "expired_batches" to "Expired batches",
    "duplicate_barcodes" to "Duplicate barcodes",
    "stale_scheduled_prices" to "Stale scheduled prices"
).map { (key, label) -> HealthIssueGroup(key, label, result[key] as? JsonArray) }
