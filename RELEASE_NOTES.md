# StorePOS v1.3.4

StorePOS v1.3.4 fixes the blank Cloudflare Turnstile area on Android by loading a dedicated hosted verification page from the StorePOS domain.

## Android Turnstile Rendering Fix
- Added a dedicated hosted Turnstile page on StorePOS Cloud for Android authentication.
- Android now loads the real StorePOS HTTPS origin instead of injecting an inline HTML challenge.
- Fixes the blank security-verification area seen in v1.3.3.
- Successful Turnstile verification sends the token directly back to Android.
- Sign in / Create account becomes available after a valid token is received.
- Expired, timed-out, or failed verification clears the token and requires a fresh challenge.
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
- Version code: 9
- Version name: `1.3.4`
- Existing shops, users, products, licenses and transaction data are preserved.
