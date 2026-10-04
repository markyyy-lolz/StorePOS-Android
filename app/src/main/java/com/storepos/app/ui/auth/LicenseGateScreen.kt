package com.storepos.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.storepos.app.data.model.LicenseAccess

@Composable
fun LicenseGateScreen(
    shopName: String,
    access: LicenseAccess,
    busy: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onActivate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var licenseKey by remember(access.status, access.requiresActivation) { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (access.requiresActivation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (access.requiresActivation) Icons.Rounded.Key else Icons.Rounded.LockClock,
                            contentDescription = null,
                            tint = if (access.requiresActivation) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text(
                    if (access.requiresActivation) "Activate this StorePOS device"
                    else when (access.status.lowercase()) {
                        "expired" -> "StorePOS access expired"
                        "suspended" -> "StorePOS license suspended"
                        else -> "StorePOS license required"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )

                Text(shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    access.message ?: "Check the shop license from the StorePOS Control Center.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (access.trial && access.expiresAt != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("7-day Pro Trial", fontWeight = FontWeight.Bold)
                            Text(
                                "Trial period: ${access.startsAt ?: "—"} → ${access.expiresAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (access.requiresActivation) {
                    OutlinedTextField(
                        value = licenseKey,
                        onValueChange = { licenseKey = it.uppercase() },
                        label = { Text("StorePOS license key") },
                        placeholder = { Text("MTP-PRO-XXXX-XXXX-XXXX") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Button(
                        onClick = { onActivate(licenseKey) },
                        enabled = !busy && licenseKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Activate device", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = onRetry,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check license again")
                }

                TextButton(
                    onClick = onSignOut,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Rounded.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}
