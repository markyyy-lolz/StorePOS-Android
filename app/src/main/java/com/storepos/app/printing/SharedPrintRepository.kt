package com.storepos.app.printing

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class SharedPrinter(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    val name: String,
    val model: String = "VOZY G80",
    val transport: String = "bluetooth",
    @SerialName("paper_width_mm") val paperWidth: Int = 80,
    @SerialName("host_user_id") val hostUserId: String? = null,
    @SerialName("host_device_id") val hostDeviceId: String? = null,
    @SerialName("host_last_seen_at") val hostLastSeenAt: String? = null,
    val enabled: Boolean = true
)

@Serializable
data class SharedPrintJob(
    val id: String,
    val kind: String,
    val status: String,
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("payload_base64") val payloadBase64: String? = null,
    @SerialName("paper_width_mm") val paperWidth: Int = 80,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("request_key") val requestKey: String,
    val attempts: Int = 0,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("created_at") val createdAt: String
)

data class SharedPrinterSnapshot(val printer: SharedPrinter?, val jobs: List<SharedPrintJob>)

object SharedPrintRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val client get() = SupabaseProvider.client
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.length >= 5 } ?: "storepos-device-unidentified"

    fun mode(context: Context): String =
        context.getSharedPreferences("motopos_settings", 0)
            .getString("shared_printer_mode", "direct") ?: "direct"

    fun setMode(context: Context, newMode: String) {
        require(newMode in listOf("direct", "client", "host"))
        context.getSharedPreferences("motopos_settings", 0)
            .edit().putString("shared_printer_mode", newMode).apply()
    }

    private suspend fun action(shopId: String, action: String, data: JsonObject = JsonObject(emptyMap())): JsonObject =
        client.postgrest.rpc("storepos_shared_print_action", buildJsonObject {
            put("p_shop_id", shopId)
            put("p_action", action)
            put("p_data", data)
        }).decodeAs()

    suspend fun snapshot(shopId: String): SharedPrinterSnapshot {
        val result = action(shopId, "list")
        val printer = result["printer"]?.takeIf { it !is JsonNull }?.let {
            json.decodeFromJsonElement<SharedPrinter>(it)
        }
        val jobs = result["jobs"]?.jsonArray?.map {
            json.decodeFromJsonElement<SharedPrintJob>(it)
        }.orEmpty()
        return SharedPrinterSnapshot(printer, jobs)
    }

    suspend fun configureHost(shopId: String, deviceId: String, userId: String, transport: String) {
        action(shopId, "configure_host", buildJsonObject {
            put("device_id", deviceId)
            put("host_user_id", userId)
            put("transport", transport)
        })
    }

    suspend fun heartbeat(shopId: String, deviceId: String) {
        action(shopId, "heartbeat", buildJsonObject { put("device_id", deviceId) })
    }

    suspend fun claim(shopId: String, deviceId: String): SharedPrintJob? {
        val result = action(shopId, "claim", buildJsonObject { put("device_id", deviceId) })
        return result["job"]?.takeIf { it !is JsonNull }?.let {
            json.decodeFromJsonElement<SharedPrintJob>(it)
        }
    }

    suspend fun acknowledge(shopId: String, deviceId: String, jobId: String) {
        action(shopId, "ack", buildJsonObject {
            put("device_id", deviceId)
            put("job_id", jobId)
        })
    }

    suspend fun failure(shopId: String, deviceId: String, jobId: String, message: String, safeToRetry: Boolean) {
        action(shopId, "fail", buildJsonObject {
            put("device_id", deviceId)
            put("job_id", jobId)
            put("error", message.take(300))
            put("safe_to_retry", safeToRetry)
        })
    }

    suspend fun recover(shopId: String, jobId: String, retry: Boolean, reason: String) {
        action(shopId, if (retry) "retry" else "cancel", buildJsonObject {
            put("job_id", jobId)
            put("reason", reason)
        })
    }

    fun requestKey(saleId: String, reprint: Boolean): String =
        if (reprint) "reprint:" + saleId + ":" + UUID.randomUUID() else "sale:" + saleId

    suspend fun enqueue(
        shopId: String, deviceId: String, kind: String, bytes: ByteArray,
        requestKey: String, saleId: String? = null, receiptNumber: String? = null
    ): String {
        require(bytes.isNotEmpty() && bytes.size <= 190_000) { "Receipt is too large to queue." }
        val result = action(shopId, "enqueue", buildJsonObject {
            put("device_id", deviceId)
            put("kind", kind)
            put("request_key", requestKey)
            put("payload_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            saleId?.let { put("sale_id", it) }
            receiptNumber?.let { put("receipt_number", it) }
        })
        return result["job_id"]?.toString()?.trim('"') ?: error("Print queue did not return a job ID")
    }
}


/** Persist-before-retry outbox on the remote cashier tablet. No sale writes. */
@Serializable
data class PendingSharedPrint(
    val shopId: String,
    val deviceId: String,
    val requestKey: String,
    val kind: String,
    val saleId: String?,
    val receiptNumber: String?,
    val payloadBase64: String
)

object SharedPrintOutbox {
    private val json = Json { ignoreUnknownKeys = true }
    private const val key = "storepos_pending_shared_print_jobs"
    private fun prefs(context: Context) =
        context.getSharedPreferences("storepos_print_outbox",Context.MODE_PRIVATE)

    @Synchronized private fun all(context: Context): List<PendingSharedPrint> =
        runCatching {
            json.decodeFromString<List<PendingSharedPrint>>(
                prefs(context).getString(key,"[]") ?: "[]"
            )
        }.getOrDefault(emptyList())

    @Synchronized fun count(context: Context, shopId: String): Int =
        all(context).count { it.shopId == shopId }

    @Synchronized fun save(context: Context, job: PendingSharedPrint) {
        val old = all(context)
        if (old.any { it.shopId == job.shopId && it.requestKey == job.requestKey }) return
        require(old.size < 100) { "Local print queue full (100). Reconnect or recover pending jobs." }
        check(prefs(context).edit().putString(key,json.encodeToString(old + job)).commit()) {
            "Could not save receipt to local print outbox"
        }
    }

    @Synchronized private fun remove(context: Context, job: PendingSharedPrint) {
        val remaining = all(context).filterNot {
            it.shopId == job.shopId && it.requestKey == job.requestKey
        }
        check(prefs(context).edit().putString(key,json.encodeToString(remaining)).commit())
    }

    suspend fun flush(context: Context, shopId: String): Int {
        var sent = 0
        for (job in all(context).filter { it.shopId == shopId }) {
            try {
                SharedPrintRepository.enqueue(
                    job.shopId,job.deviceId,job.kind,
                    Base64.decode(job.payloadBase64,Base64.DEFAULT),
                    job.requestKey,job.saleId,job.receiptNumber
                )
                remove(context,job)
                sent++
            } catch (_: Exception) {
                // Do not drop a receipt if the network, authorization or sale sync
                // is not ready. A later retry uses the exact same idempotency key.
                break
            }
        }
        return sent
    }
}
