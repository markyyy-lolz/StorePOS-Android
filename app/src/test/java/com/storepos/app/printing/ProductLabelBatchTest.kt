package com.storepos.app.printing

import com.storepos.app.data.model.Product
import org.junit.Assert.*
import org.junit.Test

class ProductLabelBatchTest {
    private fun p(id: String, shop: String = "shop") = Product(
        id = id, shopId = shop, sku = id, name = "Sample " + id, sellingPrice = 25.0
    )
    @Test fun multiSkuLabelsPreserveSingleLabelByteSequenceAndEachAutoCut() {
        val a = p("A1")
        val b = p("B2")
        for (width in listOf(58,80)) {
            val expected = ProductLabelPrinter.labelBytes(a, 2, width) +
                ProductLabelPrinter.labelBytes(b, 2, width)
            val bytes = ProductLabelPrinter.batchLabelBytes(listOf(a,b),2,width)
            assertArrayEquals(expected,bytes)
            val cuts=(0..bytes.size-3).count {i->
                bytes[i]==0x1D.toByte() && bytes[i+1]==0x56.toByte() &&
                    bytes[i+2]==0x00.toByte()
            }
            assertEquals(4,cuts)
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun crossShopLabelsAreRejected() {
        ProductLabelPrinter.batchLabelBytes(listOf(p("A"),p("B","other")),1)
    }
    @Test(expected = IllegalArgumentException::class)
    fun batchCannotExceedOneHundredPieces() {
        ProductLabelPrinter.batchLabelBytes(listOf(p("A"),p("B")),51)
    }
}
