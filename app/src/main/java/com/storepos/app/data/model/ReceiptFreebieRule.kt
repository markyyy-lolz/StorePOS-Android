package com.storepos.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shop-scoped receipt reward; informational until verified/redeemed by staff. */
@Serializable
data class ReceiptFreebieRule(
    @SerialName("shop_id") val shopId: String,
    val enabled: Boolean = false,
    @SerialName("minimum_total") val minimumTotal: Double = 1500.0,
    @SerialName("gift_name") val giftName: String = "",
    val quantity: Int = 1
) {
    fun qualifies(finalTotal: Double): Boolean =
        enabled && minimumTotal > 0.0 && minimumTotal.isFinite() &&
            finalTotal.isFinite() && finalTotal + 0.000001 >= minimumTotal &&
            giftName.isNotBlank() && giftName.length <= 80 && quantity in 1..20
}
