package com.storepos.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.model.ShopContext
import com.storepos.app.printing.ReceiptCutSettings
import com.storepos.app.printing.BluetoothReceiptPrinter
import kotlin.math.roundToInt
import com.storepos.app.printing.PrinterDevice
import com.storepos.app.printing.ReceiptPrinter
import com.storepos.app.printing.UsbReceiptPrinter
import com.storepos.app.ui.components.MotoCard
import kotlinx.coroutines.launch

@Composable
fun PrinterSettingsCard(context: ShopContext) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { androidContext.getSharedPreferences("motopos_settings", 0) }

    var transport by remember {
        mutableStateOf(prefs.getString("printer_transport", "bluetooth") ?: "bluetooth")
    }
    var address by remember { mutableStateOf(prefs.getString("printer_address", null)) }
    var name by remember { mutableStateOf(prefs.getString("printer_name", null)) }
    var width by remember { mutableIntStateOf(prefs.getInt("paper_width", 80)) }
    var footerFeedLines by remember {
        mutableIntStateOf(ReceiptCutSettings.get(androidContext))
    }
    var devices by remember { mutableStateOf<List<PrinterDevice>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun bluetoothPermitted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            androidContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun reload() {
        devices = when (transport) {
            "usb" -> runCatching { UsbReceiptPrinter.usbPrinters(androidContext) }.getOrDefault(emptyList())
            else -> if (bluetoothPermitted()) {
                runCatching { BluetoothReceiptPrinter.bondedPrinters(androidContext) }.getOrDefault(emptyList())
            } else emptyList()
        }
    }

    fun setTransport(value: String) {
        if (transport == value) return
        transport = value
        address = null
        name = null
        prefs.edit()
            .putString("printer_transport", value)
            .remove("printer_address")
            .remove("printer_name")
            .apply()
        message = null
        reload()
    }

    fun select(p: PrinterDevice) {
        address = p.address
        name = p.name
        prefs.edit()
            .putString("printer_transport", transport)
            .putString("printer_address", p.address)
            .putString("printer_name", p.name)
            .apply()
    }

    val bluetoothPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            message = if (granted) {
                "Bluetooth permission granted."
            } else {
                "Bluetooth permission is required for Bluetooth printing."
            }
            reload()
        }

    LaunchedEffect(transport) { reload() }

    MotoCard(Modifier.fillMaxWidth()) {
        Text("Receipt printer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            name?.let { "$it • ${transport.uppercase()}" } ?: "No printer selected",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = transport == "bluetooth",
                onClick = { setTransport("bluetooth") },
                label = { Text("Bluetooth") },
                leadingIcon = { Icon(Icons.Rounded.Bluetooth, null) }
            )
            FilterChip(
                selected = transport == "usb",
                onClick = { setTransport("usb") },
                label = { Text("USB") },
                leadingIcon = { Icon(Icons.Rounded.Usb, null) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                width == 80,
                { width = 80; prefs.edit().putInt("paper_width", 80).apply() },
                { Text("80mm") }
            )
            FilterChip(
                width == 58,
                { width = 58; prefs.edit().putInt("paper_width", 58).apply() },
                { Text("58mm") }
            )
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Footer-to-Cut Spacing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Blank paper after Powered by StorePOS, before the automatic full cut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "$footerFeedLines lines",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = footerFeedLines.toFloat(),
            onValueChange = { value ->
                footerFeedLines = ReceiptCutSettings.normalize(value.roundToInt())
                prefs.edit().putInt(ReceiptCutSettings.PREF_KEY, footerFeedLines).apply()
            },
            valueRange = 0f..ReceiptCutSettings.MAX_LINES.toFloat(),
            steps = ReceiptCutSettings.MAX_LINES - 1,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "0 = closest cut, 8 = longest feed. Default is 2. " +
                "The physical cutter may need extra clearance; use Test print to check. " +
                "This setting applies to new receipts and queued print jobs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (transport == "bluetooth") {
            if (!bluetoothPermitted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Button(onClick = {
                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }) {
                    Icon(Icons.Rounded.Bluetooth, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Allow Bluetooth")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        androidContext.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) {
                        Text("Pair device")
                    }
                    OutlinedButton(onClick = { reload() }) { Text("Refresh") }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { reload() }) {
                    Icon(Icons.Rounded.Usb, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh USB")
                }
                if (address != null && !UsbReceiptPrinter.hasPermission(androidContext, address!!)) {
                    Button(onClick = {
                        val selected = address ?: return@Button
                        val alreadyGranted = UsbReceiptPrinter.requestPermission(androidContext, selected)
                        message = if (alreadyGranted) {
                            "USB permission already granted."
                        } else {
                            "Approve the Android USB permission dialog, then tap Test print."
                        }
                    }) {
                        Text("Allow USB")
                    }
                }
            }
        }

        if (devices.isEmpty()) {
            Text(
                if (transport == "usb")
                    "Connect a USB ESC/POS printer or USB-OTG adapter, then tap Refresh USB."
                else
                    "Pair the Xprinter/ESC-POS printer in Android Bluetooth settings, then tap Refresh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            devices.forEach { printer ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { select(printer) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(address == printer.address, { select(printer) })
                    Column {
                        Text(printer.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (printer.transport == "usb") "USB device" else printer.address,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val selectedAddress = address ?: return@Button
                if (transport == "usb" &&
                    !UsbReceiptPrinter.hasPermission(androidContext, selectedAddress)
                ) {
                    UsbReceiptPrinter.requestPermission(androidContext, selectedAddress)
                    message = "Approve USB access, then tap Test print again."
                    return@Button
                }

                testing = true
                message = null
                scope.launch {
                    val device = PrinterDevice(
                        name = name ?: "Receipt printer",
                        address = selectedAddress,
                        transport = transport
                    )
                    val printer: ReceiptPrinter = if (transport == "usb") {
                        UsbReceiptPrinter(androidContext)
                    } else {
                        BluetoothReceiptPrinter(androidContext)
                    }
                    val result = printer.connect(device).fold(
                        onSuccess = {
                            printer.printReceipt(
                                BluetoothReceiptPrinter.testReceipt(context.shop.name, width, footerFeedLines)
                            )
                        },
                        onFailure = { Result.failure(it) }
                    )
                    printer.disconnect()
                    message = result.fold(
                        { "Test receipt sent successfully." },
                        { it.message ?: "Unable to print." }
                    )
                    testing = false
                }
            },
            enabled = address != null &&
                (transport == "usb" || bluetoothPermitted()) &&
                !testing
        ) {
            Icon(Icons.Rounded.Print, null)
            Spacer(Modifier.width(6.dp))
            Text(if (testing) "Printing…" else "Test print")
        }

        message?.let {
            Text(
                it,
                color = if (it.contains("success", true))
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
