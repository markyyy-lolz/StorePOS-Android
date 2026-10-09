package com.storepos.app.printing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPrinterConnectionPolicyTest {
    @Test fun legacyBluetoothMayCancelDiscovery() {
        assertTrue(shouldCancelLegacyDiscovery(26))
        assertTrue(shouldCancelLegacyDiscovery(30))
    }

    @Test fun pairedPrintersOnAndroid12AndLaterDoNotNeedScanPermission() {
        assertFalse(shouldCancelLegacyDiscovery(31))
        assertFalse(shouldCancelLegacyDiscovery(33))
        assertFalse(shouldCancelLegacyDiscovery(35))
        assertFalse(shouldCancelLegacyDiscovery(36))
    }
}
