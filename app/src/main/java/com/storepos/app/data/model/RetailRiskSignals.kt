package com.storepos.app.data.model

import kotlin.math.abs

/** Explainable REVIEW signals only, not determinations of fraud. */
data class RetailRiskSignals(
    val repeatedReceiptSales: Int,
    val deniedApprovals: Int,
    val paymentVarianceCount: Int
) {
    val total: Int get() = repeatedReceiptSales + deniedApprovals + paymentVarianceCount
}

object RetailRiskReview {
    fun calculate(
        shopId: String,
        reprints: List<RetailReceiptReprint>,
        approvals: List<RetailManagerApproval>,
        reconciliations: List<RetailPaymentReconciliation>
    ): RetailRiskSignals {
        val repeats = reprints.filter { it.shopId == shopId }
            .groupBy { it.saleId }
            // Two or more audited reprints warrant a human review. The
            // original receipt is not counted as a reprint.
            .count { it.value.size >= 2 }
        val denied = approvals.count {
            it.shopId == shopId && it.status.lowercase() in setOf("rejected", "denied")
        }
        val variances = reconciliations.count {
            it.shopId == shopId && it.variance.isFinite() && abs(it.variance) >= 1.0
        }
        return RetailRiskSignals(repeats, denied, variances)
    }
}
