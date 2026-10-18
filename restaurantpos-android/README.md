# RestaurantPOS Native Android v0.2.0

Native Java Android app (no WebView UI), independent package `com.azurate.restaurantpos`. Development branch only; StorePOS production app unchanged.

## Features
- Native touchscreen dashboard, dine-in/takeout menu POS, kitchen queue, orders, printer settings, and device/cloud status.
- SQLite-backed menu cache, order outbox, printable kitchen-ticket queue, client-request UUID deduplication.
- Short-lived (8h) offline staff re-authentication after successful online login; encrypted at rest is NOT yet implemented.
- Cloud REST sync to existing RestaurantPOS Node API and separate Neon PostgreSQL project. Server calculates live prices; mismatches are marked conflicts and payments are withheld.
- Configurable multiple ESC/POS printers: TCP LAN/Wi-Fi port 9100, paired Bluetooth SPP, and USB bulk-out with Android permission prompt. Output routes separately to receipt vs kitchen; can send to multiple per role.
- Cash acknowledgment via local provisional slip; separately labeled non-BIR, not an official invoice.
- PayMongo checkout entry from native orders, **requires separate updated backend** and merchant credentials. The app never embeds payment secret keys.

## Important limitations
- Real offline inventory reconciliation, refunds, split bills, secure hardware key storage, hardware printer certification, end-to-end production acceptance, and cloud deployment remain pending.
- Most ESC/POS transports cannot confirm paper output: each job gets only one automatic attempt; uncertain delivery requires a conscious reprint.
- Android app should be tested with actual selected thermal models, LAN isolation policy, and production HTTPS domain before restaurant usage.
- Do not uninstall or clear app data while offline outbox contains unsynced orders.

## Build
JDK 17, Android SDK 36, Gradle 8.11.1: `gradle :app:assembleDebug`
Signed debug APK is auto-built in GitHub Actions from the isolated branch.
