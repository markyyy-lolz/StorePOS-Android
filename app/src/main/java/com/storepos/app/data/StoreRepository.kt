package com.storepos.app.data

import android.util.Base64
import com.storepos.app.BuildConfig
import com.storepos.app.update.latestStorePosVersion
import com.storepos.app.data.model.*
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object StoreRepository {
    private val client get() = SupabaseProvider.client

    suspend fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id

    fun currentUserEmail(): String? =
        client.auth.currentSessionOrNull()?.user?.email

    fun mustChangePassword(): Boolean {
        val token = client.auth.currentSessionOrNull()?.accessToken ?: return false
        return runCatching {
            val payload = token.split('.').getOrNull(1) ?: return@runCatching false
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val decoded = String(
                Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val root = Json.parseToJsonElement(decoded).jsonObject
            root["app_metadata"]
                ?.jsonObject
                ?.get("must_change_password")
                ?.jsonPrimitive
                ?.booleanOrNull == true
        }.getOrDefault(false)
    }

    suspend fun changeRequiredPassword(newPassword: String) = withContext(Dispatchers.IO) {
        require(newPassword.length >= 8) { "Your new password must be at least 8 characters." }
        val session = client.auth.currentSessionOrNull()
            ?: error("Authentication required. Sign in again.")
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/storepos-invite-staff"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        val payload = buildJsonObject {
            put("action", "change_password")
            put("new_password", newPassword)
        }.toString()

        connection.outputStream.use { stream ->
            stream.write(payload.toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        val responseStream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (code !in 200..299) {
            val message = runCatching {
                Json.parseToJsonElement(body)
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull().orEmpty()
            error(message.ifBlank { "Unable to change the temporary StorePOS password." })
        }
    }

    suspend fun signIn(email: String, password: String, captchaToken: String) {
        require(captchaToken.isNotBlank()) { "Security verification required. Please complete the verification and try again." }
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
            this.captchaToken = captchaToken
        }
    }

    suspend fun signUp(displayName: String, email: String, password: String, captchaToken: String) {
        require(captchaToken.isNotBlank()) { "Security verification required. Please complete the verification and try again." }
        client.auth.signUpWith(
            Email,
            redirectUrl = "https://storepos.2023107337.workers.dev/#/confirm-email"
        ) {
            this.email = email.trim()
            this.password = password
            this.captchaToken = captchaToken
            data = buildJsonObject {
                put("display_name", displayName.trim().ifBlank { "Owner" })
            }
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    fun userMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()

        return when {
            "unexpected status code returned from hook: 405" in lower ->
                "Account creation is temporarily unavailable because the email confirmation hook is misconfigured. Please contact StorePOS Support."
            "over_email_send_rate_limit" in lower || "email rate limit exceeded" in lower ->
                "Verification email limit reached. Please try again later. StorePOS production sign-ups require a custom SMTP email provider."
            ("security purposes" in lower && "seconds" in lower) -> {
                val seconds = Regex("""after\\s+(\\d+)\\s+seconds""", RegexOption.IGNORE_CASE)
                    .find(raw)?.groupValues?.getOrNull(1)
                if (seconds != null) "Please wait $seconds seconds before trying again."
                else "Please wait about a minute before trying again."
            }
            "captcha_failed" in lower || "captcha failed" in lower || "captcha" in lower || "turnstile" in lower ->
                "Security verification failed or expired. Please complete the Cloudflare verification again."
            "invalid login credentials" in lower || "invalid_credentials" in lower ->
                "Incorrect email or password."
            "email not confirmed" in lower ->
                "Please verify your email first, then sign in."
            "user already registered" in lower || "already been registered" in lower ->
                "An account already exists for this email. Please sign in."
            "plan does not include" in lower ->
                raw.lineSequence().firstOrNull()?.take(220) ?: "This feature is not included in your StorePOS plan."
            "row-level security" in lower || "permission denied" in lower || "not have permission" in lower ->
                "Your account does not have permission to perform this action."
            "duplicate key" in lower && "products_shop_id_sku_key" in lower ->
                "That SKU is already used by another product."
            "duplicate key" in lower ->
                "That record already exists."
            "insufficient stock" in lower ->
                "There is not enough stock to complete this sale."
            "7-day motopos trial has expired" in lower || "trial has expired" in lower ->
                "Your 7-day StorePOS Pro Trial has expired. Ask your StorePOS administrator to activate a license."
            "license has expired" in lower ->
                "Your StorePOS license has expired."
            "license is suspended" in lower ->
                "This StorePOS license is suspended. Contact your StorePOS administrator."
            "device limit reached" in lower ->
                "The device limit for this StorePOS plan has been reached."
            "expected start of the array" in lower || "json input" in lower || "eof" in lower ->
                "StorePOS received an incomplete cloud response. Please retry once."
            "network" in lower || "timeout" in lower || "unable to resolve host" in lower ->
                "Unable to reach StorePOS Cloud. Check your internet connection and try again."
            raw.isBlank() -> "Something went wrong. Please try again."
            else -> raw.lineSequence().firstOrNull()?.take(220) ?: "Something went wrong."
        }
    }

    suspend fun loadShopContext(): ShopContext? {
        val userId = currentUserId() ?: return null
        val memberships = client.from("shop_members").select {
            filter { eq("user_id", userId) }
        }.decodeList<ShopMember>()
            .filter { it.isActive }

        if (memberships.isEmpty()) return null

        val motoShops = client.from("shops").select {
            filter { eq("app_code", "storepos") }
        }.decodeList<Shop>()

        val shopById = motoShops.associateBy { it.id }
        val membership = memberships.firstOrNull { shopById.containsKey(it.shopId) } ?: return null
        val shop = shopById[membership.shopId] ?: return null
        return ShopContext(userId, shop, membership)
    }

    suspend fun shopMembers(shopId: String): List<ShopMember> =
        client.from("shop_members").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<ShopMember>().sortedBy { it.role }

    suspend fun userProfiles(): List<UserProfile> =
        client.from("user_profiles").select().decodeList<UserProfile>()

    // All StorePOS staff mutations go through the server-side authorization,
    // license and audit checks. Do not update shop_members directly.
    private fun executeStaffAction(payload: JsonObject) {
        val session = client.auth.currentSessionOrNull()
            ?: error("Authentication required. Sign in again.")
        val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/storepos-invite-staff"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val details = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull()
                val message = details?.get("error")?.jsonPrimitive?.content.orEmpty()
                val code = details?.get("code")?.jsonPrimitive?.content.orEmpty()
                error((message.ifBlank { "Unable to complete StorePOS staff administration." }) +
                    (if (code.isNotBlank()) " ($code)" else ""))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun createStaffAccount(
        shopId: String,
        displayName: String,
        email: String,
        temporaryPassword: String,
        role: String
    ) = withContext(Dispatchers.IO) {
        require(role in setOf("cashier", "manager", "inventory")) { "Unsupported StorePOS staff role." }
        require(displayName.isNotBlank() && email.contains('@')) { "A full name and valid email are required." }
        require(temporaryPassword.length >= 8) { "Temporary password must be at least 8 characters." }
        executeStaffAction(buildJsonObject {
            put("action", "create")
            put("shop_id", shopId)
            put("display_name", displayName.trim())
            put("email", email.trim())
            put("password", temporaryPassword)
            put("role", role)
        })
    }

    suspend fun updateMemberRole(memberId: String, role: String, active: Boolean = true) =
        withContext(Dispatchers.IO) {
            val member = client.from("shop_members").select {
                filter { eq("id", memberId) }
            }.decodeList<ShopMember>().firstOrNull()
                ?: error("Staff membership could not be found. Refresh and try again.")

            if (role != member.role) {
                require(role in setOf("manager", "cashier", "inventory")) {
                    "Only Manager, Cashier and Inventory roles are allowed."
                }
                executeStaffAction(buildJsonObject {
                    put("action", "update_role")
                    put("shop_id", member.shopId)
                    put("user_id", member.userId)
                    put("role", role)
                })
            }
            if (active != member.isActive) {
                executeStaffAction(buildJsonObject {
                    put("action", "set_active")
                    put("shop_id", member.shopId)
                    put("user_id", member.userId)
                    put("active", active)
                })
            }
        }

    suspend fun deviceSessions(shopId: String): List<DeviceSession> =
        client.from("device_sessions").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<DeviceSession>().sortedByDescending { it.lastSeenAt }

    suspend fun setDeviceSessionActive(sessionId: String, active: Boolean) {
        client.from("device_sessions").update({
            set("is_active", active)
        }) {
            filter { eq("id", sessionId) }
        }
    }

    suspend fun createFirstShop(name: String, phone: String?, address: String?): ShopContext {
        requireNotNull(currentUserId()) { "You must be signed in." }

        client.postgrest.rpc(
            function = "bootstrap_shop_v2",
            parameters = buildJsonObject {
                put("p_name", name.trim())
                phone?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_phone", it) }
                address?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_address", it) }
                put("p_app_code", "storepos")
                put("p_business_type", "retail")
            }
        )

        return loadShopContext()
            ?: error("Workspace setup completed, but StorePOS could not reload the shop. Please sign in again.")
    }

    suspend fun validateDeviceAccess(
        shopId: String,
        deviceId: String,
        deviceName: String,
        appVersion: String
    ): LicenseAccess =
        client.postgrest.rpc(
            function = "validate_device_access",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_device_id", deviceId)
                put("p_device_name", deviceName)
                put("p_app_version", appVersion)
            }
        ).decodeSingle()

    suspend fun activateDeviceAccess(
        shopId: String,
        licenseKey: String,
        deviceId: String,
        deviceName: String,
        appVersion: String
    ): LicenseAccess =
        client.postgrest.rpc(
            function = "activate_device_access",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_license_key", licenseKey.trim())
                put("p_device_id", deviceId)
                put("p_device_name", deviceName)
                put("p_app_version", appVersion)
            }
        ).decodeSingle()

    suspend fun shopEntitlements(shopId: String): PlanEntitlements =
        client.postgrest.rpc(
            function = "get_shop_entitlements",
            parameters = buildJsonObject { put("p_shop_id", shopId) }
        ).decodeAs()

    suspend fun shopAlerts(shopId: String): List<ShopAlert> =
        client.postgrest.rpc(
            function = "get_shop_alerts",
            parameters = buildJsonObject { put("p_shop_id", shopId) }
        ).decodeList()


    suspend fun shopSettings(shopId: String): ShopSettings {
        val rows = client.from("shop_settings").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<ShopSettings>()
        return rows.firstOrNull() ?: ShopSettings(shopId = shopId)
    }

    suspend fun updateShopSettings(settings: ShopSettings) {
        client.from("shop_settings").upsert(settings)
    }

    suspend fun products(shopId: String): List<Product> =
        client.from("products").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Product>().sortedBy { it.name.lowercase() }

    suspend fun addProduct(input: ProductInsert): Product {
        val openingStock = input.stockQuantity
        val product = client.from("products")
            .insert(input.copy(stockQuantity = 0.0)) { select() }
            .decodeSingle<Product>()
        return if (openingStock > 0.0) {
            adjustInventoryStock(product.id, openingStock, "opening", "Opening stock")
        } else product
    }

    suspend fun adjustInventoryStock(
        productId: String,
        quantityDelta: Double,
        reason: String = "adjustment",
        notes: String? = null
    ): Product =
        client.postgrest.rpc(
            function = "adjust_inventory_stock",
            parameters = buildJsonObject {
                put("p_product_id", productId)
                put("p_quantity_delta", quantityDelta)
                put("p_reason", reason)
                notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_notes", it) }
            }
        ).decodeSingle()

    suspend fun updateProduct(product: Product) {
        client.from("products").update({
            set("name", product.name)
            set("category_id", product.categoryId)
            set("brand", product.brand)
            set("barcode", product.barcode)
            set("part_number", product.partNumber)
            set("cost_price", product.costPrice)
            set("selling_price", product.sellingPrice)
            set("reorder_level", product.reorderLevel)
            set("unit", product.unit)
            set("is_active", product.isActive)
        }) {
            filter { eq("id", product.id) }
        }
    }

    suspend fun deleteProduct(id: String) {
        client.from("products").delete {
            filter { eq("id", id) }
        }
    }

    suspend fun inventoryCounts(shopId: String): List<InventoryCount> =
        client.from("inventory_counts").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<InventoryCount>().sortedByDescending { it.createdAt }

    suspend fun inventoryCountItems(countId: String): List<InventoryCountItem> =
        client.from("inventory_count_items").select {
            filter { eq("count_id", countId) }
        }.decodeList()

    suspend fun startInventoryCount(shopId: String): InventoryCount =
        client.postgrest.rpc(
            "start_inventory_count",
            buildJsonObject { put("p_shop_id", shopId) }
        ).decodeSingle()

    suspend fun submitInventoryCount(
        countId: String,
        items: List<Pair<String, Double>>
    ): InventoryCount =
        client.postgrest.rpc(
            "submit_inventory_count",
            buildJsonObject {
                put("p_count_id", countId)
                put("p_counts", buildJsonArray {
                    items.forEach { (productId, counted) ->
                        add(buildJsonObject {
                            put("product_id", productId)
                            put("counted_quantity", counted)
                        })
                    }
                })
            }
        ).decodeSingle()

    suspend fun approveInventoryCount(countId: String): InventoryCount =
        client.postgrest.rpc(
            "approve_inventory_count",
            buildJsonObject { put("p_count_id", countId) }
        ).decodeSingle()

    suspend fun categories(shopId: String): List<ProductCategory> =
        client.from("product_categories").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<ProductCategory>()
            .sortedWith(compareBy<ProductCategory> { it.sortOrder }.thenBy { it.name.lowercase() })

    suspend fun addCategory(input: ProductCategoryInsert): ProductCategory =
        client.from("product_categories").insert(input) { select() }.decodeSingle()

    suspend fun updateCategory(category: ProductCategory) {
        client.from("product_categories").update({
            set("name", category.name.trim())
            set("description", category.description?.trim()?.ifBlank { null })
            set("sort_order", category.sortOrder)
            set("is_active", category.isActive)
        }) {
            filter { eq("id", category.id) }
        }
    }

    suspend fun customers(shopId: String): List<Customer> =
        client.from("customers").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Customer>().sortedBy { it.name.lowercase() }

    suspend fun addCustomer(input: CustomerInsert): Customer =
        client.from("customers").insert(input) { select() }.decodeSingle()

    suspend fun motorcycles(shopId: String): List<Motorcycle> =
        client.from("motorcycles").select {
            filter { eq("shop_id", shopId) }
        }.decodeList()

    suspend fun motorcyclesForCustomer(customerId: String): List<Motorcycle> =
        client.from("motorcycles").select {
            filter { eq("customer_id", customerId) }
        }.decodeList()

    suspend fun addMotorcycle(input: MotorcycleInsert): Motorcycle =
        client.from("motorcycles").insert(input) { select() }.decodeSingle()

    suspend fun services(shopId: String): List<ServiceItem> =
        client.from("service_catalog").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<ServiceItem>().sortedBy { it.name.lowercase() }

    suspend fun jobs(shopId: String): List<JobOrder> =
        client.from("job_orders").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<JobOrder>().sortedByDescending { it.createdAt ?: "" }

    suspend fun addJob(input: JobOrderInsert): JobOrder =
        client.from("job_orders").insert(input) { select() }.decodeSingle()

    suspend fun updateJobStatus(id: String, status: String) {
        client.from("job_orders").update({
            set("status", status)
        }) {
            filter { eq("id", id) }
        }
    }

    suspend fun suppliers(shopId: String): List<Supplier> =
        client.from("suppliers").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Supplier>().sortedBy { it.name.lowercase() }

    suspend fun addSupplier(input: SupplierInsert): Supplier =
        client.from("suppliers").insert(input) { select() }.decodeSingle()

    suspend fun purchaseOrders(shopId: String): List<PurchaseOrder> =
        client.from("purchase_orders").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<PurchaseOrder>().sortedByDescending { it.createdAt ?: "" }

    suspend fun sales(shopId: String): List<Sale> =
        client.from("sales").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Sale>().sortedByDescending { it.createdAt ?: "" }

    suspend fun expenses(shopId: String): List<Expense> =
        client.from("expenses").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Expense>().sortedByDescending { it.expenseDate }

    suspend fun addExpense(input: ExpenseInsert): Expense =
        client.from("expenses").insert(input) { select() }.decodeSingle()

    suspend fun supportThreads(shopId: String): List<SupportThread> =
        client.from("support_threads").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<SupportThread>().sortedByDescending { it.lastMessageAt ?: it.createdAt ?: "" }

    suspend fun supportMessages(threadId: String): List<SupportMessage> =
        client.from("support_messages").select {
            filter { eq("thread_id", threadId) }
        }.decodeList<SupportMessage>().sortedBy { it.createdAt ?: "" }

    suspend fun createSupportThread(
        shopId: String,
        subject: String,
        priority: String,
        firstMessage: String
    ): SupportThread {
        val created = client.postgrest.rpc(
            function = "create_support_thread",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_subject", subject.trim())
                put("p_priority", priority)
                put("p_first_message", firstMessage.trim())
            }
        ).decodeSingle<SupportThread>()
        requestAutoSupportReply(created.id)
        return created
    }

    suspend fun sendSupportMessage(threadId: String, shopId: String, body: String) {
        val userId = currentUserId() ?: error("Authentication required.")
        client.from("support_messages").insert(
            SupportMessageInsert(
                threadId = threadId,
                shopId = shopId,
                senderId = userId,
                senderType = "customer",
                body = body.trim()
            )
        )
        requestAutoSupportReply(threadId)
    }

    private suspend fun requestAutoSupportReply(threadId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val token = client.auth.currentSessionOrNull()?.accessToken ?: return@runCatching
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/support-auto-reply"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = """{"thread_id":"$threadId"}"""
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val responseStream = if (code in 200..299) connection.inputStream else connection.errorStream
            responseStream?.use { it.readBytes() }
            connection.disconnect()
        }
    }


    suspend fun heldSales(shopId: String): List<HeldSale> =
        client.from("held_sales").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<HeldSale>().sortedByDescending { it.createdAt ?: "" }

    suspend fun holdSale(
        shopId: String,
        cart: List<CartLine>,
        customerId: String? = null,
        motorcycleId: String? = null,
        label: String? = null,
        notes: String? = null,
        discount: Double = 0.0,
        tax: Double = 0.0
    ): HeldSale {
        val userId = currentUserId() ?: error("Authentication required.")
        require(cart.isNotEmpty()) { "Cart is empty." }
        return client.from("held_sales").insert(
            HeldSaleInsert(
                shopId = shopId,
                heldBy = userId,
                customerId = customerId,
                motorcycleId = motorcycleId,
                label = label?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                discountAmount = discount,
                taxAmount = tax,
                items = cart.map { HeldSaleItem(it.product.id, it.quantity, it.unitPriceOverride) }
            )
        ) { select() }.decodeSingle()
    }

    suspend fun deleteHeldSale(id: String) {
        client.from("held_sales").delete { filter { eq("id", id) } }
    }

    suspend fun recordCashMovement(
        shiftId: String,
        type: String,
        amount: Double,
        reason: String
    ): CashMovement =
        client.postgrest.rpc(
            "record_cash_movement",
            buildJsonObject {
                put("p_shift_id", shiftId)
                put("p_type", type)
                put("p_amount", amount)
                put("p_reason", reason.trim())
            }
        ).decodeSingle()

    suspend fun zReports(shopId: String): List<ZReport> =
        client.from("z_reports").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<ZReport>().sortedByDescending { it.generatedAt }

    suspend fun receivables(shopId: String): List<CustomerReceivable> =
        client.from("customer_receivables").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<CustomerReceivable>().sortedByDescending { it.createdAt }

    suspend fun receiveCustomerPayment(
        receivableId: String,
        amount: Double,
        method: String,
        referenceNumber: String?
    ): CustomerReceivable =
        client.postgrest.rpc(
            "receive_customer_payment",
            buildJsonObject {
                put("p_receivable_id", receivableId)
                put("p_amount", amount)
                put("p_method", method)
                referenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_reference_number", it) }
            }
        ).decodeSingle()

    suspend fun cashierShifts(shopId: String): List<CashierShift> =
        client.from("cashier_shifts").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<CashierShift>().sortedByDescending { it.startedAt }

    suspend fun startCashierShift(shopId: String, openingCash: Double): CashierShift =
        client.postgrest.rpc(
            "start_cashier_shift",
            buildJsonObject {
                put("p_shop_id", shopId)
                put("p_opening_cash", openingCash)
            }
        ).decodeSingle()

    suspend fun endCashierShift(shiftId: String, actualCash: Double, notes: String?): CashierShift =
        client.postgrest.rpc(
            "end_cashier_shift",
            buildJsonObject {
                put("p_shift_id", shiftId)
                put("p_actual_cash", actualCash)
                notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_notes", it) }
            }
        ).decodeSingle()

    suspend fun setManagerPin(shopId: String, pin: String) {
        client.postgrest.rpc(
            "set_manager_pin",
            buildJsonObject {
                put("p_shop_id", shopId)
                put("p_pin", pin)
            }
        )
    }

    suspend fun returnableSaleItems(saleId: String): List<ReturnableSaleItem> =
        client.postgrest.rpc(
            "returnable_sale_items",
            buildJsonObject { put("p_sale_id", saleId) }
        ).decodeList()

    suspend fun saleItems(saleId: String): List<SaleItem> =
        client.from("sale_items").select {
            filter { eq("sale_id", saleId) }
        }.decodeList()

    /** Recorded payment methods and amounts for a historical thermal receipt copy. */
    suspend fun salePayments(shopId: String, saleId: String): List<CheckoutPayment> =
        client.from("payments").select {
            filter {
                eq("shop_id", shopId)
                eq("sale_id", saleId)
                eq("status", "paid")
            }
        }.decodeList()

    suspend fun voidSale(saleId: String, managerPin: String, reason: String): Sale =
        client.postgrest.rpc(
            "void_sale_transaction",
            buildJsonObject {
                put("p_sale_id", saleId)
                put("p_manager_pin", managerPin)
                put("p_reason", reason.trim())
            }
        ).decodeSingle()

    suspend fun returnEntireSale(
        sale: Sale,
        managerPin: String,
        reason: String,
        refundMethod: String
    ): SaleReturn {
        val items = saleItems(sale.id)
        require(items.isNotEmpty()) { "Sale has no returnable items." }
        return client.postgrest.rpc(
            "return_sale_items",
            buildJsonObject {
                put("p_sale_id", sale.id)
                put("p_manager_pin", managerPin)
                put("p_reason", reason.trim())
                put("p_refund_method", refundMethod)
                put("p_items", buildJsonArray {
                    items.forEach { item ->
                        add(buildJsonObject {
                            put("sale_item_id", item.id)
                            put("quantity", item.quantity)
                            put("restock", item.productId != null)
                        })
                    }
                })
            }
        ).decodeSingle()
    }

    suspend fun returnSaleItems(
        saleId: String,
        items: List<Triple<ReturnableSaleItem, Double, Boolean>>,
        managerPin: String,
        reason: String,
        refundMethod: String
    ): SaleReturn {
        require(items.isNotEmpty()) { "Select at least one item to return." }
        return client.postgrest.rpc(
            "return_sale_items",
            buildJsonObject {
                put("p_sale_id", saleId)
                put("p_manager_pin", managerPin)
                put("p_reason", reason.trim())
                put("p_refund_method", refundMethod)
                put("p_items", buildJsonArray {
                    items.forEach { (item, qty, restock) ->
                        add(buildJsonObject {
                            put("sale_item_id", item.saleItemId)
                            put("quantity", qty)
                            put("restock", restock)
                        })
                    }
                })
            }
        ).decodeSingle()
    }

    suspend fun appointments(shopId: String): List<Appointment> =
        client.from("appointments").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Appointment>().sortedBy { it.scheduledAt }

    suspend fun addAppointment(input: AppointmentInsert): Appointment =
        client.from("appointments").insert(input) { select() }.decodeSingle()

    suspend fun updateAppointmentStatus(id: String, status: String) {
        client.from("appointments").update({ set("status", status) }) {
            filter { eq("id", id) }
        }
    }

    suspend fun serviceReminders(shopId: String): List<ServiceReminder> =
        client.from("service_reminders").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<ServiceReminder>().sortedWith(
            compareBy<ServiceReminder> { it.status != "pending" }
                .thenBy { it.dueDate ?: "9999-12-31" }
        )

    suspend fun addServiceReminder(input: ServiceReminderInsert): ServiceReminder =
        client.from("service_reminders").insert(input) { select() }.decodeSingle()

    suspend fun updateServiceReminderStatus(id: String, status: String) {
        client.from("service_reminders").update({
            set("status", status)
        }) {
            filter { eq("id", id) }
        }
    }

    suspend fun warranties(shopId: String): List<Warranty> =
        client.from("warranties").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Warranty>().sortedByDescending { it.startsOn }

    suspend fun warrantyClaims(shopId: String): List<WarrantyClaim> =
        client.from("warranty_claims").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<WarrantyClaim>().sortedByDescending { it.createdAt ?: "" }

    suspend fun addWarrantyClaim(input: WarrantyClaimInsert): WarrantyClaim =
        client.from("warranty_claims").insert(input) { select() }.decodeSingle()

    suspend fun technicianTimers(shopId: String): List<TechnicianTimeEntry> =
        client.from("technician_time_entries").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<TechnicianTimeEntry>().sortedByDescending { it.startedAt }

    suspend fun startTechnicianTimer(jobOrderId: String, notes: String? = null): TechnicianTimeEntry =
        client.postgrest.rpc(
            "start_technician_timer",
            buildJsonObject {
                put("p_job_order_id", jobOrderId)
                notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_notes", it) }
            }
        ).decodeSingle()

    suspend fun stopTechnicianTimer(entryId: String): TechnicianTimeEntry =
        client.postgrest.rpc(
            "stop_technician_timer",
            buildJsonObject { put("p_entry_id", entryId) }
        ).decodeSingle()

    suspend fun purchaseOrderItems(purchaseOrderId: String): List<PurchaseOrderItem> =
        client.from("purchase_order_items").select {
            filter { eq("purchase_order_id", purchaseOrderId) }
        }.decodeList()

    suspend fun createPurchaseOrder(
        shopId: String,
        supplierId: String,
        lines: List<Triple<Product, Double, Double>>,
        notes: String? = null,
        expectedAt: String? = null
    ): PurchaseOrder =
        client.postgrest.rpc(
            "create_purchase_order_v2",
            buildJsonObject {
                put("p_shop_id", shopId)
                put("p_supplier_id", supplierId)
                notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_notes", it) }
                expectedAt?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_expected_at", it) }
                put("p_items", buildJsonArray {
                    lines.forEach { (product, qty, cost) ->
                        add(buildJsonObject {
                            put("product_id", product.id)
                            put("quantity", qty)
                            put("unit_cost", cost)
                        })
                    }
                })
            }
        ).decodeSingle()

    suspend fun receivePurchaseOrder(orderId: String, items: List<PurchaseOrderItem>): PurchaseOrder =
        client.postgrest.rpc(
            "receive_purchase_order",
            buildJsonObject {
                put("p_purchase_order_id", orderId)
                put("p_items", buildJsonArray {
                    items.filter { it.quantityReceived < it.quantityOrdered }.forEach {
                        add(buildJsonObject {
                            put("purchase_order_item_id", it.id)
                            put("quantity", it.quantityOrdered - it.quantityReceived)
                        })
                    }
                })
            }
        ).decodeSingle()

    suspend fun createQuotation(
        shopId: String,
        customerId: String?,
        motorcycleId: String?,
        items: List<Map<String, Any?>>,
        discount: Double,
        validUntil: String?,
        notes: String?
    ): Quotation =
        client.postgrest.rpc(
            "create_quotation_v2",
            buildJsonObject {
                put("p_shop_id", shopId)
                customerId?.let { put("p_customer_id", it) }
                motorcycleId?.let { put("p_motorcycle_id", it) }
                put("p_discount_amount", discount)
                validUntil?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_valid_until", it) }
                notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_notes", it) }
                put("p_items", buildJsonArray {
                    items.forEach { line ->
                        add(buildJsonObject {
                            put("item_type", line["item_type"] as? String ?: "other")
                            (line["product_id"] as? String)?.let { put("product_id", it) }
                            (line["service_id"] as? String)?.let { put("service_id", it) }
                            put("description", line["description"] as? String ?: "Item")
                            put("quantity", (line["quantity"] as? Number)?.toDouble() ?: 1.0)
                            put("unit_price", (line["unit_price"] as? Number)?.toDouble() ?: 0.0)
                        })
                    }
                })
            }
        ).decodeSingle()

    suspend fun convertQuotationToJob(quotationId: String): JobOrder =
        client.postgrest.rpc(
            "convert_quotation_to_job",
            buildJsonObject { put("p_quotation_id", quotationId) }
        ).decodeSingle()

    suspend fun quotations(shopId: String): List<Quotation> =
        client.from("quotations").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<Quotation>().sortedByDescending { it.createdAt ?: "" }

    suspend fun analyticsSummary(shopId: String, days: Int = 30): AnalyticsSummary =
        client.postgrest.rpc(
            "analytics_summary",
            buildJsonObject {
                put("p_shop_id", shopId)
                put("p_days", days)
            }
        ).decodeAs()


    suspend fun adjustCustomerLoyalty(customerId: String, points: Int, reason: String) {
        client.postgrest.rpc(
            "adjust_loyalty_points",
            buildJsonObject {
                put("p_customer_id", customerId)
                put("p_points", points)
                put("p_reason", reason.trim())
            }
        )
    }

    suspend fun adjustCustomerStoreCredit(customerId: String, amount: Double, reason: String) {
        client.postgrest.rpc(
            "adjust_store_credit",
            buildJsonObject {
                put("p_customer_id", customerId)
                put("p_amount", amount)
                put("p_reason", reason.trim())
            }
        )
    }

    suspend fun createCustomerPortalToken(customerId: String, days: Int = 30): String =
        client.postgrest.rpc(
            "create_customer_portal_token",
            buildJsonObject {
                put("p_customer_id", customerId)
                put("p_days", days)
            }
        ).decodeAs()


    suspend fun accessibleBranches(): List<AccessibleBranch> {
        val userId = currentUserId() ?: return emptyList()
        val members = client.from("shop_members").select {
            filter {
                eq("user_id", userId)
                eq("is_active", true)
            }
        }.decodeList<ShopMember>()

        return members.mapNotNull { member ->
            runCatching {
                val shop = client.from("shops").select {
                    filter { eq("id", member.shopId) }
                }.decodeSingle<Shop>()
                AccessibleBranch(shop, member)
            }.getOrNull()
        }
    }

    suspend fun stockTransfers(): List<StockTransfer> =
        client.from("stock_transfers").select()
            .decodeList<StockTransfer>()
            .sortedByDescending { it.createdAt ?: "" }

    suspend fun createStockTransfer(
        fromShopId: String,
        toShopId: String,
        productId: String,
        quantity: Double,
        notes: String? = null
    ): StockTransfer =
        client.postgrest.rpc(
            "create_stock_transfer",
            buildJsonObject {
                put("p_from_shop_id", fromShopId)
                put("p_to_shop_id", toShopId)
                notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_notes", it) }
                put("p_items", buildJsonArray {
                    add(buildJsonObject {
                        put("from_product_id", productId)
                        put("quantity", quantity)
                    })
                })
            }
        ).decodeSingle()

    suspend fun shipStockTransfer(transferId: String): StockTransfer =
        client.postgrest.rpc(
            "ship_stock_transfer",
            buildJsonObject { put("p_transfer_id", transferId) }
        ).decodeSingle()

    suspend fun receiveStockTransfer(transferId: String): StockTransfer =
        client.postgrest.rpc(
            "receive_stock_transfer",
            buildJsonObject { put("p_transfer_id", transferId) }
        ).decodeSingle()

    suspend fun latestVersion(): AppVersion? =
        client.from("app_versions").select {
            filter {
                eq("is_published", true)
                eq("app_code", "storepos")
            }
        }.decodeList<AppVersion>().let(::latestStorePosVersion)


    suspend fun completeOfflineSale(payload: OfflineSalePayload): Sale {
        if (payload.payments.isEmpty()) {
            return client.postgrest.rpc(
                function = "complete_sale_transaction_v2",
                parameters = buildJsonObject {
                    put("p_client_key", payload.clientKey)
                    put("p_shop_id", payload.shopId)
                    payload.customerId?.let { put("p_customer_id", it) }
                    payload.motorcycleId?.let { put("p_motorcycle_id", it) }
                    payload.jobOrderId?.let { put("p_job_order_id", it) }
                    put("p_discount_amount", payload.discountAmount)
                    put("p_tax_amount", payload.taxAmount)
                    payload.amountTendered?.let { put("p_amount_tendered", it) }
                    put("p_payment_method", payload.paymentMethod)
                    payload.referenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_reference_number", it) }
                    put("p_items", buildJsonArray {
                        payload.items.forEach { line ->
                            add(buildJsonObject {
                                put("product_id", line.productId)
                                put("quantity", line.quantity)
                                line.unitPrice?.let { put("unit_price", it) }
                            })
                        }
                    })
                }
            ).decodeSingle()
        }

        return client.postgrest.rpc(
            function = "complete_sale_transaction_v3",
            parameters = buildJsonObject {
                put("p_client_key", payload.clientKey)
                put("p_shop_id", payload.shopId)
                payload.customerId?.let { put("p_customer_id", it) }
                payload.motorcycleId?.let { put("p_motorcycle_id", it) }
                payload.jobOrderId?.let { put("p_job_order_id", it) }
                put("p_discount_amount", payload.discountAmount)
                put("p_tax_amount", payload.taxAmount)
                put("p_items", buildJsonArray {
                    payload.items.forEach { line ->
                        add(buildJsonObject {
                            put("product_id", line.productId)
                            put("quantity", line.quantity)
                            line.unitPrice?.let { put("unit_price", it) }
                        })
                    }
                })
                put("p_payments", buildJsonArray {
                    payload.payments.forEach { payment ->
                        add(buildJsonObject {
                            put("method", payment.method)
                            put("amount", payment.amount)
                            payment.tendered?.let { put("tendered", it) }
                            payment.referenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let {
                                put("reference_number", it)
                            }
                        })
                    }
                })
            }
        ).decodeSingle()
    }

    suspend fun completeSaleV3(
        shopId: String,
        customerId: String?,
        motorcycleId: String?,
        jobOrderId: String?,
        cart: List<CartLine>,
        discount: Double,
        tax: Double,
        payments: List<CheckoutPayment>,
        managerPin: String? = null,
        clientKey: String = java.util.UUID.randomUUID().toString()
    ): Sale {
        require(cart.isNotEmpty()) { "Cart is empty." }
        require(cart.all { it.quantity > 0 }) { "Cart contains an invalid quantity." }
        require(discount >= 0 && tax >= 0) { "Discount and tax cannot be negative." }
        require(payments.isNotEmpty()) { "At least one payment is required." }

        return client.postgrest.rpc(
            function = "complete_sale_transaction_v3",
            parameters = buildJsonObject {
                put("p_client_key", clientKey)
                put("p_shop_id", shopId)
                customerId?.let { put("p_customer_id", it) }
                motorcycleId?.let { put("p_motorcycle_id", it) }
                jobOrderId?.let { put("p_job_order_id", it) }
                put("p_discount_amount", discount)
                put("p_tax_amount", tax)
                managerPin?.trim()?.takeIf { it.isNotBlank() }?.let { put("p_manager_pin", it) }
                put("p_items", buildJsonArray {
                    cart.forEach { line ->
                        add(buildJsonObject {
                            put("product_id", line.product.id)
                            put("quantity", line.quantity)
                            line.unitPriceOverride?.let { put("unit_price", it) }
                        })
                    }
                })
                put("p_payments", buildJsonArray {
                    payments.forEach { payment ->
                        add(buildJsonObject {
                            put("method", payment.method)
                            put("amount", payment.amount)
                            payment.tendered?.let { put("tendered", it) }
                            payment.referenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let {
                                put("reference_number", it)
                            }
                        })
                    }
                })
            }
        ).decodeSingle()
    }

    suspend fun completeSale(
        shopId: String,
        customerId: String?,
        motorcycleId: String?,
        jobOrderId: String?,
        cart: List<CartLine>,
        discount: Double,
        tax: Double,
        amountTendered: Double,
        paymentMethod: String,
        referenceNumber: String?
    ): Sale {
        require(cart.isNotEmpty()) { "Cart is empty." }
        require(cart.all { it.quantity > 0 }) { "Cart contains an invalid quantity." }
        require(discount >= 0 && tax >= 0) { "Discount and tax cannot be negative." }

        return client.postgrest.rpc(
            function = "complete_sale_transaction",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                customerId?.let { put("p_customer_id", it) }
                motorcycleId?.let { put("p_motorcycle_id", it) }
                jobOrderId?.let { put("p_job_order_id", it) }
                put("p_discount_amount", discount)
                put("p_tax_amount", tax)
                put("p_amount_tendered", amountTendered)
                put("p_payment_method", paymentMethod)
                referenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let {
                    put("p_reference_number", it)
                }
                put("p_items", buildJsonArray {
                    cart.forEach { line ->
                        add(buildJsonObject {
                            put("product_id", line.product.id)
                            put("quantity", line.quantity)
                        })
                    }
                })
            }
        ).decodeSingle()
    }

}
