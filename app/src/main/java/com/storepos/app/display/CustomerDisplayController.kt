package com.storepos.app.display

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.storepos.app.data.model.CartLine
import java.util.Locale

class CustomerDisplayController(private val context: Context) {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var presentation: Presentation? = null
    private var activeDisplayId: Int? = null

    fun hasExternalDisplay(): Boolean =
        displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()

    fun show(shopName: String, cart: List<CartLine>): Boolean {
        val display = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull() ?: run {
            dismiss()
            return false
        }

        if (presentation == null || activeDisplayId != display.displayId) {
            dismiss()
            presentation = Presentation(context, display).also {
                activeDisplayId = display.displayId
                it.show()
            }
        }

        presentation?.setContentView(buildContent(shopName, cart))
        return true
    }

    fun dismiss() {
        runCatching { presentation?.dismiss() }
        presentation = null
        activeDisplayId = null
    }

    private fun buildContent(shopName: String, cart: List<CartLine>): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(42, 36, 42, 36)
            setBackgroundColor(Color.rgb(247, 250, 253))
        }

        root.addView(text(shopName, 30f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(19, 55, 96))
        }, matchWidth())

        root.addView(text("Customer Display", 16f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }, matchWidth())

        val scroll = ScrollView(context)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 24)
        }

        if (cart.isEmpty()) {
            list.addView(text("Ready for your order", 24f, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, 80, 0, 80)
            }, matchWidth())
        } else {
            cart.forEach { line ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 14, 8, 14)
                }
                row.addView(
                    text(
                        line.product.name + "\n" + qty(line.quantity) + " × " + money(line.unitPrice),
                        18f,
                        false
                    ),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(text(money(line.lineTotal), 20f, true))
                list.addView(row, matchWidth())
            }
        }

        scroll.addView(list, matchWidth())
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val total = cart.sumOf { it.lineTotal }
        root.addView(text("TOTAL  " + money(total), 34f, true).apply {
            gravity = Gravity.END
            setTextColor(Color.rgb(11, 79, 216))
            setPadding(0, 18, 0, 8)
        }, matchWidth())

        root.addView(text("Thank you for shopping with us.", 15f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }, matchWidth())

        return root
    }

    private fun text(value: String, size: Float, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(25, 34, 50))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun money(value: Double): String =
        "₱" + String.format(Locale.US, "%,.2f", value)

    private fun qty(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
}
