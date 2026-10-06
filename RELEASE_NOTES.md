# StorePOS v1.3.5

StorePOS v1.3.5 fixes the final Android Turnstile handoff issue where Cloudflare verification could show Success but the Sign in button remained disabled.

## Android Turnstile Token Handoff Fix
- Hosted StorePOS Turnstile now redirects through a StorePOS callback URL after successful verification.
- Android WebView intercepts the callback URL and reads the verified CAPTCHA token directly.
- Removes reliance on the JavaScript bridge for enabling the Sign in button.
- Sign in / Create account becomes available immediately after a valid token is received.
- Expired, timed-out, or failed verification still requires a fresh challenge.
- Supabase CAPTCHA protection remains enabled.

## Existing StorePOS Features
- Native QR Ph payments with automatic PayMongo verification.
- Per-client PayMongo merchant integration.
- Retail Control Center, inventory, barcode scanning, suppliers, cashier operations and reports.
- Bluetooth and USB receipt printing.
- Offline sales, staff roles and StorePOS Cloud management.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 10
- Version name: `1.3.5`
- Existing shops, users, products, licenses and transaction data are preserved.
