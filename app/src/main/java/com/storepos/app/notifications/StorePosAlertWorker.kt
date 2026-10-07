package com.storepos.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.storepos.app.R
import com.storepos.app.data.StoreRepository
import java.util.concurrent.TimeUnit

class StorePosAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = StoreRepository.loadShopContext() ?: return Result.success()
        val alerts = runCatching {
            StoreRepository.shopAlerts(context.shop.id)
        }.getOrElse {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }

        val important = alerts.filter {
            it.severity.equals("critical", true) || it.severity.equals("warning", true)
        }
        if (important.isEmpty()) return Result.success()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences("motopos_settings", 0)
        if (!prefs.getBoolean("background_alerts_enabled", true)) return Result.success()

        val signature = important
            .sortedBy { it.code }
            .joinToString("|") { it.code + ":" + it.severity + ":" + it.message.take(80) }

        val lastSignature = prefs.getString("background_alert_signature", null)
        if (lastSignature == signature) return Result.success()

        publish(important)
        prefs.edit()
            .putString("background_alert_signature", signature)
            .putLong("background_alert_last_check", System.currentTimeMillis())
            .apply()
        return Result.success()
    }

    private fun publish(alerts: List<com.storepos.app.data.model.ShopAlert>) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "StorePOS Background Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Low stock, billing, sync and operations alerts from StorePOS."
                }
            )
        }

        val first = alerts.first()
        val title = if (alerts.size == 1) first.title
        else alerts.size.toString() + " StorePOS alerts need attention"

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(applicationContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(applicationContext)
        }
            .setSmallIcon(R.drawable.ic_storepos_logo)
            .setContentTitle(title)
            .setContentText(first.message.take(140))
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "storepos_background_alerts"
        private const val NOTIFICATION_ID = 221
        private const val UNIQUE_WORK = "storepos-background-alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StorePosAlertWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
