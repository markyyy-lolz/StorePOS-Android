package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun BranchesPage(context: ShopContext) {
    var branches by remember { mutableStateOf<List<AccessibleBranch>>(emptyList()) }
    var transfers by remember { mutableStateOf<List<StockTransfer>>(emptyList()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var create by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() = coroutineScope {
        val b=async { StoreRepository.accessibleBranches() }
        val t=async { StoreRepository.stockTransfers() }
        val p=async { StoreRepository.products(context.shop.id) }
        branches=b.await()
        transfers=t.await().filter { it.fromShopId==context.shop.id || it.toShopId==context.shop.id }
        products=p.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error=StoreRepository.userMessage(it) }
        loading=false
    }
    if (loading) return LoadingView("Loading branches…")
    val other=branches.filter { it.shop.id!=context.shop.id }

    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            PageHeader("Branches & Transfers","Multi-branch inventory control",action={
                Button(onClick={create=true},enabled=other.isNotEmpty()&&products.isNotEmpty()){
                    Icon(Icons.Rounded.SwapHoriz,null); Spacer(Modifier.width(5.dp)); Text("Transfer")
                }
            })
        }
        error?.let { item { Text(it,color=MaterialTheme.colorScheme.error) } }
        item { Text("Accessible branches",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        items(branches,key={it.shop.id}) { b ->
            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.shop.name,fontWeight=FontWeight.Bold)
                        Text(b.shop.address?:"No address",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(b.member.role)
                }
            }
        }
        item { Text("Transfers",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        if(transfers.isEmpty()) item { EmptyView("No transfers","Create a stock transfer between accessible branches.") }
        items(transfers,key={it.id}) { tr ->
            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalShipping,null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr.transferNumber,fontWeight=FontWeight.Bold)
                        Text(
                            (branches.firstOrNull{it.shop.id==tr.fromShopId}?.shop?.name?:"Source")+" → "+
                            (branches.firstOrNull{it.shop.id==tr.toShopId}?.shop?.name?:"Destination"),
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(tr.status)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.align(Alignment.End)) {
                    if(tr.status=="requested"&&tr.fromShopId==context.shop.id) OutlinedButton(onClick={
                        scope.launch { runCatching{StoreRepository.shipStockTransfer(tr.id)}.onSuccess{refresh()}.onFailure{error=StoreRepository.userMessage(it)} }
                    }){Text("Ship")}
                    if(tr.status=="shipped"&&tr.toShopId==context.shop.id) Button(onClick={
                        scope.launch { runCatching{StoreRepository.receiveStockTransfer(tr.id)}.onSuccess{refresh()}.onFailure{error=StoreRepository.userMessage(it)} }
                    }){Text("Receive")}
                }
            }
        }
    }
    if(create) TransferDialog(context.shop,other.map{it.shop},products,{create=false}){to,p,q,n->
        scope.launch {
            runCatching{StoreRepository.createStockTransfer(context.shop.id,to,p,q,n)}
                .onSuccess{create=false;refresh()}.onFailure{error=StoreRepository.userMessage(it)}
        }
    }
}

@Composable
private fun TransferDialog(from:Shop,destinations:List<Shop>,products:List<Product>,onDismiss:()->Unit,onCreate:(String,String,Double,String?)->Unit){
    var to by remember{mutableStateOf(destinations.firstOrNull())}; var toMenu by remember{mutableStateOf(false)}
    var product by remember{mutableStateOf(products.firstOrNull())}; var pMenu by remember{mutableStateOf(false)}
    var qty by remember{mutableStateOf("1")}; var notes by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Stock transfer")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("From: "+from.name,fontWeight=FontWeight.Bold)
            Box {
                OutlinedButton(onClick={toMenu=true},modifier=Modifier.fillMaxWidth()){Text(to?.name?:"Destination")}
                DropdownMenu(toMenu,{toMenu=false}){destinations.forEach{b->DropdownMenuItem(text={Text(b.name)},onClick={to=b;toMenu=false})}}
            }
            Box {
                OutlinedButton(onClick={pMenu=true},modifier=Modifier.fillMaxWidth()){Text(product?.let{it.name+" • stock "+it.stockQuantity}?:"Product")}
                DropdownMenu(pMenu,{pMenu=false}){products.filter{it.isActive}.forEach{p->DropdownMenuItem(text={Text(p.name+" • "+p.stockQuantity)},onClick={product=p;pMenu=false})}}
            }
            OutlinedTextField(qty,{qty=it},label={Text("Quantity")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(notes,{notes=it},label={Text("Notes")},modifier=Modifier.fillMaxWidth())
            Text("Stock leaves the source on Ship and enters the destination on Receive.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={Button(onClick={
        val d=to?:return@Button; val p=product?:return@Button; val q=qty.toDoubleOrNull()?:return@Button
        onCreate(d.id,p.id,q,notes.trim().ifBlank{null})
    },enabled=to!=null&&product!=null&&(qty.toDoubleOrNull()?:0.0)>0){Text("Create")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
