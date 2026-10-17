# StorePOS Android v1.8.7 — Trusted Cloud Products Available Offline

**Build 39 • Updated from v1.8.6 (build 38), package `com.storepos.app`.**

## What's new
- **StorePOS Cloud → SQLite:** fetches the complete current shop's Supabase product catalog in 500-item pages and atomically saves a trusted SQLite snapshot with the catalog reset/epoch marker.
- **Offline catalog immediately:** after one successful online catalog sync, the POS loads saved products, barcodes, prices and stock when the screen opens, without waiting for internet requests.
- **Independent cloud product caching:** a temporary error in customers, cashier shifts, settings, serials, favorites or promotions cannot prevent a successful catalog download being cached.
- **Automatic refresh:** updates catalog while idle approximately every two minutes. Manual **Sync Products** is available in the POS screen, with last successful cloud sync time and local product count.
- **Protected local quantities:** cloud refresh subtracts unreconciled cash sales belonging to the current shop **and the current catalog generation**, without changing non-stock-tracked items.

## Keep existing v1.8.6 safety protections
- All existing **SQLite schema v4 migrations**, trusted shop epochs, reset safety, manager-review queues, offline inventory movements, cloud idempotent cash reconciliation, local receipt bytes/PDF, PayMongo restrictions and 80mm/58mm Azurate footer remain.
- A tablet with no trusted online snapshot **must not sell offline**. Offline cash payments still require the existing role/device/shift checks.
- Cloud catalog replacement is refused while there are unsynced or needs-review offline inventory operations, to protect stock changes.
- This APK does not alter the Supabase schema or delete existing shop products, sales, receipts, outboxes or client accounts. MotoPOS and BrewPOS are unaffected.

## Safe install & offline acceptance test
1. Install v1.8.7 **over v1.8.6**. **Do not uninstall, clear app data or reset the tablet.**
2. With internet enabled, open **POS → Sync Products** on *each* Samsung Galaxy Tab A9+.
3. Verify the saved product count and timestamp, scan a barcode, and check the expected price and quantity.
4. Turn off internet and reopen POS. Products and scanning should work from SQLite.
5. Make an authorized small cash transaction and verify local receipt printing, queued status and idempotent cloud reconciliation after reconnect.
6. Test two tablets separately; on-device stock is provisional while another terminal remains offline.

## Verification
- Build/test/lint in GitHub Actions before distribution. Validate the real devices, direct and shared VOZY G80 printer, and live Supabase policies before using as production.
- Keep an up-to-date offline database backup before upgrading any production terminal.
