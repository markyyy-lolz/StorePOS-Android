package com.storepos.app.ui.screens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataHealthPresentationTest {
    @Test fun listsProductsByNameAndSkuWithoutRawJson() {
        val health = Json.parseToJsonElement(
            """{
                "missing_cost": [
                    {"id":"id-1","sku":"1001","name":"StorePOS Basic Monthly","selling_price":1.00},
                    {"id":"id-2","sku":"1002","name":"StorePOS Basic Yearly","selling_price":4990.00},
                    {"id":"id-3","sku":"1003","name":"StorePOS Pro Monthly","selling_price":999.00}
                ],
                "negative_stock": [], "expired_batches": [],
                "duplicate_barcodes": [], "stale_scheduled_prices": []
            }"""
        ).jsonObject
        val groups = healthGroups(health)
        val cost = groups.single { it.key == "missing_cost" }
        assertEquals(3, cost.results?.size)
        assertEquals("StorePOS Basic Monthly", healthRecordTitle(cost.results!![0]))
        assertEquals("SKU 1001  •  Selling price: 1.00", healthRecordDescription(cost.results[0]))
        assertEquals(0, groups.single { it.key == "negative_stock" }.results?.size)
    }

    @Test fun missingOrMalformedGroupDoesNotLookLikeZeroIssues() {
        val health = Json.parseToJsonElement(
            """{"missing_cost": {"error": "unexpected"}, "negative_stock": []}"""
        ).jsonObject
        val groups = healthGroups(health)
        assertNull(groups.single { it.key == "missing_cost" }.results)
        assertNull(groups.single { it.key == "expired_batches" }.results)
        assertEquals(0, groups.single { it.key == "negative_stock" }.results?.size)
    }

    @Test fun rawPrimitiveIsNotPrintedAsHealthJson() {
        val bad = Json.parseToJsonElement("123")
        assertEquals("Record requiring review", healthRecordTitle(bad))
        assertEquals("", healthRecordDescription(bad))
    }
}
