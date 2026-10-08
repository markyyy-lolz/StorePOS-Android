package com.storepos.app.printing

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Decodes the very same ESC/POS byte stream sent to the physical receipt printer.
 * The PDF exporter must never maintain a second receipt template.
 */
sealed interface ThermalReceiptElement {
    data class Text(val line: String) : ThermalReceiptElement
    data class QrCode(val content: String) : ThermalReceiptElement
}

object ThermalReceiptDecoder {
    private val printerCharset: Charset = Charset.forName("CP437")

    fun decode(payload: ByteArray): List<ThermalReceiptElement> {
        val result = mutableListOf<ThermalReceiptElement>()
        val text = ByteArrayOutputStream()
        var qrContent: String? = null
        var i = 0

        fun byte(at: Int): Int = payload[at].toInt() and 0xff
        fun line() {
            result += ThermalReceiptElement.Text(String(text.toByteArray(), printerCharset))
            text.reset()
        }
        fun flushPartialLine() {
            if (text.size() > 0) line()
        }

        while (i < payload.size) {
            val b = byte(i)
            when {
                b == 0x0A -> {
                    line()
                    i++
                }
                b == 0x0D -> i++
                b == 0x1B -> {
                    require(i + 1 < payload.size) { "Incomplete ESC/POS escape sequence." }
                    when (byte(i + 1)) {
                        0x40 -> i += 2 // Initialize printer
                        0x61 -> {
                            require(i + 2 < payload.size) { "Incomplete alignment command." }
                            i += 3
                        }
                        0x70 -> {
                            require(i + 4 < payload.size) { "Incomplete cash drawer command." }
                            i += 5
                        }
                        else -> error("Unsupported ESC/POS command; refusing to export an incorrect PDF.")
                    }
                }
                b == 0x1D -> {
                    require(i + 1 < payload.size) { "Incomplete ESC/POS graphics command." }
                    when (byte(i + 1)) {
                        0x56 -> {
                            require(i + 2 < payload.size) { "Incomplete paper cut command." }
                            i += 3
                        }
                        0x28 -> {
                            require(i + 4 < payload.size && byte(i + 2) == 0x6B) {
                                "Unsupported ESC/POS graphics command."
                            }
                            val commandLength = byte(i + 3) + (byte(i + 4) shl 8)
                            require(i + 5 + commandLength <= payload.size) {
                                "Truncated ESC/POS QR command."
                            }
                            val start = i + 5
                            if (commandLength >= 3 && byte(start) == 0x31) {
                                when (byte(start + 1)) {
                                    0x50 -> if (byte(start + 2) == 0x30) {
                                        qrContent = String(
                                            payload.copyOfRange(start + 3, start + commandLength),
                                            Charsets.UTF_8
                                        )
                                    }
                                    0x51 -> if (byte(start + 2) == 0x30) {
                                        flushPartialLine()
                                        qrContent?.let { result += ThermalReceiptElement.QrCode(it) }
                                    }
                                }
                            }
                            i += 5 + commandLength
                        }
                        else -> error("Unsupported ESC/POS command; refusing to export an incorrect PDF.")
                    }
                }
                else -> {
                    text.write(b)
                    i++
                }
            }
        }
        flushPartialLine()
        return result
    }
}
