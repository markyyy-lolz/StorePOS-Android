package com.storepos.app.ui.screens

/**
 * Sanitizes a keyboard-wedge / Bluetooth HID barcode before it reaches the
 * POS search. Many scanners append ENTER, CRLF or TAB as a suffix.
 */
internal data class HidScannerText(
    val searchText: String,
    val completedCode: String?
)

internal fun parseHidScannerText(raw: String): HidScannerText {
    val terminatorIndex = raw.indexOfFirst { it == '\r' || it == '\n' || it == '\t' }
    val value = if (terminatorIndex >= 0) raw.substring(0, terminatorIndex) else raw
    // Remove control keys that are not valid printable barcode characters;
    // retain spaces (some product names/SKU labels contain them).
    val clean = value.filter { !it.isISOControl() }
    return HidScannerText(
        searchText = clean,
        completedCode = if (terminatorIndex >= 0) clean.trim().takeIf { it.isNotBlank() } else null
    )
}

/** Scanners can send an empty suffix after a successful auto-submit. */
internal fun scannedCodeOrNull(currentInput: String): String? =
    currentInput.trim().takeIf { it.isNotBlank() }
