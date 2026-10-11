package com.storepos.app.data.local

import android.content.Context
import android.util.Base64
import com.storepos.app.data.model.OfflineSalePayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class LanCheckoutRequest(
    val sale: OfflineSalePayload,
    val receiptNumber: String,
    val paperWidth: Int,
    val thermalBase64: String
)

data class LanHubConfig(
    val mode: String = "off",
    val host: String = "",
    val secret: String = ""
)

/**
 * Optional single-authority stock ledger on a trusted, isolated shop Wi-Fi LAN.
 * Mode "host" should run on the primary StorePOS tablet; peers use "client".
 * All LAN sale writes pass through the host's atomic SQLite transaction.
 *
 * An isolated device without access to its configured host must never silently
 * accept stock-tracked sales: simultaneous inventory cannot be guaranteed.
 */
object LanStockHub {
    const val PORT = 18763
    private const val PREFS = "storepos_lan_stock_hub"
    private const val MAX_PACKET = 2 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val clients = Executors.newFixedThreadPool(4)
    private val lock = Any()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var currentShop: String? = null
    @Volatile private var currentSecret: String? = null

    fun load(context: Context, shopId: String): LanHubConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LanHubConfig(
            mode = prefs.getString("mode_$shopId", "off") ?: "off",
            host = prefs.getString("host_$shopId", "") ?: "",
            secret = prefs.getString("secret_$shopId", "") ?: ""
        )
    }

    fun save(context: Context, shopId: String, config: LanHubConfig) {
        require(config.mode in setOf("off", "host", "client"))
        if (config.mode != "off") require(config.secret.length >= 32) {
            "A 32+ character random LAN pairing key is required."
        }
        if (config.mode == "client") require(config.host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) &&
            config.host.split('.').all { segment -> segment.toIntOrNull()?.let { it in 0..255 } == true }) {
            "Enter the host's Wi-Fi IPv4 address."
        }
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode_$shopId", config.mode)
            .putString("host_$shopId", config.host.trim())
            .putString("secret_$shopId", config.secret.trim())
            .commit()) { "Could not save LAN Hub settings." }
    }

    fun newPairingKey(): String = UUID.randomUUID().toString().replace("-", "") +
        UUID.randomUUID().toString().replace("-", "")

    fun localAddress(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { iface ->
            if (!iface.isUp || iface.isLoopback) emptyList()
            else Collections.list(iface.inetAddresses)
        }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress ?: "Not on Wi-Fi/LAN"
    }.getOrDefault("Unavailable")

    /** Call from the main StorePOS session lifecycle; does not need cloud internet. */
    fun ensureRunning(context: Context, shopId: String) {
        val config = load(context, shopId)
        synchronized(lock) {
            val active = server?.isClosed == false
            if (config.mode != "host" || config.secret.isBlank()) {
                closeServer()
                return
            }
            if (active && currentShop == shopId && currentSecret == config.secret) return
            closeServer()
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(PORT), 16)
            server = socket
            currentShop = shopId
            currentSecret = config.secret
            val ctx = context.applicationContext
            Thread({
                while (!socket.isClosed) {
                    try {
                        val incoming = socket.accept()
                        clients.execute { handle(ctx, socket, incoming, shopId, config.secret) }
                    } catch (_: Exception) {
                        if (socket.isClosed) break
                    }
                }
            }, "storepos-lan-stock-hub").apply { isDaemon = true; start() }
        }
    }

    fun stop() = synchronized(lock) { closeServer() }

    private fun closeServer() {
        runCatching { server?.close() }
        server = null
        currentShop = null
        currentSecret = null
    }

    fun reserve(
        context: Context,
        shopId: String,
        payload: OfflineSalePayload,
        receiptNumber: String,
        paperWidth: Int,
        thermalBytes: ByteArray
    ): Map<String, Double> {
        val config = load(context, shopId)
        require(config.secret.length >= 32) { "Configure LAN Hub first." }
        require(payload.shopId == shopId && payload.catalogEpoch != null) {
            "Trusted shop catalog required."
        }
        val request = LanCheckoutRequest(
            sale = payload,
            receiptNumber = receiptNumber,
            paperWidth = paperWidth,
            thermalBase64 = Base64.encodeToString(thermalBytes, Base64.NO_WRAP)
        )
        return when (config.mode) {
            "host" -> {
                check(server?.isClosed == false && currentShop == shopId) {
                    "LAN Hub host not active."
                }
                LanHostLedger.accept(context, payload, receiptNumber, paperWidth, thermalBytes)
            }
            "client" -> exchange(config, shopId, "reserve", json.encodeToString(request))
            else -> error("LAN Hub is disabled.")
        }
    }

    fun stock(context: Context, shopId: String): Map<String, Double> {
        val config = load(context, shopId)
        return when (config.mode) {
            "host" -> {
                check(server?.isClosed == false && currentShop == shopId) {
                    "LAN Hub service is not running."
                }
                val local = OfflineStore(context)
                try { local.loadProducts(shopId).associate { it.id to it.stockQuantity } }
                finally { local.close() }
            }
            "client" -> exchange(config, shopId, "stock", "")
            else -> emptyMap()
        }
    }

    private fun exchange(
        config: LanHubConfig,
        shopId: String,
        action: String,
        data: String
    ): Map<String, Double> {
        val nonce = UUID.randomUUID().toString()
        val body = Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val epoch = System.currentTimeMillis().toString()
        val signature = sign(config.secret, listOf(shopId, action, nonce, epoch, body).joinToString("\n"))
        val request = buildJsonObject {
            put("shop", shopId)
            put("action", action)
            put("nonce", nonce)
            put("epoch", epoch)
            put("body", body)
            put("mac", signature)
        }.toString()
        val reply = Socket().use { socket ->
            socket.connect(InetSocketAddress(config.host, PORT), 2500)
            socket.soTimeout = 5500
            writePacket(socket, request)
            json.parseToJsonElement(readPacket(socket)).let { it as kotlinx.serialization.json.JsonObject }
        }
        val replyNonce = reply["nonce"]?.jsonPrimitive?.content
        require(replyNonce == nonce) { "LAN Hub response did not match the request." }
        val responseBody = reply["body"]?.jsonPrimitive?.content ?: error("Invalid LAN response")
        val responseSignature = reply["mac"]?.jsonPrimitive?.content.orEmpty()
        require(secureEquals(responseSignature, sign(config.secret, "$nonce\n$responseBody"))) {
            "LAN Hub response verification failed."
        }
        val success = reply["ok"]?.jsonPrimitive?.content == "true"
        if (!success) error(String(Base64.decode(responseBody, Base64.DEFAULT), Charsets.UTF_8))
        return json.decodeFromString(String(Base64.decode(responseBody, Base64.DEFAULT), Charsets.UTF_8))
    }

    private fun handle(
        context: Context,
        boundServer: ServerSocket,
        socket: Socket,
        shopId: String,
        secret: String
    ) {
        socket.use { connection ->
            var authenticatedNonce: String? = null
            try {
                connection.soTimeout = 5500
                if (boundServer.isClosed || currentShop != shopId) return
                val request = json.parseToJsonElement(readPacket(connection)) as kotlinx.serialization.json.JsonObject
                val claimedShop = request["shop"]?.jsonPrimitive?.content.orEmpty()
                val nonce = request["nonce"]?.jsonPrimitive?.content.orEmpty()
                val action = request["action"]?.jsonPrimitive?.content.orEmpty()
                val epoch = request["epoch"]?.jsonPrimitive?.content.orEmpty()
                val body = request["body"]?.jsonPrimitive?.content.orEmpty()
                val claimedMac = request["mac"]?.jsonPrimitive?.content.orEmpty()
                require(claimedShop == shopId && nonce.length in 20..64)
                require(action == "stock" || action == "reserve")
                val millis = epoch.toLongOrNull() ?: error("Invalid LAN request timestamp")
                require(kotlin.math.abs(System.currentTimeMillis() - millis) <= 120_000L) {
                    "Check that both tablet clocks use automatic time."
                }
                val expected = sign(secret, listOf(shopId, action, nonce, epoch, body).joinToString("\n"))
                require(secureEquals(claimedMac, expected)) { "LAN pairing key mismatch." }
                authenticatedNonce = nonce
                val store = OfflineStore(context)
                val result = try {
                    when (action) {
                        "stock" -> store.loadProducts(shopId).associate { it.id to it.stockQuantity }
                        "reserve" -> {
                            val bytes = Base64.decode(body, Base64.DEFAULT)
                            val request = json.decodeFromString<LanCheckoutRequest>(
                                String(bytes, Charsets.UTF_8)
                            )
                            require(request.sale.shopId == shopId)
                            LanHostLedger.accept(
                                context, request.sale, request.receiptNumber, request.paperWidth,
                                Base64.decode(request.thermalBase64, Base64.DEFAULT)
                            )
                        }
                        else -> error("Unsupported action")
                    }
                } finally { store.close() }
                sendReply(connection, secret, nonce, true, json.encodeToString(result))
            } catch (e: Exception) {
                // Never echo app credentials or raw data in an exception.
                val message = (e.message ?: "LAN request failed").take(180)
                authenticatedNonce?.let { nonce ->
                    runCatching { sendReply(connection, secret, nonce, false, message) }
                }
            }
        }
    }

    private fun sendReply(socket: Socket, secret: String, nonce: String, ok: Boolean, body: String) {
        val encoded = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        writePacket(socket, buildJsonObject {
            put("nonce", nonce)
            put("ok", ok)
            put("body", encoded)
            put("mac", sign(secret, "$nonce\n$encoded"))
        }.toString())
    }

    private fun readPacket(socket: Socket): String {
        val input = DataInputStream(socket.getInputStream())
        val n = input.readInt()
        require(n in 1..MAX_PACKET) { "Invalid LAN packet size" }
        val bytes = ByteArray(n)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun writePacket(socket: Socket, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_PACKET) { "LAN packet too large" }
        DataOutputStream(socket.getOutputStream()).apply {
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
    }

    private fun sign(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun secureEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
