package com.storepos.app.data.local

import com.storepos.app.data.StoreRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OfflineInventorySyncResult(
    val synced: Int,
    val pending: Int,
    val review: Int,
    val retry: Boolean
)

/** Foreground and background inventory sync share this lock. */
object OfflineInventorySynchronizer {
    private val mutex=Mutex()

    suspend fun syncShop(shopId:String,store:OfflineStore): OfflineInventorySyncResult =
        mutex.withLock {
            val list=store.pendingInventory(shopId)
            val actor=StoreRepository.currentUserId()
            if(actor.isNullOrBlank() || list.isEmpty())
                return@withLock summary(shopId,store,0,list.isNotEmpty())
            var successful=0
            var retry=false
            for(entry in list) {
                val op=entry.operation
                if(op.shopId!=shopId || op.actorId!=actor) {
                    retry=true // Wait for original operator's authenticated session.
                    continue
                }
                try {
                    StoreRepository.reconcileOfflineInventory(op)
                    store.removePendingInventory(shopId,op.id)
                    successful++
                }catch(err:Throwable) {
                    val message=StoreRepository.userMessage(err)
                    val lower=message.lowercase()
                    if(listOf("timeout","network","host","socket","connect","unreachable",
                            "temporarily","authentication","sign in","session").any { it in lower }) {
                        store.setInventoryError(shopId,op.id,message)
                        retry=true
                    }else {
                        // Never silently overwrite another tablet's inventory or
                        // discard a conflict. Operator must reconcile manually.
                        store.markInventoryReview(shopId,op.id,message)
                    }
                }
            }
            summary(shopId,store,successful,retry)
        }

    private fun summary(shopId:String,store:OfflineStore,synced:Int,retry:Boolean) =
        OfflineInventorySyncResult(
            synced=synced,
            pending=store.pendingInventory(shopId).size,
            review=store.needsReviewInventory(shopId).size,
            retry=retry
        )
}
