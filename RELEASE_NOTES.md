# StorePOS v1.3.3

StorePOS v1.3.3 fixes the Android Turnstile completion bridge so the Sign in button becomes available immediately after successful Cloudflare verification.

## Android Turnstile Callback Fix
- Replaced the previous website-token polling workaround with a dedicated Turnstile WebView challenge.
- Turnstile's success callback now sends the token directly to the Android bridge.
- The Sign in / Create account button becomes enabled as soon as verification succeeds.
- Expired or failed verification clears the token correctly and requires a fresh challenge.
- Removes the unwanted StorePOS/GitHub website content that appeared below the CAPTCHA widget.
- Keeps Supabase CAPTCHA protection enabled and continues sending the verified token with authentication.

## Existing StorePOS Features
- Native QR Ph payments with automatic PayMongo verification.
- Per-client PayMongo merchant integration.
- Retail Control Center, barcode scanning, inventory, suppliers, cashier operations and reports.
- Bluetooth and USB thermal receipt printing.
- Offline sales, staff roles and StorePOS Cloud management.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 8
- Version name: `1.3.3`
- Existing shops, users, products, licenses and transaction data are preserved.
