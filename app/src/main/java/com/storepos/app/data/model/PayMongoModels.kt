package com.storepos.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PayMongoIntegration(
    @SerialName("shop_id") val shopId: String,
    val enabled: Boolean = false,
    val mode: String = "test",
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("public_key_last4") val publicKeyLast4: String? = null,
    @SerialName("secret_key_last4") val secretKeyLast4: String? = null,
    @SerialName("webhook_secret_last4") val webhookSecretLast4: String? = null,
    @SerialName("webhook_id") val webhookId: String? = null,
    @SerialName("webhook_status") val webhookStatus: String? = null,
    @SerialName("payment_method_types") val paymentMethodTypes: List<String> = emptyList(),
    @SerialName("connected_at") val connectedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PayMongoCheckoutEnvelope(
    val session: PayMongoCheckoutSession
)

@Serializable
data class PayMongoCheckoutSession(
    val id: String,
    @SerialName("shop_id") val shopId: String,
    @SerialName("client_reference") val clientReference: String,
    val amount: Double,
    val currency: String = "PHP",
    val status: String,
    @SerialName("flow_type") val flowType: String = "hosted",
    @SerialName("paymongo_checkout_session_id") val payMongoCheckoutSessionId: String? = null,
    @SerialName("paymongo_payment_intent_id") val payMongoPaymentIntentId: String? = null,
    @SerialName("paymongo_payment_method_id") val payMongoPaymentMethodId: String? = null,
    @SerialName("checkout_url") val checkoutUrl: String? = null,
    @SerialName("qr_image_url") val qrImageUrl: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("payment_id") val paymentId: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("paid_amount") val paidAmount: Double? = null,
    @SerialName("fee_amount") val feeAmount: Double? = null,
    @SerialName("net_amount") val netAmount: Double? = null,
    val livemode: Boolean = false,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
