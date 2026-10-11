# StorePOS Hybrid v1.9.0-beta.1 — Internal Two-Tablet LAN Test Release

**Prerelease / internal beta.** The Android automated build, unit tests, and lint have passed on the development branch. Physical Samsung tablet, LAN interruption, and receipt-printer acceptance tests are still required. **Do not replace the active grocery register before testing with backup devices.**

## What's new
- **Offline-first checkout**: basic retail checkout saves to app-private SQLite if internet is unavailable. Each sale has an idempotency key for retries and a provisional reference.
- **Wi-Fi LAN Hub**: pair two tablets on the same router/hotspot, including with no WAN internet. Settings → Offline LAN Hub: configure one Host Tablet and one Second Tablet, the host LAN IPv4 address and matching random pairing key. Use **Test LAN Inventory Connection** on both.
- **Shared stock**: LAN host serializes local stock deductions and rejects overselling; the second tablet must reach host to reserve shared stock. Stock snapshots refresh while POS is foregrounded.
- **Provisional offline sales**: local paper receipts (Bluetooth / USB configured printer) and PDF; the final cloud sale number is created after sync. Do not treat the provisional receipt as proof of online settlement.
- **Store credit and weighed items**: basic weighed quantities accepted offline. Store-credit spending can be queued provisionally only through LAN-authoritative cached customer balance. Cloud reconciliation may reject stale balances from another independent online register.
- **Cloud recovery**: pending sale replay preserves original transaction IDs; retries approximately every 30 seconds while POS is open and internet is validated; failure details remain in Admin Center → Sync Center.
- **Offline startup**: previously signed-in, authorized devices can reopen during the configured license grace period.
- **Offline database settings**: admin can see actual database path and size, pending count, and export a consistent SQLite backup using a chosen destination.
- **Preserves data**: SQLite schema upgrades in place; shop sales, receipts, inventory, customers and authentication are not reset.

## How to pair the devices
1. Connect both Android tablets to **the same local Wi-Fi network**. Internet is optional but the router must allow device-to-device connections; turn off AP/client isolation.
2. Host tablet: Settings → Offline LAN Hub → Host Tablet → Generate secure key → Save. Keep StorePOS foregrounded and note its IPv4 address.
3. Client tablet: Settings → Offline LAN Hub → Second Tablet → enter host IPv4 and same key → Save.
4. Select Test LAN Inventory Connection on both. Test a stock-tracked SKU with only one unit available and attempt competing checkouts on two tablets: **only one should be accepted**.
5. Disconnect internet while keeping LAN Wi-Fi active, verify offline receipts and queued sales, then reconnect and confirm cloud sync. Do not clear app storage or uninstall.

## Important limitations
- The host must be powered on and reachable over LAN. If host becomes unreachable, the client cannot approve shared-stock checkout. Two completely disconnected tablets cannot guarantee the same inventory count.
- Automated PayMongo payments **cannot be verified/settled offline**; do not mark pending/unverified payment as paid. New credit debt, batches, serial tracking, pack pricing, and complex promotions remain online-only.
- The current LAN pairing protocol authenticates requests with a shared secret but does **not encrypt LAN traffic**. Use a trusted local network, never forward port 18763 to the internet.
- Physical printer tests, two-device collision tests, rollback/update signing check, and failed-sync reconciliation still must be completed before a production rollout.

## Compatibility
- Android package: com.storepos.app
- Version 1.9.0-beta.1 (versionCode 32); newer than the published v1.8.2 (versionCode 31).
- Uses the existing StorePOS update-signing workflow. Android will reject an in-place upgrade if the installed APK was signed with a different certificate.
- No public Supabase schema changes and no production data migration. This release remains in the development/prerelease channel.
