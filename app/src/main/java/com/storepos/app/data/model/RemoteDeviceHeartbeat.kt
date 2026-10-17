package com.storepos.app.data.model

import java.time.Instant

/** Telemetry age, not a guarantee that a tablet is actually online. */
object RemoteDeviceHeartbeat {
    const val RECENT_MS = 10L * 60L * 1000L
    fun recent(device: DeviceSession, shopId: String, nowMillis: Long): Boolean {
        if (!device.isActive || device.shopId != shopId) return false
        val seen = runCatching { Instant.parse(device.lastSeenAt).toEpochMilli() }.getOrNull() ?: return false
        return seen <= nowMillis && nowMillis - seen <= RECENT_MS
    }
    fun recentCount(sessions: List<DeviceSession>, shopId: String, nowMillis: Long): Int =
        sessions.count { recent(it, shopId, nowMillis) }
}
