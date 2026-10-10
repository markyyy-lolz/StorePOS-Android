package com.storepos.app.data.local

import com.storepos.app.data.StoreRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OfflineSyncSummary(
    val synced: Int,
    val pending: Int,
    val needsReview: Int,
    val retryLater: Boolean,
    val message: String? = null
)

/**
 * Shared by foreground POS and WorkManager so one Android process cannot
 * submit the same local sale from two coroutines at the same time.
 *
 * The original UUID client key remains stable on all retries. Cloud RPC
 * also handles duplicate keys from lost network responses.
 */
object OfflineSaleSynchronizer {
    private val mutex = Mutex()

    suspend fun syncShop(
        shopId: String,
        store: OfflineStore,
        onlySaleId: String? = null
    ): OfflineSyncSummary =
        mutex.withLock {
            val queued = store.pendingSales(shopId)
                .filter { onlySaleId == null || it.id == onlySaleId }
            if (queued.isEmpty()) return@withLock snapshot(shopId, store, 0, false)

            val signedInCashier = StoreRepository.currentUserId()
                ?: return@withLock snapshot(shopId, store, 0, true, "Sign in to sync pending sales.")

            // Do not preflight using current stock before the idempotency
            // check. A timed-out ONLINE sale might already be posted, making
            // cloud stock lower than when the offline payload was created.
            // The atomic RPC checks existing client key FIRST, then catalog
            // epoch, prices and locked inventory in a single transaction.

            var successful = 0
            var shouldRetry = false
            var awaitingCashier = false
            for (record in queued) {
                // A shift must not be posted under a different employee after
                // switching user accounts on a shared tablet.
                if (record.payload.cashierId != signedInCashier) {
                    if (record.payload.cashierId.isNullOrBlank()) {
                        store.markNeedsReview(
                            shopId, record.id, "Original cashier identity missing from legacy offline sale."
                        )
                    } else {
                        awaitingCashier = true
                    }
                    continue
                }

                val review = OfflineSyncPolicy.localReviewReason(shopId, record.payload)
                if (review != null) {
                    store.markNeedsReview(shopId, record.id, review)
                    continue
                }

                try {
                    StoreRepository.completeOfflineSale(record.payload)
                    store.removePendingSale(shopId, record.id)
                    successful += 1
                } catch (failure: Throwable) {
                    val message = StoreRepository.userMessage(failure)
                    val normalized = message.lowercase()
                    val connectivity = listOf(
                        "network", "timeout", "connect", "unreachable", "host", "ioexception",
                        "temporarily", "socket", "internet"
                    ).any { it in normalized }
                    if (connectivity) {
                        shouldRetry = true
                        store.setPendingError(shopId, record.id, message)
                    } else {
                        // Business-rule failures are preserved for review;
                        // never silently drop a customer's cash transaction.
                        store.markNeedsReview(shopId, record.id, message)
                    }
                }
            }

            snapshot(
                shopId, store, successful, shouldRetry || awaitingCashier,
                when {
                    awaitingCashier -> "Some sales await their original cashier sign-in."
                    shouldRetry -> "Cloud sync is temporarily unavailable."
                    else -> null
                }
            )
        }

    private fun snapshot(
        shopId: String,
        store: OfflineStore,
        successful: Int,
        retry: Boolean,
        message: String? = null
    ) = OfflineSyncSummary(
        synced = successful,
        pending = store.pendingSales(shopId).size,
        needsReview = store.needsReview(shopId).size,
        retryLater = retry,
        message = message
    )
}
