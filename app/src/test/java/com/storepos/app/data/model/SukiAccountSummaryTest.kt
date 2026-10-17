package com.storepos.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SukiAccountSummaryTest {
    private val customer = Customer(
        id = "31e52000-5b71-4db3-829a-aabbccddeeff",
        shopId = "store-shop", name = "Sample suki",
        loyaltyPoints = 200, creditLimit = 1500.0, storeCreditBalance = 100.0
    )
    @Test fun memberIdStableAndShopCreditNotCountedAsUtang() {
        assertEquals("SUKI-31E520005B71", SukiAccountSummary.membershipReference(customer))
        val due = listOf(CustomerReceivable(
            id = "r1", shopId = "store-shop", customerId = customer.id,
            saleId = "sale", originalAmount = 250.0,
            balance = 175.0, status = "partial", createdAt = "2026-10-17"
        ))
        assertEquals(175.0, SukiAccountSummary.outstanding(customer, due), 0.001)
        assertEquals(1325.0, SukiAccountSummary.availableCredit(customer, due), 0.001)
        // Separate prepaid wallet has no effect on the utang calculation.
        assertEquals(175.0, SukiAccountSummary.outstanding(customer.copy(storeCreditBalance=10000.0), due), 0.001)
    }
    @Test fun neverMixesOtherShopsOrCustomersAndClampsNegativeBalances() {
        val due = listOf(
            CustomerReceivable("r1","another-shop",customer.id,"s1",500.0,500.0,status="open",createdAt="2026-10-17"),
            CustomerReceivable("r2","store-shop","another-customer","s2",900.0,900.0,status="open",createdAt="2026-10-17"),
            CustomerReceivable("r3","store-shop",customer.id,"s3",500.0,-5.0,status="overpaid",createdAt="2026-10-17")
        )
        assertEquals(0.0, SukiAccountSummary.outstanding(customer, due),0.001)
        assertEquals(1500.0, SukiAccountSummary.availableCredit(customer,due),0.001)
    }
}
