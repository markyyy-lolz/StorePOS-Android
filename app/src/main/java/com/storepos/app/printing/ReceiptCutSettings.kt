package com.storepos.app.printing

import android.content.Context

/**
 * Extra blank line feeds after the final printable receipt/footer line and
 * before the existing ESC/POS GS V 0 full-cut command.
 *
 * Keep the cutter mode unchanged for compatibility. This preference is local
 * to the cashier/host initiating the print payload; queued jobs contain the
 * already formatted bytes so retries cannot silently change the paper cut.
 */
object ReceiptCutSettings {
    const val PREF_KEY = "receipt_footer_to_cut_lines"
    const val DEFAULT_LINES = 2
    const val MAX_LINES = 8

    fun normalize(lines: Int): Int = lines.coerceIn(0, MAX_LINES)

    fun get(context: Context): Int {
        val prefs = context.getSharedPreferences("motopos_settings", Context.MODE_PRIVATE)
        return normalize(prefs.getInt(PREF_KEY, DEFAULT_LINES))
    }

    /** The printable footer ends with its own newline; these lines are *additional*. */
    fun extraFeedAndFullCut(lines: Int): ByteArray =
        ByteArray(normalize(lines)) { 0x0A } + byteArrayOf(0x1D, 0x56, 0x00)
}
