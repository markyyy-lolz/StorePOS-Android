package com.storepos.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HidScannerInputTest {
    @Test fun enterSuffixSubmitsOnceWithoutControlCharacter() {
        assertEquals(HidScannerText("4800001234567", "4800001234567"), parseHidScannerText("4800001234567\r"))
    }

    @Test fun crLfScannerTerminatorIsHandled() {
        assertEquals(HidScannerText("0123456789012", "0123456789012"), parseHidScannerText("0123456789012\r\n"))
    }

    @Test fun tabSuffixSubmits() {
        assertEquals(HidScannerText("SKU-1000", "SKU-1000"), parseHidScannerText("SKU-1000\t"))
    }

    @Test fun noSuffixLeavesInputWaitingForNextCharacter() {
        assertEquals(HidScannerText("48000123", null), parseHidScannerText("48000123"))
    }

    @Test fun emptySuffixDoesNotGenerateEmptySaleLine() {
        assertEquals(HidScannerText("", null), parseHidScannerText("\n"))
        assertNull(scannedCodeOrNull(""))
        assertNull(scannedCodeOrNull(" \t "))
    }

    @Test fun barcodeAndSkuPunctuationStayIntact() {
        assertEquals(HidScannerText("ITEM-A/2 5", "ITEM-A/2 5"), parseHidScannerText("ITEM-A/2 5\n"))
    }

    @Test fun scannerControlCharacterIsRemoved() {
        assertEquals(HidScannerText("EAN13", null), parseHidScannerText("EA\u0000N13"))
    }

    @Test fun scanRepeatsAreNotDeduplicated() {
        assertEquals("4800001234567", scannedCodeOrNull("4800001234567"))
        assertEquals("4800001234567", scannedCodeOrNull("4800001234567"))
    }
}
