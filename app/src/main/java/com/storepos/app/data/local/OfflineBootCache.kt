package com.storepos.app.data.local

import android.content.Context
import com.storepos.app.data.model.LicenseAccess
import com.storepos.app.data.model.PlanEntitlements
import com.storepos.app.data.model.ShopContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * An offline boot snapshot of a previously validated device, NOT an offline
 * activation mechanism. It never stores passwords, API keys, or auth tokens.
 * The existing restored Supabase session must match the original cashier.
 */
object OfflineBootCache {
    private const val NAME = "storepos_validated_offline_boot"
    private const val KEY = "snapshot"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val DAY = 86_400_000L

    @Serializable
    private data class Snapshot(
        val userId: String,
        val deviceId: String,
        val shop: com.storepos.app.data.model.Shop,
        val member: com.storepos.app.data.model.ShopMember,
        val access: LicenseAccess,
        val entitlements: PlanEntitlements,
        val verifiedAtMs: Long
    )

    fun save(
        context: Context,
        shopContext: ShopContext,
        deviceId: String,
        access: LicenseAccess,
        entitlements: PlanEntitlements
    ) {
        if (!access.valid || !entitlements.valid || !shopContext.member.isActive) return
        val record = Snapshot(
            userId = shopContext.userId,
            deviceId = deviceId,
            shop = shopContext.shop,
            member = shopContext.member,
            access = access,
            entitlements = entitlements,
            verifiedAtMs = System.currentTimeMillis()
        )
        check(context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.encodeToString(record)).commit()) {
            "Unable to cache offline device validation"
        }
    }

    fun restore(context: Context, currentUserId: String, deviceId: String): Triple<ShopContext, LicenseAccess, PlanEntitlements>? {
        val saved = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        val record = runCatching { json.decodeFromString<Snapshot>(saved) }.getOrNull() ?: return null
        if (record.userId != currentUserId || record.deviceId != deviceId ||
            !record.member.isActive || !record.access.valid || !record.entitlements.valid) return null
        if (record.member.userId != currentUserId || record.member.shopId != record.shop.id) return null

        val now = System.currentTimeMillis()
        // Deny apparent clock rollback and snapshots too old to trust.
        if (record.verifiedAtMs > now + 300_000L) return null
        val graceDays = (record.access.offlineGraceDays
            ?: record.entitlements.offlineGraceDays ?: 3).coerceIn(0, 30)
        if (now - record.verifiedAtMs > graceDays * DAY) return null
        val expiry = record.access.expiresAt ?: record.entitlements.expiresAt
        if (expiry != null) {
            val expiryTime = parseExpiry(expiry) ?: return null
            if (now > expiryTime) return null
        }
        return Triple(
            ShopContext(record.userId, record.shop, record.member),
            record.access,
            record.entitlements
        )
    }

    private fun parseExpiry(value: String): Long? = runCatching {
        val text = value.trim()
        runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: LocalDate.parse(text.take(10))
                .plusDays(1).atStartOfDay(ZoneId.of("Asia/Manila")).toInstant().toEpochMilli()
    }.getOrNull()
}
