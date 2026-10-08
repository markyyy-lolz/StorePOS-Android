package com.storepos.app.printing

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.storepos.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One foreground print host per printer. The Supabase claim RPC serializes
 * printing across devices. A successful ESC/POS write is only "sent", not
 * proof that the physical paper emerged; failed writes require manual review.
 */
class SharedPrintHostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private val channel = "storepos_shared_print"
    private val notificationId = 7017

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channel, "StorePOS Shared Printer", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shopId = intent?.getStringExtra("shop_id")
        if (shopId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(notificationId, notification("Connected to shared queue"))
        worker?.cancel()
        worker = scope.launch { runHost(shopId) }
        return START_NOT_STICKY
    }

    private fun notification(status: String): Notification {
        val open = PendingIntent.getActivity(
            this, 7017, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("StorePOS • VOZY G80 Print Host")
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun show(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification(status))
    }

    private suspend fun runHost(shopId: String) {
        val deviceId = SharedPrintRepository.deviceId(this)
        while (scope.isActive && SharedPrintRepository.mode(this) == "host") {
            try {
                SharedPrintRepository.heartbeat(shopId, deviceId)
                val job = SharedPrintRepository.claim(shopId, deviceId)
                if (job != null) dispatch(shopId, deviceId, job)
                else delay(2400)
            } catch (error: Exception) {
                show("Queue unavailable: " + (error.message ?: "reconnecting").take(80))
                delay(5500)
            }
        }
        stopSelf()
    }

    private suspend fun dispatch(shopId: String, deviceId: String, job: SharedPrintJob) {
        val payload = job.payloadBase64?.let {
            runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
        }
        if (payload == null || payload.isEmpty()) {
            SharedPrintRepository.failure(shopId,deviceId,job.id,"Receipt payload is missing",false)
            return
        }
        val prefs = getSharedPreferences("motopos_settings", 0)
        val address = prefs.getString("printer_address", null)
        val name = prefs.getString("printer_name", "VOZY G80") ?: "VOZY G80"
        val transport = prefs.getString("printer_transport", "bluetooth") ?: "bluetooth"
        if (address.isNullOrBlank()) {
            SharedPrintRepository.failure(shopId,deviceId,job.id,"Select and pair a printer on the host tablet",true)
            delay(5000)
            return
        }
        if (transport == "usb" && !UsbReceiptPrinter.hasPermission(this,address)) {
            SharedPrintRepository.failure(shopId,deviceId,job.id,"USB permission must be granted on host",true)
            delay(5000)
            return
        }
        val printer: ReceiptPrinter = if (transport == "usb") UsbReceiptPrinter(this)
            else BluetoothReceiptPrinter(this)
        show("Receipt " + (job.receiptNumber ?: job.id.take(8)) + " • printing")
        var connected = false
        try {
            val open = printer.connect(PrinterDevice(name,address,transport))
            if (open.isFailure) {
                SharedPrintRepository.failure(shopId,deviceId,job.id,
                    open.exceptionOrNull()?.message ?: "Connection failed",true)
                delay(1500)
                return
            }
            connected = true
            val written = printer.printReceipt(payload)
            if (written.isFailure) {
                // Some bytes might already have reached the cutter: NEVER auto-retry.
                SharedPrintRepository.failure(shopId,deviceId,job.id,
                    written.exceptionOrNull()?.message ?: "Write outcome uncertain",false)
                show("Receipt needs manual review")
                return
            }
            // This means bytes were transmitted, NOT physical print confirmed.
            SharedPrintRepository.acknowledge(shopId,deviceId,job.id)
            show("Receipt sent • ready for next job")
            delay(850)
        } catch (error: Exception) {
            // If acknowledgement fails, the lease expires into needs_review.
            runCatching {
                SharedPrintRepository.failure(shopId,deviceId,job.id,
                    error.message ?: "Unknown transmission state",!connected)
            }
            show("Printing interrupted; check queue")
        } finally {
            printer.disconnect()
        }
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, shopId: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, SharedPrintHostService::class.java)
                    .putExtra("shop_id",shopId)
            )
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, SharedPrintHostService::class.java))
        }
    }
}
