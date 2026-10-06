package com.storepos.app.ui.auth

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val TURNSTILE_PAGE_URL = "https://storepos.2023107337.workers.dev/turnstile.html"

private class TurnstileBridge(
    private val onToken: (String) -> Unit,
    private val onExpired: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onToken(token: String) {
        if (token.isBlank()) return
        mainHandler.post { onToken(token) }
    }

    @JavascriptInterface
    fun onExpired() {
        mainHandler.post(onExpired)
    }

    @JavascriptInterface
    fun onError(message: String) {
        mainHandler.post { onError(message) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileChallenge(
    signUp: Boolean,
    refreshKey: Int,
    enabled: Boolean,
    onToken: (String) -> Unit,
    onExpired: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface("StorePosCaptcha")
                destroy()
            }
            webView = null
        }
    }

    key(signUp, refreshKey) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                factory = {
                    WebView(context).apply {
                        webView = this
                        setBackgroundColor(Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(
                            TurnstileBridge(
                                onToken = onToken,
                                onExpired = onExpired,
                                onError = onError
                            ),
                            "StorePosCaptcha"
                        )

                        webViewClient = WebViewClient()

                        val mode = if (signUp) "signup" else "signin"
                        loadUrl("$TURNSTILE_PAGE_URL?mode=$mode&v=2")
                    }
                },
                update = { view ->
                    view.isEnabled = enabled
                    view.alpha = if (enabled) 1f else 0.72f
                    view.setBackgroundColor(surfaceColor and 0x00FFFFFF)
                }
            )
        }
    }
}

@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    notice: String?,
    onSubmit: (
        displayName: String,
        email: String,
        password: String,
        signUp: Boolean,
        captchaToken: String
    ) -> Unit
) {
    var signUp by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signUpCooldown by remember { mutableIntStateOf(0) }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    var challengeRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(signUpCooldown) {
        if (signUpCooldown > 0) {
            delay(1000)
            signUpCooldown -= 1
        }
    }

    LaunchedEffect(error, notice) {
        if (!error.isNullOrBlank() || !notice.isNullOrBlank()) {
            captchaToken = null
            challengeRefreshKey += 1
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text("StorePOS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(
                    if (signUp) "Create the owner account for your retail store."
                    else "Sign in to manage sales, inventory and your retail store.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (signUp) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                if (signUp) {
                    Text(
                        "Use at least 8 characters for new accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Security verification",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    TurnstileChallenge(
                        signUp = signUp,
                        refreshKey = challengeRefreshKey,
                        enabled = !busy,
                        onToken = { token -> captchaToken = token },
                        onExpired = { captchaToken = null },
                        onError = {
                            captchaToken = null
                            challengeRefreshKey += 1
                        }
                    )
                    Text(
                        if (captchaToken.isNullOrBlank())
                            "Complete the Cloudflare check before continuing."
                        else
                            "Security check complete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (captchaToken.isNullOrBlank())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }

                if (!notice.isNullOrBlank()) {
                    Text(
                        notice,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val token = captchaToken ?: return@Button
                        if (signUp) signUpCooldown = 60
                        captchaToken = null
                        challengeRefreshKey += 1
                        onSubmit(displayName, email, password, signUp, token)
                    },
                    enabled = !busy && !captchaToken.isNullOrBlank() && email.isNotBlank() &&
                        password.length >= (if (signUp) 8 else 6) &&
                        (!signUp || (displayName.isNotBlank() && signUpCooldown == 0)),
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
                        Text(
                            if (signUp && signUpCooldown > 0) "Try again in ${signUpCooldown}s"
                            else if (signUp) "Create account"
                            else "Sign in",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = {
                        signUp = !signUp
                        password = ""
                        captchaToken = null
                        challengeRefreshKey += 1
                    },
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (signUp) "Already have an account? Sign in" else "New shop? Create owner account")
                }
            }
        }
    }
}

@Composable
fun SetupShopScreen(
    busy: Boolean,
    error: String?,
    accountEmail: String?,
    onUseAnotherAccount: () -> Unit,
    onCreate: (name: String, phone: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Finish setting up your shop", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your owner account is already signed in. Create the shop workspace that will belong to this account.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Signed-in owner", fontWeight = FontWeight.SemiBold)
                            Text(
                                accountEmail ?: "Authenticated StorePOS account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Shop name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp)
                )

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { onCreate(name, phone, address) },
                    enabled = !busy && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Create StorePOS workspace", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onUseAnotherAccount,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use another account")
                }
            }
        }
    }
}
