# StorePOS Android v1.7.0 — VOZY G80 Shared Printer

**Release date:** October 9, 2026
**Version Code:** 24
**Signing:** StorePOS existing internally signed sideload/update APK (not Google Play production-signed).

## New in v1.7.0
- **2 tablets + 1 VOZY G80** USB/Bluetooth 80mm ESC/POS thermal printer; a single tablet becomes the assigned print host, and a second tablet submits jobs remotely.
- **Automatic sequential FIFO queue** with a single in-flight job, atomic claim, host heartbeat, shop-scoped job history, status dashboard, owner/admin-controlled reassignment, and manual recovery.
- **Connection retries (maximum 3)** when no bytes were sent. Ambiguous physical printing, partial writes and expired leases require inspection and manual recovery to avoid accidental duplicates.
- **Offline cashier print outbox** stores pending print requests and retries with a stable idempotency key. Sale creation is unchanged by print retries.
- **80mm POS receipts** now include recorded tax and customer information sections; Receipt Designer preview reflects the new layout. Receipt-size PDFs use the same ESC/POS template as the printer.
- **X Reading** (non-closing), historical **Z Reading** (does not close shift again), and **Batch Sales** printable thermal reports.

## Installation and setup
1. Install on two Android tablets belonging to the same StorePOS retail shop.
2. On Tablet 1, pair VOZY G80 over Bluetooth; select the printer and **80mm** in Settings, and verify Direct Test Print.
3. Assign Tablet 1 as the **Shared Print Host** (shop owner/admin required).
4. On Tablet 2 enable **Remote cashier** and send Queue Test Print; then perform sequential transactions.
5. Keep Tablet 1 signed in, charged, with foreground service running and internet access to Supabase.

## Operational limitations and compliance
- **Physical VOZY G80 acceptance tests on two tablets have NOT been completed.** This build has passed Android CI (Kotlin tests, lint, debug APK), but production behavior must be tested before running live checkout.
- Bluetooth output cannot independently confirm paper was printed; **SENT means bytes successfully transmitted**. Check paper before manual retry to avoid duplicate slips.
- Production roll stock, cutter mechanics, reconnect behavior and idle/battery-kill behavior vary by device.
- Monochrome store-logo bitmap printing, comprehensive BIR-compliant tax invoice formatting, and full physical printer validation are **not yet completed**. No BIR accreditation or permit is claimed.
- A full test plan and recovery instructions are in **SHARED_PRINTER_V170.md**.
- The Android APK is internally debug-signed for existing StorePOS sideload updates; it is **not a Play Store signed/AAB release**.

## Database safety
Only additive StorePOS shared-printer tables and an authenticated, store-validated RPC were migrated. Existing StorePOS/MotoPOS shops, sales, accounts and licenses have not been reset.
