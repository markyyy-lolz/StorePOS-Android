package com.storepos.app.display

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.storepos.app.data.model.CartLine
import com.storepos.app.data.model.Sale
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.Locale

class CustomerDisplayController(private val context: Context) {
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var presentation: Presentation? = null
    private var activeDisplayId: Int? = null

    fun hasExternalDisplay(): Boolean =
        displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()

    fun show(
        shopName: String,
        cart: List<CartLine>,
        completedSale: Sale? = null,
        paymentQrImage: String? = null,
        paymentAmount: Double? = null,
        receiptUrl: String? = null
    ): Boolean {
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

        // IMPORTANT: build the UI with the Presentation context so dp/sp scaling
        // follows the second display instead of the cashier display.
        val displayContext = presentation?.context ?: context
        presentation?.setContentView(
            when {
                !paymentQrImage.isNullOrBlank() && paymentAmount != null ->
                    buildPaymentQrContent(displayContext, shopName, paymentQrImage, paymentAmount)
                completedSale != null ->
                    buildCompletedContent(displayContext, shopName, completedSale, receiptUrl)
                else ->
                    buildContent(displayContext, shopName, cart)
            }
        )
        return true
    }

    fun dismiss() {
        runCatching { presentation?.dismiss() }
        presentation = null
        activeDisplayId = null
    }

    private fun buildPaymentQrContent(
        displayContext: Context,
        shopName: String,
        qrValue: String,
        amount: Double
    ): View {
        val root = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(displayContext, 28), dp(displayContext, 24), dp(displayContext, 28), dp(displayContext, 24))
            background = gradient(
                intArrayOf(
                    Color.rgb(7, 18, 34),
                    Color.rgb(10, 47, 83),
                    Color.rgb(13, 91, 151)
                ),
                radius = 0f
            )
        }

        val header = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text(displayContext, "STOREPOS", 13f, true).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 12), dp(displayContext, 7), dp(displayContext, 12), dp(displayContext, 7))
            background = rounded(Color.rgb(20, 118, 255), 999f)
        })
        header.addView(text(displayContext, shopName, 24f, true).apply {
            setTextColor(Color.WHITE)
            setPadding(dp(displayContext, 14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text(displayContext, "●  WAITING FOR PAYMENT", 11f, true).apply {
            setTextColor(Color.rgb(84, 230, 161))
        })
        root.addView(header, matchWidth())
        root.addView(space(displayContext, 18))

        val body = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val qrCard = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 24), dp(displayContext, 22), dp(displayContext, 24), dp(displayContext, 22))
            background = rounded(Color.WHITE, 26f)
        }
        qrCard.addView(text(displayContext, "Scan to pay", 25f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(19, 34, 55))
        }, matchWidth())
        qrCard.addView(space(displayContext, 12))

        val qrBitmap = decodeBase64Bitmap(qrValue)
        if (qrBitmap != null) {
            qrCard.addView(ImageView(displayContext).apply {
                setImageBitmap(qrBitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }, LinearLayout.LayoutParams(dp(displayContext, 280), dp(displayContext, 280)))
        } else {
            qrCard.addView(text(displayContext, "QR image unavailable", 15f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(180, 45, 45))
            }, matchWidth())
        }

        qrCard.addView(space(displayContext, 10))
        qrCard.addView(text(displayContext, "GCash • Maya • QR Ph banking apps", 13f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 105, 125))
        }, matchWidth())

        body.addView(
            qrCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.25f).apply {
                marginEnd = dp(displayContext, 16)
            }
        )

        val summary = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 26), dp(displayContext, 28), dp(displayContext, 26), dp(displayContext, 28))
            background = rounded(Color.rgb(15, 84, 163), 26f)
        }
        summary.addView(text(displayContext, "AMOUNT DUE", 12f, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            setTextColor(Color.rgb(192, 221, 255))
        }, matchWidth())
        summary.addView(space(displayContext, 12))
        summary.addView(text(displayContext, money(amount), 42f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, matchWidth())
        summary.addView(space(displayContext, 20))
        summary.addView(text(displayContext, "Please scan the QR code on this screen.", 15f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, matchWidth())
        summary.addView(space(displayContext, 8))
        summary.addView(text(displayContext, "StorePOS will confirm the payment automatically.", 12.5f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(215, 231, 249))
        }, matchWidth())

        body.addView(summary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.75f))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildCompletedContent(
        displayContext: Context,
        shopName: String,
        sale: Sale,
        receiptUrl: String?
    ): View {
        val root = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(displayContext, 28), dp(displayContext, 24), dp(displayContext, 28), dp(displayContext, 24))
            background = gradient(
                intArrayOf(
                    Color.rgb(7, 18, 34),
                    Color.rgb(10, 47, 83),
                    Color.rgb(13, 91, 151)
                ),
                radius = 0f
            )
        }

        val header = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text(displayContext, "STOREPOS", 13f, true).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 12), dp(displayContext, 7), dp(displayContext, 12), dp(displayContext, 7))
            background = rounded(Color.rgb(20, 118, 255), 999f)
        })
        header.addView(text(displayContext, shopName, 24f, true).apply {
            setTextColor(Color.WHITE)
            setPadding(dp(displayContext, 14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text(displayContext, "✓  PAYMENT COMPLETE", 11f, true).apply {
            setTextColor(Color.rgb(84, 230, 161))
        })
        root.addView(header, matchWidth())
        root.addView(space(displayContext, 18))

        val body = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val summary = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 28), dp(displayContext, 28), dp(displayContext, 28), dp(displayContext, 28))
            background = rounded(Color.rgb(249, 252, 255), 26f)
        }
        summary.addView(text(displayContext, "Thank you!", 34f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(19, 34, 55))
        }, matchWidth())
        summary.addView(space(displayContext, 12))
        summary.addView(text(displayContext, "Total paid", 13f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(103, 119, 139))
        }, matchWidth())
        summary.addView(text(displayContext, money(sale.totalAmount), 40f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(15, 84, 163))
        }, matchWidth())

        val change = sale.changeDue ?: 0.0
        if (change > 0.009) {
            summary.addView(space(displayContext, 16))
            summary.addView(text(displayContext, "CHANGE DUE", 12f, true).apply {
                gravity = Gravity.CENTER
                letterSpacing = 0.12f
                setTextColor(Color.rgb(103, 119, 139))
            }, matchWidth())
            summary.addView(text(displayContext, money(change), 32f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(10, 125, 90))
            }, matchWidth())
        }

        summary.addView(space(displayContext, 18))
        summary.addView(text(displayContext, "Receipt " + sale.saleNumber, 12f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(103, 119, 139))
        }, matchWidth())

        if (!receiptUrl.isNullOrBlank()) {
            body.addView(
                summary,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dp(displayContext, 16)
                }
            )

            val receiptCard = LinearLayout(displayContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(displayContext, 22), dp(displayContext, 20), dp(displayContext, 22), dp(displayContext, 20))
                background = rounded(Color.WHITE, 26f)
            }
            receiptCard.addView(text(displayContext, "Digital receipt", 22f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(19, 34, 55))
            }, matchWidth())
            receiptCard.addView(space(displayContext, 8))
            receiptCard.addView(text(displayContext, "Scan this QR code to open your receipt.", 12.5f, false).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(103, 119, 139))
            }, matchWidth())
            receiptCard.addView(space(displayContext, 12))

            val receiptQr = makeQrBitmap(receiptUrl, 720)
            receiptCard.addView(ImageView(displayContext).apply {
                setImageBitmap(receiptQr)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }, LinearLayout.LayoutParams(dp(displayContext, 210), dp(displayContext, 210)))

            receiptCard.addView(space(displayContext, 8))
            receiptCard.addView(text(displayContext, "Available for 3 days", 11.5f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(18, 99, 214))
            }, matchWidth())

            body.addView(receiptCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.82f))
        } else {
            body.addView(summary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(space(displayContext, 10))
        root.addView(text(displayContext, "Please collect your receipt and change before leaving.", 12.5f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(200, 220, 239))
        }, matchWidth())
        return root
    }

    private fun buildContent(
        displayContext: Context,
        shopName: String,
        cart: List<CartLine>
    ): View {
        val metrics = displayContext.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val wide = widthDp >= 720f
        val prefs = context.getSharedPreferences("motopos_settings", 0)
        val showBrand = prefs.getBoolean("customer_display_show_brand", true)
        val idleMessage = prefs.getString(
            "customer_display_idle_message",
            "Ready for your order"
        )?.trim().orEmpty().ifBlank { "Ready for your order" }
        val itemCount = cart.sumOf { it.quantity }
        val total = cart.sumOf { it.lineTotal }

        val root = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(displayContext, 26), dp(displayContext, 22), dp(displayContext, 26), dp(displayContext, 20))
            background = gradient(
                intArrayOf(
                    Color.rgb(7, 18, 34),
                    Color.rgb(10, 40, 72),
                    Color.rgb(10, 67, 118)
                ),
                radius = 0f
            )
        }

        // Header
        val header = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (showBrand) {
            val brand = text(displayContext, "STOREPOS", 13f, true).apply {
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(displayContext, 12), dp(displayContext, 7), dp(displayContext, 12), dp(displayContext, 7))
                background = rounded(Color.rgb(20, 118, 255), 999f)
            }
            header.addView(brand)
        }

        val titleWrap = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(displayContext, 14), 0, 0, 0)
        }
        titleWrap.addView(text(displayContext, shopName, if (wide) 25f else 21f, true).apply {
            setTextColor(Color.WHITE)
            maxLines = 1
        })
        titleWrap.addView(text(displayContext, "Customer display • Live checkout", 12.5f, false).apply {
            setTextColor(Color.rgb(185, 211, 235))
        })
        header.addView(
            titleWrap,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val livePill = text(displayContext, "●  LIVE", 12f, true).apply {
            setTextColor(Color.rgb(84, 230, 161))
            gravity = Gravity.CENTER
            setPadding(dp(displayContext, 12), dp(displayContext, 7), dp(displayContext, 12), dp(displayContext, 7))
            background = rounded(Color.argb(38, 84, 230, 161), 999f)
        }
        header.addView(livePill)

        root.addView(header, matchWidth())
        root.addView(space(displayContext, 16))

        val content = LinearLayout(displayContext).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }

        val orderCard = buildOrderCard(displayContext, cart, itemCount, wide, idleMessage)
        val summaryCard = buildSummaryCard(displayContext, cart.isEmpty(), itemCount, total, wide)

        if (wide) {
            content.addView(
                orderCard,
                LinearLayout.LayoutParams(0, 0, 1.65f).apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    marginEnd = dp(displayContext, 14)
                }
            )
            content.addView(
                summaryCard,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
            )
        } else {
            content.addView(
                orderCard,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            content.addView(space(displayContext, 12))
            content.addView(summaryCard, matchWidth())
        }

        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(space(displayContext, 12))
        root.addView(
            text(
                displayContext,
                if (cart.isEmpty())
                    "Waiting for the cashier to start your order"
                else
                    "Your order updates automatically as items are scanned",
                11.5f,
                false
            ).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(183, 205, 226))
            },
            matchWidth()
        )

        return root
    }

    private fun buildOrderCard(
        context: Context,
        cart: List<CartLine>,
        itemCount: Double,
        wide: Boolean,
        idleMessage: String
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 18))
            background = rounded(Color.rgb(249, 252, 255), 24f)
        }

        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(
            text(context, "Your order", if (wide) 22f else 19f, true).apply {
                setTextColor(Color.rgb(19, 34, 55))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        heading.addView(text(context, itemLabel(itemCount), 12f, true).apply {
            setTextColor(Color.rgb(18, 99, 214))
            gravity = Gravity.CENTER
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = rounded(Color.rgb(229, 240, 255), 999f)
        })
        card.addView(heading)
        card.addView(space(context, 12))

        if (cart.isEmpty()) {
            val emptyWrap = FrameLayout(context)
            val empty = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(context, 24), dp(context, 26), dp(context, 24), dp(context, 26))
            }

            empty.addView(text(context, "READY WHEN YOU ARE", 12f, true).apply {
                setTextColor(Color.rgb(18, 99, 214))
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
            }, matchWidth())

            empty.addView(space(context, 10))
            empty.addView(text(context, idleMessage, if (wide) 30f else 24f, true).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(20, 35, 57))
            }, matchWidth())

            empty.addView(space(context, 8))
            empty.addView(text(context, "Items will appear here as the cashier scans them.", 14f, false).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(103, 119, 139))
            }, matchWidth())

            emptyWrap.addView(
                empty,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            card.addView(
                emptyWrap,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        } else {
            val columnHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                background = rounded(Color.rgb(239, 245, 252), 12f)
            }
            columnHeader.addView(
                text(context, "ITEM", 10.5f, true).apply { setTextColor(Color.rgb(105, 122, 143)) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            columnHeader.addView(
                text(context, "AMOUNT", 10.5f, true).apply {
                    gravity = Gravity.END
                    setTextColor(Color.rgb(105, 122, 143))
                },
                LinearLayout.LayoutParams(dp(context, if (wide) 150 else 110), ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            card.addView(columnHeader)
            card.addView(space(context, 7))

            val scroll = ScrollView(context).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            cart.forEachIndexed { index, line ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(context, 9), dp(context, 10), dp(context, 9), dp(context, 10))
                }

                val qtyBadge = text(context, qty(line.quantity), 13f, true).apply {
                    setTextColor(Color.rgb(18, 99, 214))
                    gravity = Gravity.CENTER
                    minWidth = dp(context, 38)
                    setPadding(dp(context, 8), dp(context, 7), dp(context, 8), dp(context, 7))
                    background = rounded(Color.rgb(231, 241, 255), 12f)
                }
                row.addView(qtyBadge)

                val itemText = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 12), 0, dp(context, 10), 0)
                }
                itemText.addView(text(context, line.product.name, if (wide) 16f else 14f, true).apply {
                    setTextColor(Color.rgb(24, 39, 60))
                    maxLines = 2
                })
                itemText.addView(text(context, money(line.unitPrice) + " each", 11.5f, false).apply {
                    setTextColor(Color.rgb(111, 126, 145))
                })

                row.addView(
                    itemText,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )

                row.addView(
                    text(context, money(line.lineTotal), if (wide) 17f else 15f, true).apply {
                        gravity = Gravity.END
                        setTextColor(Color.rgb(14, 56, 103))
                    },
                    LinearLayout.LayoutParams(dp(context, if (wide) 150 else 110), ViewGroup.LayoutParams.WRAP_CONTENT)
                )

                list.addView(row, matchWidth())

                if (index != cart.lastIndex) {
                    list.addView(View(context).apply {
                        setBackgroundColor(Color.rgb(230, 236, 243))
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)))
                }
            }

            scroll.addView(list, matchWidth())
            card.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        return card
    }

    private fun buildSummaryCard(
        context: Context,
        empty: Boolean,
        itemCount: Double,
        total: Double,
        wide: Boolean
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 20))
            background = rounded(Color.rgb(15, 84, 163), 24f)
        }

        card.addView(text(context, if (empty) "CURRENT TOTAL" else "AMOUNT DUE", 11f, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            setTextColor(Color.rgb(192, 221, 255))
        }, matchWidth())

        card.addView(space(context, 8))

        card.addView(text(context, money(total), if (wide) 37f else 31f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            maxLines = 1
        }, matchWidth())

        card.addView(space(context, 10))

        card.addView(text(context, itemLabel(itemCount), 13f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(217, 234, 255))
        }, matchWidth())

        if (wide) {
            card.addView(
                space(context, 1),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        } else {
            card.addView(space(context, 16))
        }

        val thankYou = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            background = rounded(Color.argb(34, 255, 255, 255), 16f)
        }
        thankYou.addView(text(context, "Thank you!", 18f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, matchWidth())
        thankYou.addView(text(context, "Please review your items before payment.", 11.5f, false).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(215, 231, 249))
        }, matchWidth())

        card.addView(thankYou, matchWidth())

        return card
    }

    private fun decodeBase64Bitmap(value: String?): Bitmap? {
        val encoded = value?.substringAfter("base64,", value)?.trim().orEmpty()
        if (encoded.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun makeQrBitmap(value: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    }

    private fun text(
        context: Context,
        value: String,
        size: Float,
        bold: Boolean
    ): TextView = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(25, 34, 50))
        includeFontPadding = false
        if (bold) setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
        else typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }

    private fun gradient(colors: IntArray, radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun space(context: Context, heightDp: Int): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(context, heightDp))
        }

    private fun money(value: Double): String =
        "₱" + String.format(Locale.US, "%,.2f", value)

    private fun qty(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    private fun itemLabel(value: Double): String {
        val formatted = qty(value)
        return formatted + if (value == 1.0) " item" else " items"
    }
}
