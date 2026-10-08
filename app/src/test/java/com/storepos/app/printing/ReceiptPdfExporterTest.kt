package com.storepos.app.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptPdfExporterTest {
    @Test fun receiptFileNameNeverContainsFilesystemSeparators() {
        assertEquals(
            "StorePOS-Receipt-SP_2026_0042.pdf",
            ReceiptPdfExporter.fileName("SP/2026:0042")
        )
    }

    @Test fun receiptFileNameIsStableForReprints() {
        val number = "SALE-0089"
        assertEquals(ReceiptPdfExporter.fileName(number), ReceiptPdfExporter.fileName(number))
        assertEquals("StorePOS-Receipt-SALE-0089.pdf", ReceiptPdfExporter.fileName(number))
    }
}
