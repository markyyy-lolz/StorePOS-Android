package com.storepos.app.printing

import com.storepos.app.data.model.ReceiptFreebieRule
import com.storepos.app.data.model.Sale
import java.nio.charset.Charset

/**
 * A physically separate, full-cut voucher appended AFTER the original sale
 * receipt has already been printed and cut. It is a claim notice, NOT
 * automatic redemption or proof of gift handover.
 */
internal object FreeItemClaimSlip {
    fun render(
        shopName: String,
        sale: Sale,
        rule: ReceiptFreebieRule,
        paperWidth: Int,
        footerFeedLines: Int
    ): ByteArray {
        require(paperWidth == 58 || paperWidth == 80)
        require(rule.shopId == sale.shopId && rule.qualifies(sale.totalAmount))
        val columns = if (paperWidth == 58) 32 else 48
        fun safe(text: String): String =
            text.map { if (Character.isISOControl(it)) ' ' else it }.joinToString("").trim()
        fun center(line: String): String {
            val value = line.take(columns)
            return " ".repeat(((columns - value.length) / 2).coerceAtLeast(0)) + value
        }
        fun wrap(prefix: String, value: String): List<String> =
            (prefix + safe(value)).chunked(columns)

        val lines = buildList {
            add(center(safe(shopName).take(columns)))
            add("-".repeat(columns))
            add(center("FREE ITEM CLAIM SLIP"))
            add(center("FREE ITEM REWARD"))
            add(center("YOU QUALIFY!"))
            add("-".repeat(columns))
            addAll(wrap("Gift: ", "${rule.quantity} x ${rule.giftName}"))
            add(center("PRICE: PHP 0.00"))
            add("-".repeat(columns))
            addAll(wrap("Sale Ref: ", sale.saleNumber))
            if (sale.status == "pending_sync") {
                add(center("OFFLINE - PENDING SYNC"))
                add(center("Provisional sale reference"))
            }
            sale.createdAt?.let { addAll(wrap("Date: ", it.replace("T", " ").take(19))) }
            add("-".repeat(columns))
            add(center("Present this slip at counter"))
            add(center("Staff: check before giving gift"))
            add(center("One gift per verified sale"))
            add(center("Reprints are not new rewards"))
            add(center("NOT AN OFFICIAL TAX RECEIPT"))
            add("")
        }

        val init = byteArrayOf(0x1B, 0x40)
        val body = lines.joinToString("\n", postfix = "\n")
            .toByteArray(Charset.forName("CP437"))
        return init + body + ReceiptCutSettings.extraFeedAndFullCut(footerFeedLines)
    }
}
