package com.storepos.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HybridConnectivityTest {
    @Test fun networkIOExceptionAllowsOfflineFallback() {
        assertTrue(HybridConnectivity.isConnectionFailure(IOException("No route to host")))
    }

    @Test fun socketTimeoutCauseAllowsOfflineFallback() {
        val error = IllegalStateException("request failed",
            java.net.SocketTimeoutException("timeout"))
        assertTrue(HybridConnectivity.isConnectionFailure(error))
    }

    @Test fun serverValidationMustNotCreateOfflineSale() {
        assertFalse(HybridConnectivity.isConnectionFailure(
            IllegalStateException("Insufficient stock for item")))
    }

    @Test fun expiredLicenseMustNeverTriggerOfflineFallback() {
        assertFalse(HybridConnectivity.isConnectionFailure(
            IllegalStateException("Your StorePOS license has expired")))
    }
}
