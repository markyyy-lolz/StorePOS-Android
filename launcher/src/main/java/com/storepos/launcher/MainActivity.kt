package com.storepos.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.storepos.launcher.ui.theme.StorePosLauncherTheme

class MainActivity : ComponentActivity() {
    private lateinit var prefs: LauncherPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)

        if (prefs.kioskEnabled && KioskController.isDeviceOwner(this)) {
            KioskController.applyKiosk(this, true)
        }

        setContent {
            StorePosLauncherTheme {
                StorePosLauncherApp()
            }
        }
    }
}

private enum class LauncherPage { Home, Settings }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorePosLauncherApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val prefs = remember { LauncherPrefs(context) }

    var page by remember { mutableStateOf(LauncherPage.Home) }
    var hasPin by remember { mutableStateOf(prefs.hasAdminPin()) }
    var showAdminPin by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var kioskEnabled by remember { mutableStateOf(prefs.kioskEnabled) }
    var autoOpen by remember { mutableStateOf(prefs.autoOpenStorePos) }
    var storePosInstalled by remember { mutableStateOf(KioskController.isStorePosInstalled(context)) }

    LaunchedEffect(Unit) {
        storePosInstalled = KioskController.isStorePosInstalled(context)
        if (hasPin && autoOpen && storePosInstalled) {
            KioskController.openStorePos(context)
        }
    }

    if (!hasPin) {
        PinSetupScreen(
            onSet = { pin ->
                prefs.setAdminPin(pin)
                hasPin = true
            }
        )
        return
    }

    when (page) {
        LauncherPage.Home -> LauncherHome(
            storePosInstalled = storePosInstalled,
            kioskEnabled = kioskEnabled,
            deviceOwner = KioskController.isDeviceOwner(context),
            onStorePos = {
                if (!KioskController.openStorePos(context)) {
                    Toast.makeText(context, "StorePOS is not installed on this device.", Toast.LENGTH_LONG).show()
                    storePosInstalled = false
                }
            },
            onSettings = { page = LauncherPage.Settings },
            onAdminLongPress = { showAdminPin = true }
        )
        LauncherPage.Settings -> RestrictedSettingsScreen(
            onBack = { page = LauncherPage.Home }
        )
    }

    if (showAdminPin) {
        PinVerifyDialog(
            onDismiss = { showAdminPin = false },
            onVerify = { pin ->
                if (prefs.verifyAdminPin(pin)) {
                    showAdminPin = false
                    showAdminPanel = true
                    true
                } else false
            }
        )
    }

    if (showAdminPanel) {
        AdminPanelDialog(
            deviceOwner = KioskController.isDeviceOwner(context),
            kioskEnabled = kioskEnabled,
            autoOpen = autoOpen,
            onDismiss = { showAdminPanel = false },
            onSetDefaultLauncher = { KioskController.openHomeSettings(context) },
            onOpenAndroidSettings = { KioskController.openSystemSettings(context) },
            onKioskChanged = { enabled ->
                val applied = KioskController.applyKiosk(activity, enabled)
                if (applied) {
                    kioskEnabled = enabled
                    prefs.kioskEnabled = enabled
                } else {
                    Toast.makeText(
                        context,
                        "Device Owner is required for full kiosk mode.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onAutoOpenChanged = {
                autoOpen = it
                prefs.autoOpenStorePos = it
            },
            onChangePin = { showChangePin = true }
        )
    }

    if (showChangePin) {
        PinSetupDialog(
            title = "Change admin PIN",
            onDismiss = { showChangePin = false },
            onSet = { pin ->
                prefs.setAdminPin(pin)
                showChangePin = false
                Toast.makeText(context, "Admin PIN updated.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherHome(
    storePosInstalled: Boolean,
    kioskEnabled: Boolean,
    deviceOwner: Boolean,
    onStorePos: () -> Unit,
    onSettings: () -> Unit,
    onAdminLongPress: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Image(
                painter = painterResource(R.drawable.storepos_brand_logo),
                contentDescription = "StorePOS Terminal",
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .height(132.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onAdminLongPress
                    ),
                contentScale = ContentScale.Fit
            )

            Text(
                "TERMINAL LAUNCHER",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Dedicated retail device",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Row(
                Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                LauncherTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PointOfSale,
                    title = "StorePOS",
                    subtitle = if (storePosInstalled) "Open register" else "Not installed",
                    enabled = storePosInstalled,
                    onClick = onStorePos
                )
                LauncherTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Settings,
                    title = "Settings",
                    subtitle = "Device setup",
                    enabled = true,
                    onClick = onSettings
                )
            }

            Spacer(Modifier.height(26.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusPill(
                        if (storePosInstalled) "StorePOS ready" else "StorePOS missing",
                        storePosInstalled
                    )
                    StatusPill(
                        if (kioskEnabled) "Kiosk locked" else if (deviceOwner) "Kiosk ready" else "Standard mode",
                        kioskEnabled || deviceOwner
                    )
                }
            }

            Spacer(Modifier.height(44.dp))
            Text(
                "Long-press the StorePOS logo for administrator access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
            Text(
                "Powered by StorePOS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LauncherTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(210.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RestrictedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(6.dp))
            Column {
                Text("Device Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Only essential terminal settings are shown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SettingsAction(Icons.Rounded.Wifi, "Wi-Fi", "Connect the terminal to a network") {
            launchSetting(context, Settings.ACTION_WIFI_SETTINGS)
        }
        SettingsAction(Icons.Rounded.Bluetooth, "Bluetooth", "Pair a receipt printer or accessory") {
            launchSetting(context, Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        SettingsAction(Icons.Rounded.DisplaySettings, "Display", "Brightness and screen settings") {
            launchSetting(context, Settings.ACTION_DISPLAY_SETTINGS)
        }
        SettingsAction(Icons.Rounded.VolumeUp, "Sound", "Volume and notification sound") {
            launchSetting(context, Settings.ACTION_SOUND_SETTINGS)
        }
        SettingsAction(Icons.Rounded.Schedule, "Date & time", "Set terminal date, time and timezone") {
            launchSetting(context, Settings.ACTION_DATE_SETTINGS)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Full Android Settings is available only from Administrator Access.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

private fun launchSetting(context: android.content.Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "This setting is not available on this device.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AdminPanelDialog(
    deviceOwner: Boolean,
    kioskEnabled: Boolean,
    autoOpen: Boolean,
    onDismiss: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onKioskChanged: (Boolean) -> Unit,
    onAutoOpenChanged: (Boolean) -> Unit,
    onChangePin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Administrator Access", fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusPill(
                    if (deviceOwner) "Device Owner active" else "Device Owner not provisioned",
                    deviceOwner
                )

                HorizontalDivider()

                SettingToggle(
                    title = "Kiosk lock",
                    subtitle = if (deviceOwner)
                        "Locks the terminal to approved StorePOS apps."
                    else
                        "Provision Device Owner first to enable full kiosk mode.",
                    checked = kioskEnabled,
                    enabled = deviceOwner,
                    onCheckedChange = onKioskChanged
                )

                SettingToggle(
                    title = "Auto-open StorePOS",
                    subtitle = "Open StorePOS when the launcher starts.",
                    checked = autoOpen,
                    enabled = true,
                    onCheckedChange = onAutoOpenChanged
                )

                HorizontalDivider()

                FilledTonalButton(onClick = onSetDefaultLauncher, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Home, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Set as default Home app")
                }

                FilledTonalButton(onClick = onOpenAndroidSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AdminPanelSettings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open full Android Settings")
                }

                OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Password, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Change admin PIN")
                }

                if (!deviceOwner) {
                    HorizontalDivider()
                    Text("Full kiosk provisioning", fontWeight = FontWeight.Bold)
                    Text(
                        "On a freshly reset device, connect ADB and run:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            "adb shell dpm set-device-owner com.storepos.launcher/.admin.StorePosDeviceAdminReceiver",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun PinSetupScreen(onSet: (String) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            shape = RoundedCornerShape(30.dp)
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(R.drawable.storepos_brand_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    contentScale = ContentScale.Fit
                )
                Text("Set administrator PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "This PIN protects kiosk settings and full Android access.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PinSetupFields(onSet)
            }
        }
    }
}

@Composable
private fun PinSetupDialog(
    title: String,
    onDismiss: () -> Unit,
    onSet: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PinSetupFields(onSet) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PinSetupFields(onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin.all(Char::isDigit) && pin == confirm

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = { Text("Admin PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it.filter(Char::isDigit).take(8) },
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Use 4–8 digits.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { onSet(pin) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save admin PIN")
        }
    }
}

@Composable
private fun PinVerifyDialog(
    onDismiss: () -> Unit,
    onVerify: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Administrator PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(8)
                        error = false
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                if (error) {
                    Text("Incorrect administrator PIN.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = !onVerify(pin) },
                enabled = pin.length >= 4
            ) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
