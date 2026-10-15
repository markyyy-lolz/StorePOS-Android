package com.storepos.app.printing

/**
 * POS software supplier contact, separate from the shop's editable receipt footer.
 * ASCII-only so 58mm/80mm ESC/POS printing and PDF-from-ESC/POS stay consistent.
 */
object PosSupplierFooter {
    const val NAME = "AZURATE SOFTWARE SOLUTION"
    const val ADDRESS = "PULO SAN ROQUE, PAOMBONG, BULACAN"
    const val PHONE = "09241134301"

    fun centeredLines(width: Int): List<String> {
        require(width >= 32) { "Unsupported receipt width" }
        val labels = if (width < ADDRESS.length) {
            listOf(
                "POS SYSTEM SUPPLIED",
                "& SUPPORTED BY",
                NAME,
                "PULO SAN ROQUE,",
                "PAOMBONG, BULACAN",
                "TECHNICAL SUPPORT / INQUIRIES",
                PHONE
            )
        } else {
            listOf(
                "POS SYSTEM SUPPLIED & SUPPORTED BY",
                NAME,
                ADDRESS,
                "TECHNICAL SUPPORT / INQUIRIES",
                PHONE
            )
        }
        // For narrow (58mm) paper the support heading is intentionally split too.
        return labels.flatMap { label ->
            if (label.length <= width) listOf(label)
            else if (label == "TECHNICAL SUPPORT / INQUIRIES") listOf("TECHNICAL SUPPORT", "/ INQUIRIES")
            else error("Supplier footer line exceeds receipt width")
        }.map { line ->
            " ".repeat(((width - line.length) / 2).coerceAtLeast(0)) + line
        }
    }
}
