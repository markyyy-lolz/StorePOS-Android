package com.storepos.app.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalReceiptDecoderTest {
    @Test
    fun ignoresOnlyNonvisualPrinterCommandsAndPreservesPrintedText() {
        val payload = byteArrayOf(0x1B, 0x40, 0x1B, 0x70, 0x00, 0x19, 0x7F.toByte()) +
            "        StorePOS\n".toByteArray() +
            "Subtotal             PHP 75.00\n".toByteArray() +
            "TOTAL                PHP 75.00\n\n".toByteArray() +
            byteArrayOf(0x1D, 0x56, 0x00)
        val decoded = ThermalReceiptDecoder.decode(payload)
        assertEquals(
            listOf(
                ThermalReceiptElement.Text("        StorePOS"),
                ThermalReceiptElement.Text("Subtotal             PHP 75.00"),
                ThermalReceiptElement.Text("TOTAL                PHP 75.00"),
                ThermalReceiptElement.Text("")
            ), decoded
        )
    }

    @Test
    fun preservesDigitalReceiptQrPayloadAndItsExactSectionOrder() {
        val url = "https://example.org/receipt/id"
        val bytes = url.toByteArray(Charsets.UTF_8)
        val n = bytes.size + 3
        val storeCommand = byteArrayOf(
            0x1D, 0x28, 0x6B, (n and 255).toByte(), ((n shr 8) and 255).toByte(),
            0x31, 0x50, 0x30
        ) + bytes
        val printCommand = byteArrayOf(
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30
        )
        val commands = byteArrayOf(0x1B, 0x40) +
            "PAYMENT DETAILS\n".toByteArray() +
            byteArrayOf(0x1B, 0x61, 0x01) +
            storeCommand + printCommand +
            byteArrayOf(0x0A, 0x1B, 0x61, 0x00) +
            "Available for 3 days only.\n".toByteArray() +
            byteArrayOf(0x1D, 0x56, 0x00)
        val blocks = ThermalReceiptDecoder.decode(commands)
        assertEquals(ThermalReceiptElement.Text("PAYMENT DETAILS"), blocks[0])
        assertEquals(ThermalReceiptElement.QrCode(url), blocks[1])
        assertEquals(ThermalReceiptElement.Text(""), blocks[2])
        assertEquals(ThermalReceiptElement.Text("Available for 3 days only."), blocks[3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTruncatedQrCommandRatherThanRenderingIncompleteReceipt() {
        ThermalReceiptDecoder.decode(byteArrayOf(0x1D, 0x28, 0x6B, 0x50, 0x00, 0x31))
    }

    @Test
    fun passesThroughCp437ReceiptGlyphsAsTheyWouldPrint() {
        val cp437 = java.nio.charset.Charset.forName("CP437")
        val phrase = "Store POS 2026"
        val bytes = byteArrayOf(0x1B, 0x40) + phrase.toByteArray(cp437) + byteArrayOf(0x0A)
        assertTrue(ThermalReceiptDecoder.decode(bytes).contains(ThermalReceiptElement.Text(phrase)))
    }
}
