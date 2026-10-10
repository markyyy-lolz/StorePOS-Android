package com.storepos.app.data.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.storepos.app.data.AppSessionRetention
import java.util.concurrent.TimeUnit

/**
 * Reconcile each tablet's pending cash sales even when POS is not visible.
 * Connected network constraint avoids retrying while Android is offline;
 * the synchronizer still validates cash, user, price, stock, shop and items.
 */
class OfflineSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val shopId = inputData.getString(KEY_SHOP_ID)
            ?.takeIf { it.isNotBlank() } ?: return Result.failure()

        return try {
            AppSessionRetention.enforceOnColdStart(applicationContext)
            val store = OfflineStore(applicationContext)
            val inventory = OfflineInventorySynchronizer.syncShop(shopId,store)
            val summary = OfflineSaleSynchronizer.syncShop(shopId, store)
            if (summary.retryLater || inventory.retry) Result.retry() else Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_SHOP_ID = "shop_id"

        private fun input(shopId: String) =
            Data.Builder().putString(KEY_SHOP_ID, shopId).build()

        private val onlineConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context, shopId: String) {
            val periodic = PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(onlineConstraint)
                .setInputData(input(shopId))
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    "storepos-offline-periodic-$shopId",
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodic
                )
        }

        fun requestOnReconnect(context: Context, shopId: String) {
            val single = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(onlineConstraint)
                .setInputData(input(shopId))
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    "storepos-offline-reconnect-$shopId",
                    ExistingWorkPolicy.KEEP,
                    single
                )
        }
    }
}
