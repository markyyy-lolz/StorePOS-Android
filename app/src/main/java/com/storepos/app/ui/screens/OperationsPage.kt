package com.storepos.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.storepos.app.printing.SharedPrintDispatcher
import com.storepos.app.printing.ThermalReportFormatter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.RetailOpsRepository
import com.storepos.app.printing.PdfReceiptLine
import com.storepos.app.printing.ReceiptPdfExporter
import com.storepos.app.data.local.OfflineStore
import com.storepos.app.data.model.*
import com.storepos.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun OperationsPage(context: ShopContext) {
    var shifts by remember { mutableStateOf<List<CashierShift>>(emptyList()) }
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var receiptSettings by remember(context.shop.id) {
        mutableStateOf(ShopSettings(shopId = context.shop.id))
    }
    var receiptSearch by remember { mutableStateOf("") }
    var pdfBusy by remember { mutableStateOf<String?>(null) }
    var receiptNotice by remember { mutableStateOf<String?>(null) }
    var pendingReceiptPdf by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var warranties by remember { mutableStateOf<List<Warranty>>(emptyList()) }
    var claims by remember { mutableStateOf<List<WarrantyClaim>>(emptyList()) }
    var zReports by remember { mutableStateOf<List<ZReport>>(emptyList()) }
    var receivables by remember { mutableStateOf<List<CustomerReceivable>>(emptyList()) }
    var customers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var motorcycles by remember { mutableStateOf<List<Motorcycle>>(emptyList()) }
    var reminders by remember { mutableStateOf<List<ServiceReminder>>(emptyList()) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var startShift by remember { mutableStateOf(false) }
    var closeShift by remember { mutableStateOf<CashierShift?>(null) }
    var cashMove by remember { mutableStateOf<Pair<CashierShift, String>?>(null) }
    var setPin by remember { mutableStateOf(false) }
    var voidSale by remember { mutableStateOf<Sale?>(null) }
    var refundSale by remember { mutableStateOf<Sale?>(null) }
    var returnable by remember { mutableStateOf<List<ReturnableSaleItem>>(emptyList()) }
    var refundLoading by remember { mutableStateOf(false) }
    var collectReceivable by remember { mutableStateOf<CustomerReceivable?>(null) }
    var addAppointment by remember { mutableStateOf(false) }
    var addReminder by remember { mutableStateOf(false) }
    var addClaim by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val offlineStore = remember { OfflineStore(androidContext) }
    val prefs = remember { androidContext.getSharedPreferences("motopos_settings", 0) }

    val receiptPdfSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val pending = pendingReceiptPdf
        pendingReceiptPdf = null
        if (uri != null && pending != null) {
            runCatching { ReceiptPdfExporter.save(androidContext, uri, pending.second) }
                .onSuccess {
                    receiptNotice = "PDF saved. Open it from Files to print again anytime, even after the digital link expires."
                }
                .onFailure { error = "Could not save PDF: " + (it.localizedMessage ?: "Storage failed") }
        }
    }

    fun handleReceiptPdf(sale: Sale, action: String) {
        if (pdfBusy != null) return
        pdfBusy = sale.id
        receiptNotice = null
        error = null
        scope.launch {
            runCatching {
                val soldItems = StoreRepository.saleItems(sale.id)
                require(soldItems.isNotEmpty()) {
                    "Original sale items are unavailable. No incomplete receipt was generated."
                }
                val recordedPayments = StoreRepository.salePayments(context.shop.id, sale.id)
                val restoredPayments = recordedPayments.map { payment ->
                    if (recordedPayments.size == 1 && payment.method == "cash") {
                        payment.copy(tendered = sale.amountTendered)
                    } else payment
                }
                ReceiptPdfExporter.render(
                    shop = context.shop,
                    sale = sale,
                    settings = receiptSettings,
                    lines = soldItems.map { item ->
                        PdfReceiptLine(
                            name = item.itemName,
                            sku = item.sku,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            lineTotal = item.lineTotal
                        )
                    },
                    payments = restoredPayments,
                    // Do not misidentify the person exporting an old receipt as its original cashier.
                    cashierLabel = null,
                    paperWidth = prefs.getInt("paper_width", receiptSettings.printerPaperWidthMm)
                )
            }.onSuccess { bytes ->
                runCatching {
                    when (action) {
                        "save" -> {
                            pendingReceiptPdf = sale.saleNumber to bytes
                            receiptPdfSaver.launch(ReceiptPdfExporter.fileName(sale.saleNumber))
                        }
                        "share" -> {
                            ReceiptPdfExporter.share(androidContext, bytes, sale.saleNumber)
                            receiptNotice = "PDF ready to share."
                        }
                        "print" -> {
                            ReceiptPdfExporter.print(
                                androidContext, bytes, sale.saleNumber,
                                prefs.getInt("paper_width", receiptSettings.printerPaperWidthMm)
                            )
                            receiptNotice = "Android print dialog opened. Select a printer or Save as PDF."
                        }
                    }
                }.onFailure { error = "Receipt action failed: " + (it.localizedMessage ?: "Please try again") }
                if (action == "print" && error == null) {
                    runCatching {
                        RetailOpsRepository.recordReceiptReprint(
                            context.shop.id, sale.id, "Android PDF receipt print requested"
                        )
                    }.onFailure {
                        receiptNotice = "Print dialog opened, but reprint audit could not sync: " +
                            StoreRepository.userMessage(it)
                    }
                }
            }.onFailure {
                error = "Unable to load original receipt: " + StoreRepository.userMessage(it)
            }
            pdfBusy = null
        }
    }


    suspend fun refresh() = coroutineScope {
        val s1 = async { StoreRepository.cashierShifts(context.shop.id) }
        val s2 = async { StoreRepository.sales(context.shop.id) }
        val s3 = async { StoreRepository.appointments(context.shop.id) }
        val s4 = async { StoreRepository.warranties(context.shop.id) }
        val s5 = async { StoreRepository.warrantyClaims(context.shop.id) }
        val s6 = async { StoreRepository.zReports(context.shop.id) }
        val s7 = async { StoreRepository.receivables(context.shop.id) }
        val s8 = async { StoreRepository.customers(context.shop.id) }
        val s9 = async { StoreRepository.motorcycles(context.shop.id) }
        val s10 = async { StoreRepository.serviceReminders(context.shop.id) }
        val s11 = async { StoreRepository.shopSettings(context.shop.id) }
        shifts = s1.await()
        sales = s2.await()
        appointments = s3.await()
        warranties = s4.await()
        claims = s5.await()
        zReports = s6.await()
        receivables = s7.await()
        customers = s8.await()
        motorcycles = s9.await()
        reminders = s10.await()
        receiptSettings = s11.await()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    if (loading) return LoadingView("Loading operations…")

    val openShift = shifts.firstOrNull { it.userId == context.userId && it.status == "open" }
    val managerRole = context.member.role.lowercase() in listOf("owner", "admin", "manager")
    val activeSales = sales.filter {
        it.status == "completed" &&
            (receiptSearch.isBlank() || it.saleNumber.contains(receiptSearch.trim(), ignoreCase = true) ||
                (it.createdAt ?: "").contains(receiptSearch.trim(), ignoreCase = true))
    }
    val upcoming = appointments.filter { it.status !in listOf("completed","cancelled","no_show") }.take(12)
    val openReceivables = receivables.filter { it.status in listOf("open","partial") }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            PageHeader(
                "Operations",
                "Register shifts, cash control, after-sales, receivables & service",
                action = {
                    IconButton(onClick = {
                        scope.launch {
                            error = null
                            runCatching { refresh() }.onFailure { error = StoreRepository.userMessage(it) }
                        }
                    }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                }
            )
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        item { Text("Register & Cash Drawer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            MotoCard(Modifier.fillMaxWidth()) {
                if (openShift == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LockClock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Register closed", fontWeight = FontWeight.Black)
                            Text(
                                "Open a shift before POS checkout. Each close creates a Z-report.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { startShift = true }) { Text("Open shift") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PointOfSale, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Register open", fontWeight = FontWeight.Black)
                            Text("Opening cash: " + money(openShift.openingCash))
                            Text(openShift.startedAt, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { cashMove = openShift to "cash_in" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.AddCircleOutline, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Cash in")
                        }
                        OutlinedButton(
                            onClick = { cashMove = openShift to "cash_out" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.RemoveCircleOutline, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Cash out")
                        }
                        Button(
                            onClick = {
                                val pending = offlineStore.pendingSales().size
                                if (pending > 0) {
                                    error = "Sync " + pending + " offline sale(s) before closing this shift."
                                } else {
                                    closeShift = openShift
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close shift")
                        }
                    }
                }
            }
        }

        if (managerRole) {
            item {
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Manager Approval PIN", fontWeight = FontWeight.Bold)
                            Text(
                                "Used for protected discounts, voids and partial/full refunds.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { setPin = true }) { Text("Set PIN") }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Z-Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(zReports.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (zReports.isEmpty()) {
            item { Text("Close a shift to generate the first Z-report.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(zReports.take(5), key = { it.id }) { z ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Z Report", fontWeight = FontWeight.Black)
                            Text(z.generatedAt, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(money(z.grossSales), fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cash sales " + money(z.cashSales))
                        Text("Non-cash " + money(z.noncashSales))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Expected " + money(z.expectedCash))
                        Text("Actual " + money(z.actualCash))
                    }
                    Text(
                        "Variance " + money(z.variance) + " • " + z.transactionCount + " transaction(s)",
                        color = if (kotlin.math.abs(z.variance) < .01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val bytes = ThermalReportFormatter.z(context.shop,z,context.userId.take(8))
                                    SharedPrintDispatcher.dispatch(androidContext,context.shop.id,"z_report",bytes)
                                }.onSuccess { receiptNotice = it }
                                 .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        }) { Text("Print Z reading") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val bytes = ThermalReportFormatter.z(context.shop,z,context.userId.take(8),true)
                                    SharedPrintDispatcher.dispatch(androidContext,context.shop.id,"batch_report",bytes)
                                }.onSuccess { receiptNotice = it }
                                 .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        }) { Text("Batch sales") }
                    }
                }
            }
        }

        item { Text("Receipts & Sales Aftercare", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(
                value = receiptSearch,
                onValueChange = { receiptSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search receipt / sale number or date") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
            )
            Text(
                "Receipt PDF matches the 58mm/80mm thermal printout (same layout, spacing and totals). Saved PDFs work offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        receiptNotice?.let {
            item { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        if (activeSales.isEmpty()) {
            item { EmptyView("No matching completed sales", "Try another receipt number or date.") }
        } else {
            items(activeSales, key = { it.id }) { sale ->
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sale.saleNumber, fontWeight = FontWeight.Bold)
                            Text(sale.createdAt ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(sale.totalAmount), fontWeight = FontWeight.Black)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { handleReceiptPdf(sale, "save") },
                            enabled = pdfBusy == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(3.dp))
                            Text("Save PDF", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { handleReceiptPdf(sale, "share") },
                            enabled = pdfBusy == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(3.dp))
                            Text("Share", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { handleReceiptPdf(sale, "print") },
                            enabled = pdfBusy == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null)
                            Spacer(Modifier.width(3.dp))
                            Text("Print PDF", maxLines = 1)
                        }
                    }
                    if (pdfBusy == sale.id) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                        OutlinedButton(onClick = {
                            refundSale = sale
                            refundLoading = true
                            scope.launch {
                                runCatching { StoreRepository.returnableSaleItems(sale.id) }
                                    .onSuccess { returnable = it }
                                    .onFailure { error = StoreRepository.userMessage(it) }
                                refundLoading = false
                            }
                        }) {
                            Icon(Icons.Rounded.Replay, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Return / Refund")
                        }
                        OutlinedButton(onClick = { voidSale = sale }) {
                            Icon(Icons.Rounded.Block, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Void")
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Customer Receivables", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(money(openReceivables.sumOf { it.balance }), fontWeight = FontWeight.Bold)
            }
        }
        if (openReceivables.isEmpty()) {
            item { Text("No outstanding customer credit.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(openReceivables.take(12), key = { it.id }) { rec ->
                val customer = customers.firstOrNull { it.id == rec.customerId }
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(customer?.name ?: "Customer", fontWeight = FontWeight.Bold)
                            Text("Balance " + money(rec.balance) + " of " + money(rec.originalAmount))
                        }
                        Button(onClick = { collectReceivable = rec }) {
                            Text("Collect")
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Maintenance Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(
                    onClick = { addReminder = true },
                    enabled = customers.isNotEmpty() && motorcycles.isNotEmpty()
                ) {
                    Icon(Icons.Rounded.NotificationsActive, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New reminder")
                }
            }
        }
        val pendingReminders = reminders.filter { it.status == "pending" }
        if (pendingReminders.isEmpty()) {
            item { Text("No pending maintenance reminders.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(pendingReminders.take(12), key = { it.id }) { reminder ->
                val customer = customers.firstOrNull { it.id == reminder.customerId }
                val bike = motorcycles.firstOrNull { it.id == reminder.motorcycleId }
                MotoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(reminder.title, fontWeight = FontWeight.Bold)
                            Text(
                                (customer?.name ?: "Customer") + " • " +
                                    (bike?.let { it.make + " " + it.model } ?: "Motorcycle"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                listOfNotNull(
                                    reminder.dueDate?.let { "Due " + it },
                                    reminder.dueOdometerKm?.let { "At " + it + " km" }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { StoreRepository.updateServiceReminderStatus(reminder.id, "completed") }
                                    .onSuccess { refresh() }
                                    .onFailure { error = StoreRepository.userMessage(it) }
                            }
                        }) { Text("Complete") }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Appointments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = { addAppointment = true }) {
                    Icon(Icons.Rounded.Event, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Book")
                }
            }
        }
        if (upcoming.isEmpty()) {
            item { Text("No upcoming appointments.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(upcoming, key = { it.id }) { appt ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.EventAvailable, null) },
                    headlineContent = { Text(appt.customerName ?: "Customer", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(appt.serviceRequest + " • " + appt.scheduledAt) },
                    trailingContent = { StatusPill(appt.status) }
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Warranty Claims", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (warranties.isNotEmpty()) {
                    OutlinedButton(onClick = { addClaim = true }) { Text("New claim") }
                }
            }
        }
        if (claims.isEmpty()) {
            item {
                Text(
                    if (warranties.isEmpty()) "No active warranty records yet." else "No warranty claims yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(claims.take(12), key = { it.id }) { claim ->
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.VerifiedUser, null) },
                    headlineContent = { Text(claim.claimNumber, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(claim.issue) },
                    trailingContent = { StatusPill(claim.status) }
                )
            }
        }
    }

    if (startShift) StartShiftDialog(
        onDismiss = { startShift = false },
        onStart = { opening ->
            scope.launch {
                error = null
                runCatching { StoreRepository.startCashierShift(context.shop.id, opening) }
                    .onSuccess { shift ->
                        prefs.edit().putString("open_shift_" + context.shop.id, shift.id).apply()
                        startShift = false
                        refresh()
                    }
                    .onFailure { error = StoreRepository.userMessage(it) }
            }
        }
    )

    closeShift?.let { shift ->
        CloseShiftDialog(
            shift = shift,
            onDismiss = { closeShift = null },
            onClose = { actual, notes ->
                scope.launch {
                    error = null
                    if (offlineStore.pendingSales().isNotEmpty()) {
                        error = "Pending offline sales must sync before shift close."
                        return@launch
                    }
                    runCatching { StoreRepository.endCashierShift(shift.id, actual, notes) }
                        .onSuccess {
                            prefs.edit().remove("open_shift_" + context.shop.id).apply()
                            closeShift = null
                            refresh()
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    cashMove?.let { pair ->
        CashMovementDialog(
            type = pair.second,
            onDismiss = { cashMove = null },
            onSave = { amount, reason ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.recordCashMovement(pair.first.id, pair.second, amount, reason) }
                        .onSuccess { cashMove = null; refresh() }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (setPin) ManagerPinDialog(
        onDismiss = { setPin = false },
        onSave = { pin ->
            scope.launch {
                error = null
                runCatching { StoreRepository.setManagerPin(context.shop.id, pin) }
                    .onSuccess { setPin = false }
                    .onFailure { error = StoreRepository.userMessage(it) }
            }
        }
    )

    voidSale?.let { sale ->
        VoidSaleDialog(
            sale = sale,
            onDismiss = { voidSale = null },
            onConfirm = { pin, reason ->
                scope.launch {
                    error = null
                    runCatching { StoreRepository.voidSale(sale.id, pin, reason) }
                        .onSuccess { voidSale = null; refresh() }
                        .onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    refundSale?.let { sale ->
        PartialRefundDialog(
            sale = sale,
            lines = returnable,
            loading = refundLoading,
            onDismiss = {
                refundSale = null
                returnable = emptyList()
            },
            onConfirm = { selections, pin, reason, method ->
                scope.launch {
                    error = null
                    runCatching {
                        StoreRepository.returnSaleItems(
                            saleId = sale.id,
                            items = selections,
                            managerPin = pin,
                            reason = reason,
                            refundMethod = method
                        )
                    }.onSuccess {
                        refundSale = null
                        returnable = emptyList()
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    collectReceivable?.let { rec ->
        ReceivablePaymentDialog(
            receivable = rec,
            onDismiss = { collectReceivable = null },
            onConfirm = { amount, method, reference ->
                scope.launch {
                    error = null
                    runCatching {
                        StoreRepository.receiveCustomerPayment(rec.id, amount, method, reference)
                    }.onSuccess {
                        collectReceivable = null
                        refresh()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }

    if (addReminder) ServiceReminderDialog(
        context = context,
        customers = customers,
        motorcycles = motorcycles,
        onDismiss = { addReminder = false },
        onSave = { input ->
            scope.launch {
                error = null
                runCatching { StoreRepository.addServiceReminder(input) }
                    .onSuccess { addReminder = false; refresh() }
                    .onFailure { error = StoreRepository.userMessage(it) }
            }
        }
    )

    if (addAppointment) AppointmentDialog(
        context = context,
        onDismiss = { addAppointment = false },
        onSave = { input ->
            scope.launch {
                error = null
                runCatching { StoreRepository.addAppointment(input) }
                    .onSuccess { addAppointment = false; refresh() }
                    .onFailure { error = StoreRepository.userMessage(it) }
            }
        }
    )

    if (addClaim) WarrantyClaimDialog(
        context = context,
        warranties = warranties,
        onDismiss = { addClaim = false },
        onSave = { input ->
            scope.launch {
                error = null
                runCatching { StoreRepository.addWarrantyClaim(input) }
                    .onSuccess { addClaim = false; refresh() }
                    .onFailure { error = StoreRepository.userMessage(it) }
            }
        }
    )
}

@Composable
private fun StartShiftDialog(onDismiss: () -> Unit, onStart: (Double) -> Unit) {
    var opening by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open cashier shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Count the opening drawer before starting sales.")
                OutlinedTextField(
                    opening,
                    { opening = it },
                    label = { Text("Opening cash") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(opening.toDoubleOrNull() ?: 0.0) },
                enabled = (opening.toDoubleOrNull() ?: -1.0) >= 0
            ) { Text("Open shift") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CloseShiftDialog(
    shift: CashierShift,
    onDismiss: () -> Unit,
    onClose: (Double, String?) -> Unit
) {
    var actual by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close shift & create Z-report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Opening cash: " + money(shift.openingCash))
                Text("Count the physical drawer. StorePOS will compare it with expected cash.")
                OutlinedTextField(
                    actual,
                    { actual = it },
                    label = { Text("Actual cash in drawer") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Closing notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onClose(actual.toDoubleOrNull() ?: 0.0, notes.trim().ifBlank { null }) },
                enabled = actual.toDoubleOrNull() != null
            ) { Text("Close & generate Z") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CashMovementDialog(
    type: String,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val cashIn = type == "cash_in"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cashIn) "Cash in" else "Cash out") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    reason,
                    { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(amount.toDoubleOrNull() ?: 0.0, reason.trim()) },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && reason.trim().length >= 2
            ) { Text(if (cashIn) "Add cash" else "Remove cash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManagerPinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Manager PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use 4–8 digits. The hash is stored privately in StorePOS Cloud.")
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    confirm,
                    { confirm = it.filter(Char::isDigit).take(8) },
                    label = { Text("Confirm PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(pin) },
                enabled = pin.length in 4..8 && pin == confirm
            ) { Text("Save PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun VoidSaleDialog(
    sale: Sale,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void " + sale.saleNumber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This reverses the entire sale and restores tracked inventory.")
                Text("Total: " + money(sale.totalAmount), fontWeight = FontWeight.Bold)
                OutlinedTextField(reason, { reason = it }, label = { Text("Void reason") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("Manager PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin, reason.trim()) },
                enabled = pin.length >= 4 && reason.trim().length >= 2
            ) { Text("Void sale") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class RefundLineState(
    val line: ReturnableSaleItem,
    val quantity: String,
    val selected: Boolean = true,
    val restock: Boolean = true
)

@Composable
private fun PartialRefundDialog(
    sale: Sale,
    lines: List<ReturnableSaleItem>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<Triple<ReturnableSaleItem, Double, Boolean>>, String, String, String) -> Unit
) {
    var states by remember(lines) {
        mutableStateOf(
            lines.map {
                RefundLineState(
                    line = it,
                    quantity = if (it.remainingQuantity % 1.0 == 0.0)
                        it.remainingQuantity.toInt().toString()
                    else it.remainingQuantity.toString()
                )
            }
        )
    }
    var pin by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("cash") }
    var menu by remember { mutableStateOf(false) }

    val selections = states.filter { it.selected }.mapNotNull { state ->
        val qty = state.quantity.toDoubleOrNull() ?: return@mapNotNull null
        if (qty <= 0 || qty > state.line.remainingQuantity) return@mapNotNull null
        Triple(state.line, qty, state.restock)
    }
    val refundTotal = selections.sumOf { (line, qty, _) -> line.unitRefund * qty }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return / Refund " + sale.saleNumber) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (lines.isEmpty()) {
                Text("There are no remaining items available to return.")
            } else {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(states, key = { it.line.saleItemId }) { state ->
                            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = state.selected,
                                            onCheckedChange = { checked ->
                                                states = states.map {
                                                    if (it.line.saleItemId == state.line.saleItemId) it.copy(selected = checked) else it
                                                }
                                            }
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(state.line.itemName, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Remaining " + state.line.remainingQuantity + " • " + money(state.line.unitRefund) + " each",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    if (state.selected) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                state.quantity,
                                                { value ->
                                                    states = states.map {
                                                        if (it.line.saleItemId == state.line.saleItemId) it.copy(quantity = value) else it
                                                    }
                                                },
                                                label = { Text("Return qty") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Checkbox(
                                                checked = state.restock,
                                                onCheckedChange = { checked ->
                                                    states = states.map {
                                                        if (it.line.saleItemId == state.line.saleItemId) it.copy(restock = checked) else it
                                                    }
                                                }
                                            )
                                            Text("Restock")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Refund method: " + method.replace("_"," ").uppercase())
                        }
                        DropdownMenu(menu, { menu = false }) {
                            listOf("cash","gcash","maya","card","bank","store_credit","other").forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.replace("_"," ").uppercase()) },
                                    onClick = { method = m; menu = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        reason,
                        { reason = it },
                        label = { Text("Refund reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        pin,
                        { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Manager PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Refund total: " + money(refundTotal), fontWeight = FontWeight.Black)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selections, pin, reason.trim(), method) },
                enabled = !loading && selections.isNotEmpty() && pin.length >= 4 && reason.trim().length >= 2
            ) { Text("Process refund") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReceivablePaymentDialog(
    receivable: CustomerReceivable,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String?) -> Unit
) {
    var amount by remember { mutableStateOf(receivable.balance.toString()) }
    var method by remember { mutableStateOf("cash") }
    var reference by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val refRequired = method in listOf("gcash","maya","card","bank")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collect customer payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding balance: " + money(receivable.balance), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Payment amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(method.uppercase())
                    }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("cash","gcash","maya","card","bank","other").forEach { m ->
                            DropdownMenuItem(text = { Text(m.uppercase()) }, onClick = { method = m; menu = false })
                        }
                    }
                }
                if (method != "cash") {
                    OutlinedTextField(
                        reference,
                        { reference = it },
                        label = { Text(if (refRequired) "Reference number" else "Reference / note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amountValue, method, reference.trim().ifBlank { null }) },
                enabled = amountValue > 0 &&
                    amountValue <= receivable.balance + .009 &&
                    (!refRequired || reference.trim().isNotBlank())
            ) { Text("Post payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ServiceReminderDialog(
    context: ShopContext,
    customers: List<Customer>,
    motorcycles: List<Motorcycle>,
    onDismiss: () -> Unit,
    onSave: (ServiceReminderInsert) -> Unit
) {
    var customer by remember { mutableStateOf<Customer?>(null) }
    var bike by remember { mutableStateOf<Motorcycle?>(null) }
    var customerMenu by remember { mutableStateOf(false) }
    var bikeMenu by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Preventive maintenance") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusMonths(3).toString()) }
    var dueKm by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val bikes = motorcycles.filter { it.customerId == customer?.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New maintenance reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(customer?.name ?: "Select customer")
                    }
                    DropdownMenu(customerMenu, { customerMenu = false }) {
                        customers.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                customer = c
                                bike = null
                                customerMenu = false
                            })
                        }
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { bikeMenu = true },
                        enabled = customer != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(bike?.let { it.make + " " + it.model } ?: "Select motorcycle")
                    }
                    DropdownMenu(bikeMenu, { bikeMenu = false }) {
                        bikes.forEach { b ->
                            DropdownMenuItem(text = { Text(b.make + " " + b.model) }, onClick = {
                                bike = b
                                bikeMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Reminder") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        dueDate,
                        { dueDate = it },
                        label = { Text("Due date") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        dueKm,
                        { dueKm = it },
                        label = { Text("Due km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cst = customer ?: return@Button
                    val mc = bike ?: return@Button
                    onSave(
                        ServiceReminderInsert(
                            shopId = context.shop.id,
                            customerId = cst.id,
                            motorcycleId = mc.id,
                            title = title.trim(),
                            dueDate = dueDate.trim().ifBlank { null },
                            dueOdometerKm = dueKm.toDoubleOrNull(),
                            notes = notes.trim().ifBlank { null },
                            createdBy = context.userId
                        )
                    )
                },
                enabled = customer != null && bike != null && title.trim().length >= 2
            ) { Text("Save reminder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AppointmentDialog(context: ShopContext, onDismiss: () -> Unit, onSave: (AppointmentInsert) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("09:00") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New appointment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Customer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(service, { service = it }, label = { Text("Service request") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(time, { time = it }, label = { Text("HH:MM") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AppointmentInsert(
                            shopId = context.shop.id,
                            customerName = name.trim(),
                            phone = phone.trim().ifBlank { null },
                            serviceRequest = service.trim(),
                            scheduledAt = date.trim() + "T" + time.trim() + ":00+08:00",
                            notes = notes.trim().ifBlank { null },
                            createdBy = context.userId
                        )
                    )
                },
                enabled = name.isNotBlank() && service.isNotBlank() && date.length >= 10 && time.length >= 4
            ) { Text("Book") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WarrantyClaimDialog(
    context: ShopContext,
    warranties: List<Warranty>,
    onDismiss: () -> Unit,
    onSave: (WarrantyClaimInsert) -> Unit
) {
    var warranty by remember { mutableStateOf<Warranty?>(warranties.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New warranty claim") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(warranty?.description ?: "Select warranty")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        warranties.forEach { w ->
                            DropdownMenuItem(
                                text = { Text(w.warrantyType + ": " + w.description) },
                                onClick = { warranty = w; menu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    issue,
                    { issue = it },
                    label = { Text("Issue / claim details") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = warranty ?: return@Button
                    onSave(WarrantyClaimInsert(context.shop.id, w.id, issue.trim(), context.userId))
                },
                enabled = warranty != null && issue.isNotBlank()
            ) { Text("Create claim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
