package com.storepos.app.data

import com.storepos.app.data.model.AuditLog
import com.storepos.app.data.model.SupplierPayable
import com.storepos.app.data.model.SupplierPayment
import com.storepos.app.data.model.MemberPermissionOverride
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AdminRepository {
    private val client get() = SupabaseProvider.client

    suspend fun memberPermissions(memberId: String): List<MemberPermissionOverride> =
        client.from("shop_member_permissions").select {
            filter { eq("member_id", memberId) }
        }.decodeList<MemberPermissionOverride>()

    suspend fun setMemberPermission(
        shopId: String,
        memberId: String,
        permissionKey: String,
        allowed: Boolean
    ) {
        client.from("shop_member_permissions").upsert(
            buildJsonObject {
                put("shop_id", shopId)
                put("member_id", memberId)
                put("permission_key", permissionKey)
                put("allowed", allowed)
            }
        ) {
            onConflict = "member_id,permission_key"
        }
    }

    suspend fun auditLogs(shopId: String): List<AuditLog> =
        client.from("audit_logs").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<AuditLog>().sortedByDescending { it.createdAt }

    suspend fun supplierPayables(shopId: String): List<SupplierPayable> =
        client.from("supplier_payables").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<SupplierPayable>()
            .sortedWith(
                compareBy<SupplierPayable> { it.status == "paid" || it.status == "cancelled" }
                    .thenBy { it.dueDate ?: "9999-12-31" }
                    .thenByDescending { it.createdAt }
            )

    suspend fun supplierPayments(shopId: String): List<SupplierPayment> =
        client.from("supplier_payments").select {
            filter { eq("shop_id", shopId) }
        }.decodeList<SupplierPayment>().sortedByDescending { it.paidAt }

    suspend fun paySupplier(
        payableId: String,
        amount: Double,
        method: String,
        referenceNumber: String?,
        notes: String?
    ): SupplierPayable =
        client.postgrest.rpc(
            function = "storepos_pay_supplier",
            parameters = buildJsonObject {
                put("p_payable_id", payableId)
                put("p_amount", amount)
                put("p_method", method.trim().lowercase())
                referenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let {
                    put("p_reference_number", it)
                }
                notes?.trim()?.takeIf { it.isNotBlank() }?.let {
                    put("p_notes", it)
                }
            }
        ).decodeSingle()
}
