package com.storepos.app.data.local

import com.storepos.app.data.model.OfflineInventoryOperation
import com.storepos.app.data.model.Product
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineInventoryPayloadTest {
    private val json=Json { encodeDefaults=true;ignoreUnknownKeys=true }

    @Test fun operationRoundTripKeepsSameIdActorEpochAndQuantity() {
        val original=OfflineInventoryOperation(
            id="9ec23e2a-3de2-4c68-bef9-91ce44db9688",
            shopId="5d5de679-0a87-445d-a31f-4b840438e9e8",
            actorId="1c1f19fd-96a6-4aeb-9ce7-f142dcce5785",
            catalogEpoch="bf75e162-45f6-41d1-adea-3bd8bd51181c",
            kind="adjust",
            productId="4fc7052c-b183-4fd2-8fc6-44c62fcf5e13",
            delta=12.0,reason="return",notes="Offline restock"
        )
        assertEquals(original,json.decodeFromString<OfflineInventoryOperation>(json.encodeToString(original)))
    }

    @Test fun productEditRetainsBeforeAfterAndOriginalSellingPrice() {
        val before=Product(
            id="4fc7052c-b183-4fd2-8fc6-44c62fcf5e13",
            shopId="5d5de679-0a87-445d-a31f-4b840438e9e8",
            sku="MILK-100",name="Milk",sellingPrice=38.0,stockQuantity=9.0
        )
        val after=before.copy(name="Fresh Milk",sellingPrice=42.0)
        val op=OfflineInventoryOperation(
            id="9ec23e2a-3de2-4c68-bef9-91ce44db9688",
            shopId=before.shopId,actorId="staff-1",catalogEpoch="epoch-1",
            kind="edit",productId=before.id,before=before,after=after
        )
        val result=json.decodeFromString<OfflineInventoryOperation>(json.encodeToString(op))
        assertEquals(38.0,result.before!!.sellingPrice,0.000001)
        assertEquals(42.0,result.after!!.sellingPrice,0.000001)
        assertEquals(9.0,result.after!!.stockQuantity,0.000001)
    }

    @Test fun physicalCountStoresExpectedAndActualSeparately() {
        val count=OfflineInventoryOperation(
            id="op-1",shopId="shop-1",actorId="cashier-1",
            catalogEpoch="epoch-1",kind="count",productId="product-1",
            expectedStock=16.0,countedStock=14.0
        )
        val encoded=json.encodeToString(count)
        assertTrue(encoded.contains("expectedStock"))
        assertTrue(encoded.contains("countedStock"))
        assertEquals(count,json.decodeFromString<OfflineInventoryOperation>(encoded))
    }
}
