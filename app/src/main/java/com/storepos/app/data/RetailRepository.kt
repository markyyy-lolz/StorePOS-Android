package com.storepos.app.data

import com.storepos.app.data.model.*
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

object RetailRepository {
    private val client get() = SupabaseProvider.client

    private fun cartItems(cart: List<CartLine>) = buildJsonArray {
        cart.forEach { line ->
            add(buildJsonObject {
                put("product_id", line.product.id)
                put("quantity", line.quantity)
                line.unitPriceOverride?.let { put("unit_price", it) }
                if (line.serials.isNotEmpty()) {
                    put("serials", buildJsonArray { line.serials.forEach { serial -> add(serial) } })
                }
            })
        }
    }

    private fun paymentItems(payments: List<CheckoutPayment>) = buildJsonArray {
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
    }

    private fun chargeItems(charges: List<RetailCharge>) = buildJsonArray {
        charges.filter { it.amount != 0.0 }.forEach { charge ->
            add(buildJsonObject {
                put("name", charge.name.trim())
                put("amount", charge.amount)
            })
        }
    }

    private suspend fun action(shopId: String, action: String, data: JsonObject): RetailActionResult =
        client.postgrest.rpc(
            function = "storepos_retail_action",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_action", action)
                put("p_data", data)
            }
        ).decodeAs()

    suspend fun quote(
        shopId: String,
        cart: List<CartLine>,
        discount: Double = 0.0,
        charges: List<RetailCharge> = emptyList(),
        managerPin: String? = null
    ): RetailQuote {
        require(cart.isNotEmpty()) { "Cart is empty." }
        return client.postgrest.rpc(
            function = "storepos_retail_action",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_action", "quote")
                put("p_data", buildJsonObject {
                    put("items", cartItems(cart))
                    put("discount", discount)
                    put("charges", chargeItems(charges))
                    managerPin?.trim()?.takeIf { it.isNotBlank() }?.let { put("manager_pin", it) }
                })
            }
        ).decodeAs()
    }

    suspend fun checkout(
        shopId: String,
        cart: List<CartLine>,
        customerId: String?,
        discount: Double,
        payments: List<CheckoutPayment>,
        managerPin: String? = null,
        charges: List<RetailCharge> = emptyList(),
        dueDate: String? = null,
        orderId: String? = null,
        exchangeSaleId: String? = null,
        clientKey: String = UUID.randomUUID().toString()
    ): RetailCheckoutResult {
        require(cart.isNotEmpty()) { "Cart is empty." }
        require(payments.isNotEmpty()) { "At least one payment is required." }
        return client.postgrest.rpc(
            function = "storepos_retail_action",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_action", "checkout")
                put("p_data", buildJsonObject {
                    put("client_key", clientKey)
                    put("items", cartItems(cart))
                    put("discount", discount)
                    put("charges", chargeItems(charges))
                    put("payments", paymentItems(payments))
                    customerId?.let { put("customer_id", it) }
                    managerPin?.trim()?.takeIf { it.isNotBlank() }?.let { put("manager_pin", it) }
                    dueDate?.trim()?.takeIf { it.isNotBlank() }?.let { put("due_date", it) }
                    orderId?.let { put("order_id", it) }
                    exchangeSaleId?.let { put("exchange_sale_id", it) }
                })
            }
        ).decodeAs()
    }

    suspend fun configureProduct(
        shopId: String,
        productId: String,
        parentId: String?,
        multiplier: Double,
        wholesalePrice: Double?,
        wholesaleMin: Double,
        variantGroup: String?,
        variantName: String?,
        unit: String,
        isWeighed: Boolean,
        batchTracked: Boolean,
        serialTracked: Boolean
    ) = action(shopId, "product", buildJsonObject {
        put("product_id", productId)
        parentId?.let { put("parent_id", it) }
        put("multiplier", multiplier)
        wholesalePrice?.let { put("wholesale_price", it) }
        put("wholesale_min", wholesaleMin)
        variantGroup?.trim()?.takeIf { it.isNotBlank() }?.let { put("variant_group", it) }
        variantName?.trim()?.takeIf { it.isNotBlank() }?.let { put("variant_name", it) }
        put("unit", unit.trim().ifBlank { "pc" })
        put("is_weighed", isWeighed)
        put("batch_tracked", batchTracked)
        put("serial_tracked", serialTracked)
    })

    suspend fun importProducts(shopId: String, rows: List<RetailImportRow>) =
        action(shopId, "import", buildJsonObject {
            put("rows", buildJsonArray {
                rows.take(1000).forEach { row ->
                    add(buildJsonObject {
                        put("sku", row.sku.trim())
                        put("name", row.name.trim())
                        row.barcode?.trim()?.takeIf { it.isNotBlank() }?.let { put("barcode", it) }
                        put("cost_price", row.costPrice)
                        put("selling_price", row.sellingPrice)
                        put("unit", row.unit.trim().ifBlank { "pc" })
                        put("reorder_level", row.reorderLevel)
                        put("opening_stock", row.openingStock)
                    })
                }
            })
        })

    fun parseCsv(text: String): List<RetailImportRow> {
        val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        require(lines.size >= 2) { "Paste a CSV header and at least one product row." }
        val headers = parseCsvLine(lines.first()).map { it.trim().lowercase() }
        fun index(name: String) = headers.indexOf(name)
        require(index("sku") >= 0 && index("name") >= 0) { "CSV must include sku and name columns." }

        fun cell(values: List<String>, name: String): String =
            index(name).takeIf { it >= 0 }?.let { values.getOrNull(it).orEmpty().trim() }.orEmpty()

        return lines.drop(1).take(1000).mapIndexed { rowIndex, line ->
            val values = parseCsvLine(line)
            val sku = cell(values, "sku")
            val name = cell(values, "name")
            require(sku.isNotBlank() && name.isNotBlank()) { "CSV row " + (rowIndex + 2) + ": SKU and name are required." }
            RetailImportRow(
                sku = sku,
                name = name,
                barcode = cell(values, "barcode").ifBlank { null },
                costPrice = cell(values, "cost_price").toDoubleOrNull() ?: 0.0,
                sellingPrice = cell(values, "selling_price").toDoubleOrNull() ?: 0.0,
                unit = cell(values, "unit").ifBlank { "pc" },
                reorderLevel = cell(values, "reorder_level").toDoubleOrNull() ?: 5.0,
                openingStock = cell(values, "opening_stock").toDoubleOrNull() ?: 0.0
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        out += current.toString()
        return out
    }

    suspend fun receiveBatch(
        shopId: String,
        productId: String,
        batchNumber: String,
        expiresOn: String?,
        quantity: Double,
        receiveStock: Boolean
    ) = action(shopId, "batch", buildJsonObject {
        put("product_id", productId)
        put("batch_number", batchNumber.trim())
        expiresOn?.trim()?.takeIf { it.isNotBlank() }?.let { put("expires_on", it) }
        put("quantity", quantity)
        put("receive_stock", receiveStock)
    })

    suspend fun registerSerials(shopId: String, productId: String, serials: List<String>) =
        action(shopId, "serial", buildJsonObject {
            put("product_id", productId)
            put("serials", buildJsonArray {
                serials.map(String::trim).filter(String::isNotBlank).distinct().forEach { add(it) }
            })
        })

    suspend fun schedulePrice(shopId: String, productId: String, price: Double, effectiveAt: String) =
        action(shopId, "schedule", buildJsonObject {
            put("product_id", productId)
            put("price", price)
            put("effective_at", effectiveAt)
        })

    suspend fun createPromo(
        shopId: String,
        name: String,
        kind: String,
        items: List<RetailPromoItem>,
        discountPercent: Double,
        buyQty: Double,
        freeQty: Double,
        bundlePrice: Double,
        startsAt: String,
        endsAt: String
    ) = action(shopId, "promo", buildJsonObject {
        put("name", name.trim())
        put("kind", kind)
        put("items", buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    put("product_id", item.productId)
                    put("quantity", item.quantity)
                })
            }
        })
        put("discount_percent", discountPercent)
        put("buy_qty", buyQty)
        put("free_qty", freeQty)
        put("bundle_price", bundlePrice)
        put("starts_at", startsAt)
        put("ends_at", endsAt)
    })

    suspend fun reserveOrder(
        shopId: String,
        customerId: String,
        cart: List<CartLine>,
        deposit: Double,
        depositMethod: String,
        dueAt: String?,
        notes: String?
    ) = action(shopId, "reserve", buildJsonObject {
        put("customer_id", customerId)
        put("items", cartItems(cart))
        put("deposit", deposit)
        put("deposit_method", depositMethod)
        dueAt?.trim()?.takeIf { it.isNotBlank() }?.let { put("due_at", it) }
        notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
    })

    suspend fun cancelOrder(shopId: String, orderId: String, refundConfirmed: Boolean) =
        action(shopId, "cancel_order", buildJsonObject {
            put("id", orderId)
            put("refund_confirmed", refundConfirmed)
        })

    suspend fun recordStockLoss(
        shopId: String,
        productId: String,
        quantity: Double,
        reason: String,
        notes: String?,
        batchId: String? = null,
        serials: List<String> = emptyList()
    ) = action(shopId, "stock_loss", buildJsonObject {
        put("product_id", productId)
        put("quantity", quantity)
        put("reason", reason)
        notes?.trim()?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
        batchId?.let { put("batch_id", it) }
        put("serials", buildJsonArray { serials.forEach { add(it) } })
    })

    suspend fun supplierReturn(
        shopId: String,
        supplierId: String,
        productId: String,
        quantity: Double,
        creditAmount: Double,
        reason: String,
        batchId: String? = null,
        serials: List<String> = emptyList()
    ) = action(shopId, "supplier_return", buildJsonObject {
        put("supplier_id", supplierId)
        put("product_id", productId)
        put("quantity", quantity)
        put("credit_amount", creditAmount)
        put("reason", reason.trim())
        batchId?.let { put("batch_id", it) }
        put("serials", buildJsonArray { serials.forEach { add(it) } })
    })

    suspend fun markSupplierReturnCredited(shopId: String, id: String) =
        action(shopId, "supplier_credit", buildJsonObject { put("id", id) })

    suspend fun updateCreditTerms(
        shopId: String,
        customerId: String,
        creditLimit: Double,
        receivableId: String? = null,
        dueDate: String? = null
    ) = action(shopId, "credit_terms", buildJsonObject {
        put("customer_id", customerId)
        put("credit_limit", creditLimit)
        receivableId?.let { put("receivable_id", it) }
        dueDate?.trim()?.takeIf { it.isNotBlank() }?.let { put("due_date", it) }
    })

    suspend fun saveChecklist(shopId: String, kind: String, checks: Map<String, Boolean>) =
        action(shopId, "checklist", buildJsonObject {
            put("kind", kind)
            put("checks", buildJsonObject {
                checks.forEach { (key, value) -> put(key, value) }
            })
        })

    suspend fun setFavorite(shopId: String, productId: String, position: Int, remove: Boolean = false) =
        action(shopId, "favorite", buildJsonObject {
            put("product_id", productId)
            put("position", position)
            put("remove", remove)
        })

    suspend fun batches(shopId: String): List<RetailBatch> =
        client.from("retail_batches").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailBatch>().sortedWith(compareBy<RetailBatch> { it.expiresOn ?: "9999-12-31" }.thenBy { it.batchNumber })

    suspend fun serials(shopId: String): List<RetailSerial> =
        client.from("retail_serials").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailSerial>().sortedByDescending { it.createdAt ?: "" }

    suspend fun priceHistory(shopId: String): List<RetailPriceHistory> =
        client.from("retail_price_history").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailPriceHistory>().sortedByDescending { it.createdAt }

    suspend fun priceSchedules(shopId: String): List<RetailPriceSchedule> =
        client.from("retail_price_schedule").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailPriceSchedule>().sortedByDescending { it.effectiveAt }

    suspend fun promos(shopId: String): List<RetailPromo> =
        client.from("retail_promos").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailPromo>().sortedByDescending { it.startsAt }

    suspend fun orders(shopId: String): List<RetailOrder> =
        client.from("retail_orders").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailOrder>().sortedByDescending { it.createdAt }

    suspend fun supplierReturns(shopId: String): List<RetailSupplierReturn> =
        client.from("retail_supplier_returns").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailSupplierReturn>().sortedByDescending { it.createdAt }

    suspend fun checklists(shopId: String): List<RetailChecklist> =
        client.from("retail_checklists").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailChecklist>().sortedByDescending { it.createdAt }

    suspend fun favorites(shopId: String): List<RetailFavorite> {
        val userId = StoreRepository.currentUserId() ?: return emptyList()
        return client.from("retail_favorites").select {
            filter {
                eq("shop_id", shopId)
                eq("user_id", userId)
            }
        }.decodeList<RetailFavorite>().sortedBy { it.position }
    }

    suspend fun saleDetails(shopId: String): List<RetailSaleDetail> =
        client.from("retail_sale_details").select { filter { eq("shop_id", shopId) } }
            .decodeList<RetailSaleDetail>().sortedByDescending { it.createdAt }

    suspend fun insights(shopId: String, days: Int = 30): RetailInsights =
        client.postgrest.rpc(
            function = "storepos_retail_insights",
            parameters = buildJsonObject {
                put("p_shop_id", shopId)
                put("p_days", days.coerceIn(1, 365))
            }
        ).decodeAs()

    suspend fun digitalReceipt(token: String): JsonObject =
        client.postgrest.rpc(
            function = "storepos_receipt",
            parameters = buildJsonObject { put("p_token", token) }
        ).decodeAs()
}
