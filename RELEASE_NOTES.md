# StorePOS Hybrid v1.9.0-beta.2 — Wi-Fi LAN inventory test build

**Internal test prerelease; not approved for live grocery registers yet.**

This version is based on the **published v1.8.2 branch**, not the outdated main branch. Existing SQLite v4 database migrations, trusted catalog epoch protections, original cash-only hybrid replay, offline printed receipt archive, background sales/inventory synchronization and related tests are retained.

## Added
- Settings → **Offline LAN Hub**: configure Host Tablet or Second Tablet with a securely generated pairing key, local IPv4 address, and Test LAN Inventory Connection.
- Two Samsung tablets can connect over one trusted Wi-Fi router or hotspot **without internet**. The host holds an authoritative local stock queue and serializes offline **cash** sale acceptance so the same last unit is not sold twice through the reachable host.
- LAN cash sale request includes the existing original cashier ID, trusted catalog epoch, and exact printed receipt bytes, and is saved atomically by the host. The second tablet archives a local copy as already supported by StorePOS v1.8.2.
- Stock snapshot is refreshed from the host while the POS is open. Offline checkout does not silently bypass a configured host when a connection fails.
- Settings → Offline Database Storage displays the real internal DB path, size and pending count; admin can export a consistent SQLite snapshot using Android Save As.
- Devices previously validated and signed in may boot during their cached license's allowed offline grace window; new unverified devices cannot activate offline.
- Internal update versionName 1.9.0-beta.2 / versionCode 33 (previous published stable v1.8.2 / versionCode 31).

## Setup
1. Connect **both tablets to the same Wi-Fi LAN**, disable client/AP isolation on the router.
2. On host tablet: Settings → Offline LAN Hub → Host Tablet, generate pairing key, save, note the IPv4 address.
3. On second tablet: Settings → Offline LAN Hub → Second Tablet; enter that IPv4 and exact key, save, run Test LAN Inventory Connection.
4. Test internet-off Wi-Fi-on basic cash sale with 1 stock item on two tablets. Only one checkout should be approved while the host is reachable. Verify original receipt print, PDF, pending sync, and cloud replay.
5. Test host power-off, Wi-Fi disconnection, cloud recovery, old SQLite database upgrade, receipt reprints, and app restart before using a live register.

## Boundaries
- The two tablets **must still communicate locally** for shared stock to be guaranteed. Fully isolated tablets can only maintain independent offline queues and cannot guarantee real-time inventory.
- Offline **cash only** stays aligned with StorePOS v1.8.2 and its trusted cloud reconciliation RPC. PayMongo QR/online card settlements, new credit debt, store-credit spending, manager approval, weighed/batch/serial/pack complex pricing and promotions need online verification. Never mark unverified electronic payments as paid.
- Supabase cloud sales may require manager reconciliation after a catalog reset/stock conflict; the local original sale/receipt is preserved rather than silently dropped.
- Pairing request/response is authenticated over LAN, but transport is **not encrypted**; use a trusted router with no port forwarding.
- A physical printer and two-tablet device test is still required. A passing automated build is NOT proof of inventory/payment reconciliation under all outages.

**Do not install v1.9.0-beta.1**: it was generated from stale main with SQLite v2 and must not be used as an update over v1.8.2. Beta.2 uses the correct schema v4 lineage.

No existing shop data was reset or deleted, and no production Supabase tables were changed by this release.
