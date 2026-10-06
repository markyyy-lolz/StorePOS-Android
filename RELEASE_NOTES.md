# StorePOS v1.3.2

StorePOS v1.3.2 fixes Android sign-in and sign-up when Supabase CAPTCHA protection is enabled.

## Android Authentication Fix
- Added Cloudflare Turnstile verification directly to the StorePOS Android login and sign-up screen.
- Verified Turnstile tokens are now submitted together with email/password authentication.
- Fixes the previous `captcha_failed` error on Android when Supabase CAPTCHA protection is enabled.
- CAPTCHA protection remains enabled on the backend and StorePOS Cloud website.
- Expired or failed security checks are refreshed automatically before another authentication attempt.
- Raw CAPTCHA errors are replaced with a clearer StorePOS security-verification message.
- StorePOS sign-up confirmation redirects now use the production StorePOS Cloud URL.

## Existing StorePOS Features
- Native QR Ph payments with automatic PayMongo verification.
- Per-client PayMongo merchant integration.
- Retail Control Center, inventory, barcode scanning, reports, supplier management and cashier operations.
- Bluetooth and USB receipt printing.
- Offline sales, role-based staff access and StorePOS Cloud management.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 7
- Version name: `1.3.2`
- Existing shop data, users, products, licenses and transactions are preserved.
