package com.storepos.app.data.local

import com.storepos.app.data.model.*
import java.util.UUID

/**
 * All offline mutations commit to the local event log AND optimistic catalog
 * in one SQLite transaction. Never use these helpers to overwrite cloud data.
 */
object OfflineInventoryActions {
    private val inventoryRoles=setOf("owner","admin","manager","inventory")
    private val managerRoles=setOf("owner","admin","manager")
    private fun epoch(store:OfflineStore,shopId:String):String =
        store.cachedCatalogEpoch(shopId)
            ?: error("Connect once to download a trusted StorePOS catalog.")

    fun add(
        store:OfflineStore,shopId:String,actorId:String,role:String,
        catalog:List<Product>,input:ProductInsert
    ):List<Product> {
        require(role.lowercase() in managerRoles) { "Manager approval needed to add a product offline." }
        require(input.shopId==shopId && input.sku.isNotBlank() && input.name.isNotBlank())
        require(catalog.none { it.sku.equals(input.sku.trim(),true) }) {
            "SKU already exists in the local catalog."
        }
        require(input.sellingPrice.isFinite() && input.costPrice.isFinite() &&
            input.stockQuantity.isFinite() && input.reorderLevel.isFinite() &&
            input.sellingPrice>=0 && input.costPrice>=0 &&
            input.stockQuantity>=0 && input.reorderLevel>=0)
        val item=Product(
            id=UUID.randomUUID().toString(),shopId=shopId,
            sku=input.sku.trim(),barcode=input.barcode,name=input.name.trim(),
            categoryId=input.categoryId,brand=input.brand,partNumber=input.partNumber,
            itemType=input.itemType,costPrice=input.costPrice,
            sellingPrice=input.sellingPrice,stockQuantity=input.stockQuantity,
            reorderLevel=input.reorderLevel,unit=input.unit,trackStock=true
        )
        val op=OfflineInventoryOperation(
            id=UUID.randomUUID().toString(),shopId=shopId,actorId=actorId,
            catalogEpoch=epoch(store,shopId),kind="create",productId=item.id,after=item
        )
        return (catalog+item).also { store.enqueueInventory(listOf(op),it) }
    }

    fun edit(
        store:OfflineStore,shopId:String,actorId:String,role:String,
        catalog:List<Product>,updated:Product
    ):List<Product> {
        require(role.lowercase() in managerRoles) { "Manager approval needed to edit products offline." }
        val old=catalog.firstOrNull { it.id==updated.id && it.shopId==shopId }
            ?: error("Product not in offline catalog")
        require(old.isActive && updated.isActive) {
            "Archive/reactivation is online-only to protect pending sales."
        }
        require(old.sku==updated.sku && old.stockQuantity==updated.stockQuantity) {
            "Use Stock Adjustment for quantities. SKU changes require cloud."
        }
        require(!old.serialTracked && !old.batchTracked && !old.isWeighed &&
            old.retailParentId==null) { "Complex stock requires online processing." }
        require(updated.sellingPrice.isFinite() && updated.costPrice.isFinite() &&
            updated.reorderLevel.isFinite() && updated.sellingPrice>=0 &&
            updated.costPrice>=0 && updated.reorderLevel>=0 &&
            updated.name.isNotBlank())
        val op=OfflineInventoryOperation(
            id=UUID.randomUUID().toString(),shopId=shopId,actorId=actorId,
            catalogEpoch=epoch(store,shopId),kind="edit",productId=old.id,
            before=old,after=updated
        )
        return catalog.map { if(it.id==old.id)updated else it }
            .also { store.enqueueInventory(listOf(op),it) }
    }

    fun adjust(
        store:OfflineStore,shopId:String,actorId:String,role:String,
        catalog:List<Product>,productId:String,delta:Double,reason:String,notes:String?
    ):List<Product> {
        require(role.lowercase() in inventoryRoles) { "Inventory permission required." }
        val old=catalog.firstOrNull { it.id==productId && it.shopId==shopId && it.isActive }
            ?: error("Product not in offline catalog")
        require(!old.serialTracked && !old.batchTracked && !old.isWeighed &&
            old.retailParentId==null) { "Batch, serial and weighed products require internet." }
        require(delta.isFinite() && delta!=0.0 && kotlin.math.abs(delta)<=100000000 &&
            reason in setOf("adjustment","damage","theft","return","opening"))
        val next=old.stockQuantity+delta
        require(next>=0) { "Stock cannot go below zero on this tablet." }
        val op=OfflineInventoryOperation(
            id=UUID.randomUUID().toString(),shopId=shopId,actorId=actorId,
            catalogEpoch=epoch(store,shopId),kind="adjust",productId=old.id,
            delta=delta,reason=reason,notes=notes
        )
        return catalog.map { if(it.id==old.id)old.copy(stockQuantity=next) else it }
            .also { store.enqueueInventory(listOf(op),it) }
    }

    /** Partial physical stocktake: ONLY explicitly counted SKUs are adjusted. */
    fun count(
        store:OfflineStore,shopId:String,actorId:String,role:String,
        catalog:List<Product>,values:Map<String,Double>
    ):List<Product> {
        require(role.lowercase() in managerRoles) { "Manager approval required for offline stocktake." }
        require(values.isNotEmpty()) { "Scan or enter at least one counted quantity." }
        val byId=catalog.associateBy { it.id }
        val uuid=epoch(store,shopId)
        val ops=values.map { (id,qty) ->
            val old=byId[id] ?: error("Stocktake item missing")
            require(old.shopId==shopId && old.isActive && old.trackStock &&
                !old.serialTracked && !old.batchTracked && !old.isWeighed &&
                old.retailParentId==null) { "This stocktake item requires online verification." }
            require(qty.isFinite() && qty>=0 && qty<=100000000) { "Invalid physical count." }
            OfflineInventoryOperation(
                id=UUID.randomUUID().toString(),shopId=shopId,actorId=actorId,
                catalogEpoch=uuid,kind="count",productId=id,
                expectedStock=old.stockQuantity,countedStock=qty
            )
        }
        return catalog.map { p -> values[p.id]?.let { p.copy(stockQuantity=it) } ?: p }
            .also { store.enqueueInventory(ops,it) }
    }
}
