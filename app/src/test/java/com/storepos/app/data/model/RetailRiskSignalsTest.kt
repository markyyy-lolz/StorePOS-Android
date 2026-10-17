package com.storepos.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RetailRiskSignalsTest {
    @Test fun repeatedPrintsAndVariancesAreSignalsNotCrossShopLeak() {
        val shop="storepos-only"
        val prints=listOf(
            RetailReceiptReprint("r1",shop,"s1","cashier",1,"asked","2026-10-17"),
            RetailReceiptReprint("r2",shop,"s1","cashier",2,"asked again","2026-10-17"),
            RetailReceiptReprint("r3","motopos-other","s2","other",1,"r","2026-10-17"),
            RetailReceiptReprint("r4","motopos-other","s2","other",2,"r","2026-10-17")
        )
        val approvals=listOf(
            RetailManagerApproval(id="a1",shopId=shop,requestedBy="c",approvalType="discount",reason="Override",status="rejected",expiresAt="2026-10-18",createdAt="2026-10-17"),
            RetailManagerApproval(id="a2",shopId="motopos-other",requestedBy="c",approvalType="discount",reason="Other",status="denied",expiresAt="2026-10-18",createdAt="2026-10-17")
        )
        val reconciliation=listOf(
            RetailPaymentReconciliation("w1",shop,"2026-10-17","gcash",0.0,2.0,2.0,createdBy="c",createdAt="2026-10-17",updatedAt="2026-10-17"),
            RetailPaymentReconciliation("w2",shop,"2026-10-17","maya",0.0,0.3,0.3,createdBy="c",createdAt="2026-10-17",updatedAt="2026-10-17")
        )
        val result=RetailRiskReview.calculate(shop,prints,approvals,reconciliation)
        assertEquals(1,result.repeatedReceiptSales)
        assertEquals(1,result.deniedApprovals)
        assertEquals(1,result.paymentVarianceCount)
        assertEquals(3,result.total)
    }
}
