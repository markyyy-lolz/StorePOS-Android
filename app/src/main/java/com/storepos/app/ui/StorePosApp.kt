package com.storepos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.storepos.app.data.AppSessionRetention
import com.storepos.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.Build
import android.provider.Settings
import com.storepos.app.BuildConfig
import com.storepos.app.data.AdminRepository
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.LicenseAccess
import com.storepos.app.data.model.PlanEntitlements
import com.storepos.app.data.model.ShopContext
import com.storepos.app.ui.auth.AuthScreen
import com.storepos.app.ui.auth.LicenseGateScreen
import com.storepos.app.ui.auth.RequiredPasswordChangeScreen
import com.storepos.app.ui.auth.SetupShopScreen
import com.storepos.app.ui.components.LoadingView
import kotlinx.coroutines.launch

enum class AppPage(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Rounded.Dashboard),
    POS("POS", Icons.Rounded.PointOfSale),
    Inventory("Inventory", Icons.Rounded.Inventory2),
    RetailOps("Retail Ops", Icons.Rounded.Hub),
    Retail("Retail Suite", Icons.Rounded.Store),
    Control("Retail Control", Icons.Rounded.AdminPanelSettings),
    Customers("Customers", Icons.Rounded.Groups),
    Service("Service", Icons.Rounded.Build),
    Quotations("Quotations", Icons.Rounded.RequestQuote),
    Suppliers("Suppliers", Icons.Rounded.LocalShipping),
    Operations("Operations", Icons.Rounded.Handyman),
    Branches("Branches", Icons.Rounded.Storefront),
    Reports("Reports", Icons.Rounded.Analytics),
    AdminCenter("Admin Center", Icons.Rounded.AdminPanelSettings),
    Alerts("Alerts", Icons.Rounded.NotificationsActive),
    Support("Support", Icons.Rounded.SupportAgent),
    Settings("Settings", Icons.Rounded.Settings)
}

internal fun restoredAppPage(stored: String?): AppPage =
    AppPage.entries.firstOrNull { it.name == stored } ?: AppPage.Dashboard

private sealed interface BootState {
    data object Loading : BootState
    data object Auth : BootState
    data class PasswordChange(val email: String?) : BootState
    data class Setup(val email: String?) : BootState
    data class LicenseGate(val context: ShopContext, val access: LicenseAccess) : BootState
    data class Ready(
        val context: ShopContext,
        val access: LicenseAccess,
        val entitlements: PlanEntitlements
    ) : BootState
}

@Composable
fun StorePosApp() {
    var state by remember { mutableStateOf<BootState>(BootState.Loading) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val deviceId = remember {
        Settings.Secure.getString(androidContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-unknown"
    }
    val deviceName = remember {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }
    }

    suspend fun restore() {
        state = BootState.Loading
        error = null
        SupabaseProvider.client.auth.awaitInitialization()
        val userId = StoreRepository.currentUserId()
        if (userId == null) {
            state = BootState.Auth
            return
        }
        if (StoreRepository.mustChangePassword()) {
            state = BootState.PasswordChange(StoreRepository.currentUserEmail())
            return
        }
        val context = StoreRepository.loadShopContext()
        if (context == null) {
            state = BootState.Setup(StoreRepository.currentUserEmail())
            return
        }

        val access = StoreRepository.validateDeviceAccess(
            shopId = context.shop.id,
            deviceId = deviceId,
            deviceName = deviceName,
            appVersion = BuildConfig.VERSION_NAME
        )
        state = if (access.valid) {
            val entitlements = StoreRepository.shopEntitlements(context.shop.id)
            BootState.Ready(context, access, entitlements)
        } else {
            BootState.LicenseGate(context, access)
        }
    }

    suspend fun restoreSafely(fallback: BootState = BootState.Auth): Boolean {
        return runCatching { restore() }.fold(
            onSuccess = { true },
            onFailure = {
                error = StoreRepository.userMessage(it)
                state = fallback
                false
            }
        )
    }

    LaunchedEffect(Unit) {
        runCatching { AppSessionRetention.enforceOnColdStart(androidContext) }
            .onSuccess { restoreSafely() }
            .onFailure {
                error = StoreRepository.userMessage(it)
                state = BootState.Auth
            }
    }

    when (val current = state) {
        BootState.Loading -> LoadingView("Starting StorePOS…")
        BootState.Auth -> AuthScreen(
            busy = busy,
            error = error,
            notice = notice,
            onSubmit = { displayName, email, password, signUp, staySignedIn, captchaToken ->
                scope.launch {
                    busy = true
                    error = null
                    notice = null

                    runCatching {
                        if (signUp) {
                            StoreRepository.signUp(displayName, email, password, captchaToken)
                            StoreRepository.currentUserId() != null
                        } else {
                            StoreRepository.signIn(email, password, captchaToken)
                            true
                        }
                    }.onSuccess { hasSession ->
                        if (hasSession) AppSessionRetention.setStaySignedIn(androidContext, staySignedIn)
                        if (signUp && !hasSession) {
                            notice = "Verification email sent. Open the email, verify your account, then sign in."
                            state = BootState.Auth
                        } else {
                            restoreSafely()
                        }
                    }.onFailure {
                        error = StoreRepository.userMessage(it)
                    }

                    busy = false
                }
            }
        )
        is BootState.PasswordChange -> RequiredPasswordChangeScreen(
            busy = busy,
            error = error,
            accountEmail = current.email,
            onSubmit = { newPassword ->
                scope.launch {
                    busy = true
                    error = null
                    notice = null
                    runCatching {
                        StoreRepository.changeRequiredPassword(newPassword)
                    }.onSuccess {
                        runCatching { StoreRepository.signOut() }
                        notice = "Password changed. Sign in again using your new StorePOS password."
                        state = BootState.Auth
                    }.onFailure {
                        error = StoreRepository.userMessage(it)
                    }
                    busy = false
                }
            },
            onSignOut = {
                scope.launch {
                    busy = true
                    runCatching { StoreRepository.signOut() }
                    error = null
                    notice = null
                    busy = false
                    state = BootState.Auth
                }
            }
        )
        is BootState.Setup -> {
            BackHandler(enabled = !busy) {
                scope.launch {
                    busy = true
                    runCatching { StoreRepository.signOut() }
                    error = null
                    notice = null
                    busy = false
                    state = BootState.Auth
                }
            }
            SetupShopScreen(
                busy = busy,
                error = error,
                accountEmail = current.email,
                onUseAnotherAccount = {
                    scope.launch {
                        busy = true
                        runCatching { StoreRepository.signOut() }
                        error = null
                        notice = null
                        busy = false
                        state = BootState.Auth
                    }
                },
                onCreate = { name, phone, address ->
                    scope.launch {
                        busy = true
                        error = null
                        runCatching {
                            StoreRepository.createFirstShop(name, phone, address)
                        }.onSuccess {
                            restoreSafely(BootState.Setup(current.email))
                        }.onFailure {
                            error = StoreRepository.userMessage(it)
                        }
                        busy = false
                    }
                }
            )
        }
        is BootState.LicenseGate -> {
            BackHandler(enabled = !busy) {
                scope.launch {
                    runCatching { StoreRepository.signOut() }
                    error = null
                    notice = null
                    state = BootState.Auth
                }
            }
            LicenseGateScreen(
            shopName = current.context.shop.name,
            access = current.access,
            busy = busy,
            error = error,
            onRetry = {
                scope.launch {
                    busy = true
                    error = null
                    restoreSafely(BootState.LicenseGate(current.context, current.access))
                    busy = false
                }
            },
            onActivate = { key ->
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        StoreRepository.activateDeviceAccess(
                            shopId = current.context.shop.id,
                            licenseKey = key,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            appVersion = BuildConfig.VERSION_NAME
                        )
                    }.onSuccess { access ->
                        if (access.valid) {
                            restoreSafely(BootState.LicenseGate(current.context, access))
                        } else {
                            state = BootState.LicenseGate(current.context, access)
                        }
                    }.onFailure {
                        error = StoreRepository.userMessage(it)
                    }
                    busy = false
                }
            },
            onSignOut = {
                scope.launch {
                    runCatching { StoreRepository.signOut() }
                    error = null
                    notice = null
                    state = BootState.Auth
                }
            }
            )
        }
        is BootState.Ready -> MainShell(
            shopContext = current.context,
            licenseAccess = current.access,
            entitlements = current.entitlements,
            onSignOut = {
                scope.launch {
                    runCatching { StoreRepository.signOut() }
                    error = null
                    notice = null
                    state = BootState.Auth
                }
            }
        )
    }
}

private fun pagesForRole(role: String): List<AppPage> = when (role.lowercase()) {
    "owner", "admin", "manager" -> AppPage.entries.filterNot {
        it in setOf(AppPage.Retail, AppPage.Control, AppPage.Operations)
    }
    "cashier" -> listOf(
        AppPage.Dashboard,
        AppPage.POS,
        AppPage.RetailOps,
        AppPage.Customers,
        AppPage.Service,
        AppPage.Quotations,
        AppPage.Alerts,
        AppPage.Support,
        AppPage.Settings
    )
    "inventory" -> listOf(
        AppPage.Dashboard,
        AppPage.Inventory,
        AppPage.RetailOps,
        AppPage.Suppliers,
        AppPage.Alerts,
        AppPage.Support,
        AppPage.Settings
    )
    "mechanic" -> listOf(
        AppPage.Dashboard,
        AppPage.Customers,
        AppPage.Service,
        AppPage.Quotations,
        AppPage.RetailOps,
        AppPage.Alerts,
        AppPage.Support,
        AppPage.Settings
    )
    else -> listOf(AppPage.Dashboard, AppPage.Alerts, AppPage.Support, AppPage.Settings)
}

private fun permissionKeyForPage(page: AppPage): String? = when (page) {
    AppPage.POS -> "pos"
    AppPage.Inventory -> "inventory"
    AppPage.RetailOps -> "retail_ops"
    AppPage.Customers -> "customers"
    AppPage.Service -> "service"
    AppPage.Quotations -> "quotations"
    AppPage.Suppliers -> "suppliers"
    AppPage.Branches -> "branches"
    AppPage.Reports -> "reports"
    AppPage.AdminCenter -> "admin_center"
    AppPage.Alerts -> "alerts"
    AppPage.Support -> "support"
    AppPage.Settings -> "settings"
    else -> null
}

private fun pageAllowedByPlan(page: AppPage, entitlements: PlanEntitlements): Boolean {
    if (page in listOf(AppPage.Dashboard, AppPage.AdminCenter, AppPage.Alerts, AppPage.Support, AppPage.Settings)) return true
    if (!entitlements.valid) return false
    val features = entitlements.features.toSet()
    return when (page) {
        AppPage.POS -> "pos" in features
        AppPage.Inventory -> "inventory" in features
        AppPage.RetailOps -> features.any { it in setOf("retail_suite", "inventory", "operations", "pos") }
        AppPage.Retail -> features.any { it in setOf("retail_suite", "inventory", "operations", "pos") }
        AppPage.Control -> features.any { it in setOf("retail_suite", "inventory", "operations", "pos") }
        AppPage.Customers -> "customers" in features
        AppPage.Service -> "service_jobs" in features
        AppPage.Quotations -> "quotations" in features
        AppPage.Suppliers -> "suppliers" in features
        AppPage.Operations -> "operations" in features
        AppPage.Branches -> "multi_branch" in features
        AppPage.Reports -> features.any { it in setOf("basic_reports","reports","advanced_reports") }
        else -> true
    }
}

@Composable
private fun MainShell(
    shopContext: ShopContext,
    licenseAccess: LicenseAccess,
    entitlements: PlanEntitlements,
    onSignOut: () -> Unit
) {
    var permissionOverrides by remember(shopContext.member.id) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }

    LaunchedEffect(shopContext.member.id) {
        permissionOverrides = if (shopContext.member.role.equals("owner", true)) {
            emptyMap()
        } else {
            runCatching {
                AdminRepository.memberPermissions(shopContext.member.id)
                    .associate { it.permissionKey to it.allowed }
            }.getOrDefault(emptyMap())
        }
    }

    val availablePages = remember(
        shopContext.member.role,
        entitlements.valid,
        entitlements.features,
        permissionOverrides
    ) {
        pagesForRole(shopContext.member.role).filter { page ->
            val roleAndPlanAllow = pageAllowedByPlan(page, entitlements)
            val key = permissionKeyForPage(page)
            val permissionAllows = key == null || permissionOverrides[key] != false
            roleAndPlanAllow && permissionAllows
        }
    }
    // Persist the destination across Activity recreation (e.g. returning
    // from the ZXing camera scanner) instead of defaulting to Dashboard.
    var pageName by rememberSaveable(shopContext.shop.id) {
        mutableStateOf(AppPage.Dashboard.name)
    }
    val page = restoredAppPage(pageName)
    var signOutConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = page != AppPage.Dashboard) {
        // Some HID barcode scanners emit a Back/Escape key after scanning.
        // POS and Inventory must never navigate away on an accidental scan key.
        // Staff can use sidebar/bottom navigation to switch pages instead.
        if (page != AppPage.POS && page != AppPage.Inventory) {
            pageName = AppPage.Dashboard.name
        }
    }

    LaunchedEffect(availablePages) {
        if (page !in availablePages) pageName = AppPage.Dashboard.name
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    shopName = shopContext.shop.name,
                    role = shopContext.member.role,
                    pages = availablePages,
                    selected = page,
                    onSelect = { pageName = it.name },
                    onSignOut = { signOutConfirm = true }
                )
                PageContent(
                    page = page,
                    context = shopContext,
                    licenseAccess = licenseAccess,
                    entitlements = entitlements,
                    onNavigate = { target -> if (target in availablePages) pageName = target.name },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    MobileNav(
                        pages = availablePages,
                        selected = page,
                        onSelect = { page = it },
                        onSignOut = { signOutConfirm = true }
                    )
                }
            ) { padding ->
                PageContent(
                    page = page,
                    context = shopContext,
                    licenseAccess = licenseAccess,
                    entitlements = entitlements,
                    onNavigate = { target -> if (target in availablePages) page = target },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            icon = { Icon(Icons.Rounded.Logout, contentDescription = null) },
            title = { Text("Sign out of StorePOS?") },
            text = { Text("You will return to the sign-in screen. This only signs out this device session.") },
            confirmButton = {
                Button(
                    onClick = {
                        signOutConfirm = false
                        onSignOut()
                    }
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { signOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun Sidebar(
    shopName: String,
    role: String,
    pages: List<AppPage>,
    selected: AppPage,
    onSelect: (AppPage) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(238.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Storefront, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("StorePOS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text(
                    shopName,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            pages.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label) },
                    selected = item == selected,
                    icon = { Icon(item.icon, null) },
                    onClick = { onSelect(item) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(
            role.replaceFirstChar { it.uppercase() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        NavigationDrawerItem(
            label = { Text("Sign out") },
            selected = false,
            icon = { Icon(Icons.Rounded.Logout, null) },
            onClick = onSignOut
        )
    }
}

@Composable
private fun MobileNav(
    pages: List<AppPage>,
    selected: AppPage,
    onSelect: (AppPage) -> Unit,
    onSignOut: () -> Unit
) {
    val preferredPrimary = listOf(AppPage.Dashboard, AppPage.POS, AppPage.Inventory, AppPage.Service)
    val primary = preferredPrimary.filter { it in pages }.take(4)
    val remaining = pages.filter { it !in primary }
    var moreOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        NavigationBar(Modifier.fillMaxWidth()) {
            primary.forEach { item ->
                NavigationBarItem(
                    selected = selected == item,
                    onClick = { onSelect(item) },
                    icon = { Icon(item.icon, null) },
                    label = { Text(item.label) }
                )
            }

            if (remaining.isNotEmpty()) {
                NavigationBarItem(
                    selected = selected in remaining,
                    onClick = { moreOpen = true },
                    icon = { Icon(Icons.Rounded.MoreHoriz, null) },
                    label = { Text("More") }
                )
            }
        }

        DropdownMenu(
            expanded = moreOpen,
            onDismissRequest = { moreOpen = false },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            remaining.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = { Icon(item.icon, null) },
                    onClick = {
                        onSelect(item)
                        moreOpen = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Sign out") },
                leadingIcon = { Icon(Icons.Rounded.Logout, contentDescription = null) },
                onClick = {
                    moreOpen = false
                    onSignOut()
                }
            )
        }
    }
}

@Composable
private fun PageContent(
    page: AppPage,
    context: ShopContext,
    licenseAccess: LicenseAccess,
    entitlements: PlanEntitlements,
    onNavigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        if (licenseAccess.trial) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("StorePOS Pro Trial", fontWeight = FontWeight.Bold)
                        Text(
                            licenseAccess.message ?: "7-day Pro Trial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${licenseAccess.daysRemaining ?: 0} day${if (licenseAccess.daysRemaining == 1) "" else "s"} left",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                AppPage.Dashboard -> com.storepos.app.ui.screens.DashboardPage(
                    context = context,
                    entitlements = entitlements,
                    onOpenPos = { onNavigate(AppPage.POS) },
                    onOpenInventory = { onNavigate(AppPage.Inventory) },
                    onOpenService = { onNavigate(AppPage.Service) },
                    onOpenQuotations = { onNavigate(AppPage.Quotations) },
                    onOpenOperations = { onNavigate(AppPage.RetailOps) }
                )
                AppPage.POS -> com.storepos.app.ui.screens.PosPage(context, entitlements)
                AppPage.Inventory -> com.storepos.app.ui.screens.InventoryPage(context)
                AppPage.RetailOps -> com.storepos.app.ui.screens.RetailOperationsPage(context)
                AppPage.Retail -> com.storepos.app.ui.screens.RetailSuitePage(context)
                AppPage.Control -> com.storepos.app.ui.screens.RetailControlPage(context)
                AppPage.Customers -> com.storepos.app.ui.screens.CustomersPage(context)
                AppPage.Service -> com.storepos.app.ui.screens.ServicePage(context)
                AppPage.Quotations -> com.storepos.app.ui.screens.QuotationsPage(context)
                AppPage.Suppliers -> com.storepos.app.ui.screens.SuppliersPage(context)
                AppPage.Operations -> com.storepos.app.ui.screens.OperationsPage(context)
                AppPage.Branches -> com.storepos.app.ui.screens.BranchesPage(context)
                AppPage.Reports -> com.storepos.app.ui.screens.ReportsPage(context)
                AppPage.AdminCenter -> com.storepos.app.ui.screens.AdminCenterPage(context)
                AppPage.Alerts -> com.storepos.app.ui.screens.AlertsPage(
                    context = context,
                    onNavigate = { target ->
                        val next = when (target) {
                            "inventory" -> AppPage.Inventory
                            "service" -> AppPage.Service
                            "operations" -> AppPage.RetailOps
                            "support" -> AppPage.Support
                            else -> AppPage.Dashboard
                        }
                        if (next in pagesForRole(context.member.role)) onNavigate(next)
                    }
                )
                AppPage.Support -> com.storepos.app.ui.screens.SupportPage(context)
                AppPage.Settings -> com.storepos.app.ui.screens.SettingsPage(context)
            }
        }
    }
}
