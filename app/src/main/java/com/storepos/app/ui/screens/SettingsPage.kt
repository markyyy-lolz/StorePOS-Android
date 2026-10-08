package com.storepos.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.storepos.app.BuildConfig
import com.storepos.app.data.AdminRepository
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.AppVersion
import com.storepos.app.data.model.DeviceSession
import com.storepos.app.data.model.ShopContext
import com.storepos.app.data.model.ShopMember
import com.storepos.app.data.model.ShopSettings
import com.storepos.app.data.model.UserProfile
import com.storepos.app.data.model.MemberPermissionOverride
import com.storepos.app.notifications.StorePosAlertWorker
import com.storepos.app.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(context: ShopContext) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { androidContext.getSharedPreferences("motopos_settings", 0) }
    val canAdmin = context.member.role.lowercase() in setOf("owner", "admin")
    val canTraining = context.member.role.lowercase() in setOf("owner", "admin", "manager")
    var trainingMode by remember(context.shop.id) {
        mutableStateOf(prefs.getBoolean("training_mode_" + context.shop.id, false))
    }
    var backgroundAlerts by remember {
        mutableStateOf(prefs.getBoolean("background_alerts_enabled", true))
    }
    var devices by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var members by remember { mutableStateOf<List<ShopMember>>(emptyList()) }
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var permissionMember by remember { mutableStateOf<ShopMember?>(null) }
    var permissionRows by remember { mutableStateOf<List<MemberPermissionOverride>>(emptyList()) }
    var permissionLoading by remember { mutableStateOf(false) }
    var adminLoading by remember { mutableStateOf(false) }
    var createStaffOpen by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<AppVersion?>(null) }
    var posSettings by remember { mutableStateOf(ShopSettings(shopId = context.shop.id)) }
    var posSettingsOpen by remember { mutableStateOf(false) }
    var receiptDesignerOpen by remember { mutableStateOf(false) }
    var customerDisplaySettingsOpen by remember { mutableStateOf(false) }
    var savingSettings by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAdminData() {
        if (!canAdmin) return
        adminLoading = true
        runCatching {
            val loadedDevices = StoreRepository.deviceSessions(context.shop.id)
            val loadedMembers = StoreRepository.shopMembers(context.shop.id)
            val loadedProfiles = StoreRepository.userProfiles()
            Triple(loadedDevices, loadedMembers, loadedProfiles)
        }.onSuccess { (loadedDevices, loadedMembers, loadedProfiles) ->
            devices = loadedDevices
            members = loadedMembers
            profiles = loadedProfiles
        }.onFailure {
            error = StoreRepository.userMessage(it)
        }
        adminLoading = false
    }

    LaunchedEffect(context.shop.id) {
        runCatching { StoreRepository.shopSettings(context.shop.id) }
            .onSuccess { posSettings = it }
            .onFailure { error = StoreRepository.userMessage(it) }
        refreshAdminData()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader("Settings", "Shop, cloud, printer and app configuration")
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Shop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(context.shop.name, style = MaterialTheme.typography.headlineSmall)
            context.shop.address?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            context.shop.phone?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Role: ${context.member.role.uppercase()}", color = MaterialTheme.colorScheme.primary)
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Row {
                Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Supabase Cloud", fontWeight = FontWeight.Bold)
                    Text("Connected • secure RLS • realtime-ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        MotoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Background Store Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Check critical and warning alerts every 30 minutes when internet is available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = backgroundAlerts,
                    onCheckedChange = { enabled ->
                        backgroundAlerts = enabled
                        prefs.edit().putBoolean("background_alerts_enabled", enabled).apply()
                        if (enabled) StorePosAlertWorker.schedule(androidContext)
                        else StorePosAlertWorker.cancel(androidContext)
                    }
                )
            }
        }

        if (canTraining) {
            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.School,
                        contentDescription = null,
                        tint = if (trainingMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Training Mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (trainingMode)
                                "ON • POS checkouts stay local and do not change real sales, stock or cloud reports."
                            else
                                "Practice cashier transactions without affecting production data.",
                            color = if (trainingMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = trainingMode,
                        onCheckedChange = { enabled ->
                            trainingMode = enabled
                            prefs.edit().putBoolean("training_mode_" + context.shop.id, enabled).apply()
                        }
                    )
                }
                if (trainingMode) {
                    Text(
                        "TRAINING MODE is device-only. StorePOS will clearly mark training transactions and block PayMongo/real checkout posting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (canAdmin) {
            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ManageAccounts, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Staff & Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Role-based access is enforced by StorePOS navigation and Supabase RLS.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { refreshAdminData() } },
                        enabled = !adminLoading
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh staff and devices")
                    }
                }
                Button(onClick = { createStaffOpen = true }, enabled = !adminLoading) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create staff account")
                }
                if (adminLoading && members.isEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    members.forEach { member ->
                        StaffPermissionRow(
                            member = member,
                            profile = profiles.firstOrNull { it.id == member.userId },
                            currentUserId = context.userId,
                            currentRole = context.member.role,
                            onChange = { role, active ->
                                scope.launch {
                                    error = null
                                    runCatching {
                                        StoreRepository.updateMemberRole(member.id, role, active)
                                    }.onSuccess {
                                        refreshAdminData()
                                    }.onFailure {
                                        error = StoreRepository.userMessage(it)
                                    }
                                }
                            },
                            onPermissions = {
                                permissionMember = member
                                permissionLoading = true
                                scope.launch {
                                    error = null
                                    runCatching {
                                        AdminRepository.memberPermissions(member.id)
                                    }.onSuccess {
                                        permissionRows = it
                                    }.onFailure {
                                        error = StoreRepository.userMessage(it)
                                    }
                                    permissionLoading = false
                                }
                            }
                        )
                    }
                }
            }

            MotoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Device Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            devices.count { it.isActive }.toString() + " active terminal(s) • revoke a device to require license reactivation",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (devices.isEmpty()) {
                    Text("No StorePOS device sessions found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    devices.take(12).forEach { session ->
                        DeviceSessionRow(
                            session = session,
                            profile = profiles.firstOrNull { it.id == session.userId },
                            onSetActive = { active ->
                                scope.launch {
                                    error = null
                                    runCatching {
                                        StoreRepository.setDeviceSessionActive(session.id, active)
                                    }.onSuccess {
                                        refreshAdminData()
                                    }.onFailure {
                                        error = StoreRepository.userMessage(it)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Software information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "StorePOS v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Retail Store POS & Management System", fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Text("Created & Developed by Mark Reymuel Pascual", fontWeight = FontWeight.Bold)
            Text(
                "Project lead • product design • Android development • system architecture",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Kotlin • Jetpack Compose • Material 3 • Supabase Cloud • Bluetooth/USB ESC/POS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "© 2026 Mark Reymuel Pascual • StorePOS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Text("Register & checkout policy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                listOf(
                    if (posSettings.requireCashierShift) "Shift required" else "Shift optional",
                    if (posSettings.taxEnabled) "Tax " + posSettings.defaultTaxRate + "%" else "Tax off",
                    "Cashier discount " + posSettings.cashierDiscountLimitPercent + "%",
                    if (posSettings.autoPrintReceipt) "Auto print" else "Manual print"
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { posSettingsOpen = true }) {
                Text("Configure POS")
            }
        }

        MotoCard(Modifier.fillMaxWidth()) {
            Text("Receipt Designer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Customize receipt content, ORPH details, QR visibility, section order and customer-facing store information with a live thermal preview.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { receiptDesignerOpen = true }) {
                    Text("Open Receipt Designer")
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (posSettings.receiptShowDigitalQr)
                                "Digital QR on"
                            else
                                "Digital QR off"
                        )
                    }
                )
            }
        }

        PrinterSettingsCard(context)
        SharedPrinterSettingsCard(context)

        MotoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.ScreenshotMonitor, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Customer Display", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Configure the external HDMI / presentation screen used for live customer checkout.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                listOf(
                    if (prefs.getBoolean("customer_display_auto", false)) "Auto-start on" else "Manual start",
                    if (prefs.getBoolean("customer_display_show_brand", true)) "StorePOS branding on" else "StorePOS branding off"
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { customerDisplaySettingsOpen = true }) {
                Text("Configure customer display")
            }
        }

        MotoCard(Modifier.fillMaxWidth()) {
            Text("Cloud backup & billing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Export Sales/Inventory/Customers CSV, download a full JSON shop backup, submit license payments and custom orders in StorePOS Cloud.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                androidContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://markyyy-lolz.github.io/StorePOS-Web/#/dashboard/settings")
                    )
                )
            }) {
                Text("Open Cloud Backup & Export")
            }
            OutlinedButton(onClick = {
                androidContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://markyyy-lolz.github.io/StorePOS-Web/#/dashboard/license")
                    )
                )
            }) {
                Text("Manage license & payment")
            }
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("App manual & Help Center", fontWeight = FontWeight.Bold)
                    Text(
                        "Open the full StorePOS guide, troubleshooting and role instructions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = {
                androidContext.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://markyyy-lolz.github.io/StorePOS-Web/#/manual")
                    )
                )
            }) {
                Text("Open manual")
            }
        }
        MotoCard(Modifier.fillMaxWidth()) {
            Text("App update", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Installed: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            latest?.let { v ->
                Text("Latest: v${v.versionName} (${v.versionCode})")
                if (v.versionCode > BuildConfig.VERSION_CODE) {
                    Text(if (v.mandatory) "Mandatory update available" else "Update available",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        val url = v.apkUrl ?: v.githubReleaseUrl
                        if (!url.isNullOrBlank()) androidContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Rounded.SystemUpdate, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open update")
                    }
                } else Text("You're up to date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { checking = true; error = null }, enabled = !checking) {
                Text(if (checking) "Checking…" else "Check for updates")
            }
            if (checking) LaunchedEffect("version-check") {
                runCatching { StoreRepository.latestVersion() }
                    .onSuccess { latest = it }
                    .onFailure { error = StoreRepository.userMessage(it) }
                checking = false
            }
        }
    if (createStaffOpen && canAdmin) {
        var staffName by remember { mutableStateOf("") }
        var staffEmail by remember { mutableStateOf("") }
        var staffPassword by remember { mutableStateOf("") }
        var staffRole by remember { mutableStateOf("cashier") }
        var roleMenuOpen by remember { mutableStateOf(false) }
        var creatingStaff by remember { mutableStateOf(false) }
        var createError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!creatingStaff) createStaffOpen = false },
            title = { Text("Create StorePOS staff") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Create a Cashier, Manager or Inventory account. The temporary password must be changed at first sign-in.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = staffName,
                        onValueChange = { staffName = it },
                        singleLine = true,
                        label = { Text("Full name") }
                    )
                    OutlinedTextField(
                        value = staffEmail,
                        onValueChange = { staffEmail = it },
                        singleLine = true,
                        label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        value = staffPassword,
                        onValueChange = { staffPassword = it },
                        singleLine = true,
                        label = { Text("Temporary password (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Box {
                        OutlinedButton(onClick = { roleMenuOpen = true }) {
                            Text("Role: " + staffRole.replaceFirstChar { it.uppercase() })
                        }
                        DropdownMenu(
                            expanded = roleMenuOpen,
                            onDismissRequest = { roleMenuOpen = false }
                        ) {
                            listOf("cashier", "manager", "inventory").forEach { next ->
                                DropdownMenuItem(
                                    text = { Text(next.replaceFirstChar { it.uppercase() }) },
                                    onClick = { staffRole = next; roleMenuOpen = false }
                                )
                            }
                        }
                    }
                    createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !creatingStaff && staffName.isNotBlank() &&
                        staffEmail.contains('@') && staffPassword.length >= 8,
                    onClick = {
                        creatingStaff = true
                        createError = null
                        scope.launch {
                            runCatching {
                                StoreRepository.createStaffAccount(
                                    context.shop.id, staffName, staffEmail, staffPassword, staffRole
                                )
                            }.onSuccess {
                                staffPassword = ""
                                createStaffOpen = false
                                refreshAdminData()
                            }.onFailure {
                                createError = StoreRepository.userMessage(it)
                            }
                            creatingStaff = false
                        }
                    }
                ) { Text(if (creatingStaff) "Creating…" else "Create staff") }
            },
            dismissButton = {
                TextButton(onClick = { createStaffOpen = false }, enabled = !creatingStaff) {
                    Text("Cancel")
                }
            }
        )
    }

    permissionMember?.let { member ->
        StaffAccessDialog(
            member = member,
            profile = profiles.firstOrNull { it.id == member.userId },
            overrides = permissionRows,
            loading = permissionLoading,
            onDismiss = {
                permissionMember = null
                permissionRows = emptyList()
            },
            onToggle = { key, allowed ->
                scope.launch {
                    permissionLoading = true
                    error = null
                    runCatching {
                        AdminRepository.setMemberPermission(
                            context.shop.id,
                            member.id,
                            key,
                            allowed
                        )
                    }.onSuccess {
                        permissionRows = AdminRepository.memberPermissions(member.id)
                    }.onFailure {
                        error = StoreRepository.userMessage(it)
                    }
                    permissionLoading = false
                }
            }
        )
    }

    if (customerDisplaySettingsOpen) {
        CustomerDisplaySettingsDialog(
            prefs = prefs,
            onDismiss = { customerDisplaySettingsOpen = false }
        )
    }

    if (receiptDesignerOpen) {
        val previewPaperWidth = prefs.getInt("paper_width", posSettings.printerPaperWidthMm)
        ReceiptDesignerDialog(
            shop = context.shop,
            settings = posSettings,
            paperWidthMm = previewPaperWidth,
            saving = savingSettings,
            onDismiss = { if (!savingSettings) receiptDesignerOpen = false },
            onSave = { updated ->
                savingSettings = true
                error = null
                scope.launch {
                    runCatching { StoreRepository.updateShopSettings(updated) }
                        .onSuccess {
                            posSettings = updated
                            receiptDesignerOpen = false
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                    savingSettings = false
                }
            }
        )
    }

    if (posSettingsOpen) {
        PosSystemSettingsDialog(
            settings = posSettings,
            saving = savingSettings,
            onDismiss = { if (!savingSettings) posSettingsOpen = false },
            onSave = { updated ->
                savingSettings = true
                error = null
                scope.launch {
                    runCatching { StoreRepository.updateShopSettings(updated) }
                        .onSuccess {
                            posSettings = updated
                            posSettingsOpen = false
                        }
                        .onFailure { error = StoreRepository.userMessage(it) }
                    savingSettings = false
                }
            }
        )
    }
    }
}


@Composable
private fun StaffPermissionRow(
    member: ShopMember,
    profile: UserProfile?,
    currentUserId: String,
    currentRole: String,
    onChange: (String, Boolean) -> Unit,
    onPermissions: () -> Unit
) {
    var menuOpen by remember(member.id) { mutableStateOf(false) }
    val role = member.role.lowercase()
    val self = member.userId == currentUserId
    val canEdit = role != "owner" && !self &&
        (currentRole.lowercase() == "owner" || (currentRole.lowercase() == "admin" && role != "admin"))

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile?.displayName ?: "Staff " + member.userId.take(8), fontWeight = FontWeight.Bold)
                Text(
                    rolePermissionSummary(role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                OutlinedButton(onClick = { menuOpen = true }, enabled = canEdit) {
                    Text(role.uppercase())
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf("manager", "cashier", "inventory").forEach { next ->
                        DropdownMenuItem(
                            text = { Text(next.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                menuOpen = false
                                onChange(next, member.isActive)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onPermissions, enabled = canEdit) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(4.dp))
                Text("Access")
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = member.isActive,
                onCheckedChange = { onChange(role, it) },
                enabled = canEdit
            )
        }
        HorizontalDivider()
    }
}

private data class StaffPermissionDefinition(
    val key: String,
    val label: String,
    val description: String
)

private val staffPermissionDefinitions = listOf(
    StaffPermissionDefinition("pos", "Point of Sale", "Open the production register and checkout."),
    StaffPermissionDefinition("inventory", "Inventory", "Products, stock adjustments, stocktake and labels."),
    StaffPermissionDefinition("retail_ops", "Retail Operations", "Returns, drawer, controls, promos and retail tools."),
    StaffPermissionDefinition("customers", "Customers", "Customer profiles, loyalty and store credit."),
    StaffPermissionDefinition("service", "Service", "Service jobs and workshop workflow."),
    StaffPermissionDefinition("quotations", "Quotations", "Create and manage quotations."),
    StaffPermissionDefinition("suppliers", "Suppliers", "Suppliers, purchase orders and payables."),
    StaffPermissionDefinition("branches", "Branches", "Multi-branch overview and stock transfers."),
    StaffPermissionDefinition("reports", "Reports", "Financial and product reports."),
    StaffPermissionDefinition("admin_center", "Admin Center", "Audit trail, sync recovery, diagnostics and backup."),
    StaffPermissionDefinition("alerts", "Alerts", "Operational and cloud attention center."),
    StaffPermissionDefinition("support", "Support", "Human StorePOS support threads."),
    StaffPermissionDefinition("settings", "Settings", "Device, printer and StorePOS configuration.")
)

@Composable
private fun StaffAccessDialog(
    member: ShopMember,
    profile: UserProfile?,
    overrides: List<MemberPermissionOverride>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Text(
                "Access • " + (profile?.displayName ?: member.role.uppercase()),
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "These switches can further restrict the staff member's role. They never grant access beyond the selected role or the shop license.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (loading && overrides.isEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(staffPermissionDefinitions.size) { index ->
                        val def = staffPermissionDefinitions[index]
                        val allowed = overrides.firstOrNull { it.permissionKey == def.key }?.allowed != false
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(def.label, fontWeight = FontWeight.Bold)
                                    Text(
                                        def.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = allowed,
                                    onCheckedChange = { onToggle(def.key, it) },
                                    enabled = !loading
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, enabled = !loading) { Text("Done") }
        }
    )
}

private fun rolePermissionSummary(role: String): String = when (role.lowercase()) {
    "owner" -> "Full business, billing, staff, device and operational access"
    "admin" -> "Full StorePOS operations, staff roles and device management"
    "manager" -> "POS, approvals, returns, reports, inventory and operations"
    "cashier" -> "POS, customers, quotations and cashier operations"
    "inventory" -> "Inventory, suppliers, stocktake, transfers and retail control"
    "mechanic" -> "Customer/service workflow and operational follow-up"
    else -> "Limited StorePOS access"
}

@Composable
private fun DeviceSessionRow(
    session: DeviceSession,
    profile: UserProfile?,
    onSetActive: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(
            if (session.isActive) Icons.Rounded.TabletAndroid else Icons.Rounded.Block,
            contentDescription = null,
            tint = if (session.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(session.deviceName ?: session.deviceId, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    session.appVersion?.let { "v$it" },
                    profile?.displayName,
                    "Last seen " + session.lastSeenAt.replace("T", " ").take(16)
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = { onSetActive(!session.isActive) }) {
            Text(if (session.isActive) "Revoke" else "Reactivate")
        }
    }
}

@Composable
private fun CustomerDisplaySettingsDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    var autoStart by remember {
        mutableStateOf(prefs.getBoolean("customer_display_auto", false))
    }
    var showBrand by remember {
        mutableStateOf(prefs.getBoolean("customer_display_show_brand", true))
    }
    var idleMessage by remember {
        mutableStateOf(
            prefs.getString("customer_display_idle_message", "Ready for your order")
                ?: "Ready for your order"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customer Display Settings", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingSwitchRow("Auto-start when a second display is connected", autoStart) {
                    autoStart = it
                }
                SettingSwitchRow("Show StorePOS branding", showBrand) {
                    showBrand = it
                }
                OutlinedTextField(
                    value = idleMessage,
                    onValueChange = { idleMessage = it.take(80) },
                    label = { Text("Idle message") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "The POS still has a Customer display button for manual on/off control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                prefs.edit()
                    .putBoolean("customer_display_auto", autoStart)
                    .putBoolean("customer_display_show_brand", showBrand)
                    .putString(
                        "customer_display_idle_message",
                        idleMessage.trim().ifBlank { "Ready for your order" }
                    )
                    .apply()
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PosSystemSettingsDialog(
    settings: ShopSettings,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShopSettings) -> Unit
) {
    var receiptHeader by remember(settings.shopId) { mutableStateOf(settings.receiptHeader.orEmpty()) }
    var receiptFooter by remember(settings.shopId) { mutableStateOf(settings.receiptFooter.orEmpty()) }
    var taxEnabled by remember(settings.shopId) { mutableStateOf(settings.taxEnabled) }
    var taxRate by remember(settings.shopId) { mutableStateOf(settings.defaultTaxRate.toString()) }
    var requireShift by remember(settings.shopId) { mutableStateOf(settings.requireCashierShift) }
    var discountLimit by remember(settings.shopId) { mutableStateOf(settings.cashierDiscountLimitPercent.toString()) }
    var pinForDiscount by remember(settings.shopId) { mutableStateOf(settings.managerPinForDiscount) }
    var allowHold by remember(settings.shopId) { mutableStateOf(settings.allowHoldSales) }
    var autoPrint by remember(settings.shopId) { mutableStateOf(settings.autoPrintReceipt) }
    var cashDrawer by remember(settings.shopId) { mutableStateOf(settings.cashDrawerEnabled) }
    var showCashier by remember(settings.shopId) { mutableStateOf(settings.receiptShowCashier) }
    var allowNegative by remember(settings.shopId) { mutableStateOf(settings.allowNegativeStock) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Production POS Settings") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Register control", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Require open cashier shift", requireShift) { requireShift = it }
                        SettingSwitchRow("Manager PIN for high cashier discounts", pinForDiscount) { pinForDiscount = it }
                        SettingSwitchRow("Allow Hold / Park Sale", allowHold) { allowHold = it }
                    }
                    item {
                        OutlinedTextField(
                            discountLimit,
                            { discountLimit = it },
                            label = { Text("Cashier discount limit (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    item {
                        HorizontalDivider()
                        Text("Tax", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Enable automatic tax", taxEnabled) { taxEnabled = it }
                    }
                    if (taxEnabled) {
                        item {
                            OutlinedTextField(
                                taxRate,
                                { taxRate = it },
                                label = { Text("Default tax rate (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    item {
                        HorizontalDivider()
                        Text("Receipt & printer", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Auto print after successful sale", autoPrint) { autoPrint = it }
                        SettingSwitchRow("Pulse cash drawer on cash sale", cashDrawer) { cashDrawer = it }
                        SettingSwitchRow("Show cashier on receipt", showCashier) { showCashier = it }
                    }
                    item {
                        OutlinedTextField(
                            receiptHeader,
                            { receiptHeader = it },
                            label = { Text("Receipt header (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        OutlinedTextField(
                            receiptFooter,
                            { receiptFooter = it },
                            label = { Text("Receipt footer") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        HorizontalDivider()
                        Text("Inventory safety", fontWeight = FontWeight.Bold)
                        SettingSwitchRow("Allow negative stock", allowNegative) { allowNegative = it }
                        Text(
                            if (allowNegative)
                                "Warning: checkout can continue when tracked stock goes below zero."
                            else
                                "Recommended: block sales that would make tracked stock negative.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allowNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val tax = taxRate.toDoubleOrNull() ?: 0.0
            val limit = discountLimit.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            receiptHeader = receiptHeader.trim().ifBlank { null },
                            receiptFooter = receiptFooter.trim().ifBlank { null },
                            taxEnabled = taxEnabled,
                            defaultTaxRate = tax.coerceIn(0.0, 100.0),
                            requireCashierShift = requireShift,
                            cashierDiscountLimitPercent = limit.coerceIn(0.0, 100.0),
                            managerPinForDiscount = pinForDiscount,
                            allowHoldSales = allowHold,
                            autoPrintReceipt = autoPrint,
                            cashDrawerEnabled = cashDrawer,
                            receiptShowCashier = showCashier,
                            allowNegativeStock = allowNegative
                        )
                    )
                },
                enabled = !saving && tax in 0.0..100.0 && limit in 0.0..100.0
            ) {
                Text(if (saving) "Saving…" else "Save settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
