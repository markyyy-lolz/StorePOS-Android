package com.storepos.app.data.model

import org.junit.Assert.*
import org.junit.Test

class RemoteDeviceHeartbeatTest {
    private val now = java.time.Instant.parse("2026-10-17T05:00:00Z").toEpochMilli()
    private fun device(shop: String, seen: String, active: Boolean = true) = DeviceSession(
        id = "d1", shopId = shop, userId = "u1", deviceId = "android-test",
        lastSeenAt = seen, isActive = active, createdAt = "2026-10-01T00:00:00Z"
    )
    @Test fun countsOnlyRecentlyReportedActiveTerminalsWithinShop() {
        val devices=listOf(
            device("storepos-shop","2026-10-17T04:59:00Z"),
            device("storepos-shop","2026-10-17T04:30:00Z"),
            device("other-shop","2026-10-17T04:59:00Z"),
            device("storepos-shop","2026-10-17T04:59:00Z",false)
        )
        assertEquals(1,RemoteDeviceHeartbeat.recentCount(devices,"storepos-shop",now))
    }
    @Test fun clockSkewAndMalformedLastSeenAreNotReportedAsOnline() {
        assertFalse(RemoteDeviceHeartbeat.recent(device("s","2026-10-17T05:05:00Z"),"s",now))
        assertFalse(RemoteDeviceHeartbeat.recent(device("s","invalid"),"s",now))
        assertTrue(RemoteDeviceHeartbeat.recent(device("s","2026-10-17T04:50:00Z"),"s",now))
    }
}
