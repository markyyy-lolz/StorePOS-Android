# StorePOS Android v1.7.5 — Offline Cloud Product Catalog

StorePOS now saves the entire StorePOS shop's Supabase product catalog to on-device SQLite, so cashiers can browse products, scan barcodes, and prepare supported cash checkouts when the internet is unavailable.

## What's new
- **Cloud to SQLite catalog sync:** downloads all products for the active shop, in pages of 500 to support grocery stores with more than 1,000 products.
- **Instant offline catalog:** previously synced products, names, SKUs, barcodes, prices, and stock are loaded immediately from SQLite when opening POS, without waiting for Supabase.
- **Resilient synchronization:** catalog caching succeeds independently of optional requests such as shift, promotions, settings, and customers. Partial cloud catalog fetches do not replace the saved full snapshot.
- **Reconnect automatically:** retries the cloud product download every two minutes while POS is open and supports manual **Sync Products**.
- **Local cache indicators:** POS shows how many products are saved locally and the last successful cloud sync time.
- **Pending sale stock:** a fresh cloud catalog is adjusted for sales still queued on this device, so the displayed available quantity does not prematurely increase.
- **Shop-specific caching:** each shop reads its own product snapshot and pending sale queue.

## Before going offline
1. On **each** Samsung tablet, install StorePOS v1.7.5 over the installed app. **Do not uninstall or clear storage.**
2. Sign in while connected to the internet; open Point of Sale and tap **Sync Products**.
3. Check **Saved locally: [number] products** and confirm the **Last cloud sync** indicator.
4. Switch off Wi-Fi/mobile data and reopen the POS; check barcode lookup, product search, prices, and supported cash checkout.
5. Restore internet. Confirm products refresh and queued offline sales are synced exactly once.

## Operational limits and safety
- The **first** product sync must succeed online on each device; an uninitialized tablet cannot load an offline catalog.
- Cashier accounts, authorization/licensing, and any device policies must already permit POS access; cached products alone do not guarantee offline sign-in.
- Supported basic cash checkout can queue offline; weighted products, retail packs, batch/serial tracking, promotions, online payment methods and other special transactions may still require cloud connectivity.
- When **two tablets** sell offline, displayed stock is a local estimate until both devices sync. Review cross-device stock consistency after recovery.
- Never uninstall the app or clear its data while offline sales remain unsynced.
- SQLite data is still held inside the app's existing `motopos_offline.db` file for backward compatibility; the catalog is logically scoped to the StorePOS shop.
- This is a StorePOS Android application-only update; no cloud schema mutation, no StorePOS historical sales deletion, and no MotoPOS data modification.

## Verification
- GitHub Android CI passes unit tests, Android lint, and debug APK build on the feature branch.
- Device-to-cloud full catalog refresh, physical Bluetooth scanner and printer, offline recovery with multiple tablets, and production Supabase RLS behavior still require real-device acceptance testing.
