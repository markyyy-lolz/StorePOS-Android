package com.storepos.app.ui

import com.storepos.app.data.AppSessionRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSessionRegressionTest {
    @Test fun scanningReturnKeepsPosDestination() {
        assertEquals(AppPage.POS, restoredAppPage("POS"))
        assertEquals(AppPage.Inventory, restoredAppPage("Inventory"))
    }

    @Test fun unexpectedOrOldDestinationsAreSafe() {
        assertEquals(AppPage.Dashboard, restoredAppPage(null))
        assertEquals(AppPage.Dashboard, restoredAppPage("DeletedPage"))
    }

    @Test fun signInPreferenceControlsColdStartOnly() {
        assertFalse(AppSessionRetention.mustClearAtColdStart(true))
        assertTrue(AppSessionRetention.mustClearAtColdStart(false))
    }
}
