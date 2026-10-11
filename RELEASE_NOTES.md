# StorePOS v1.8.3-rc.1 — Offline Startup & Connection-Loss Hotfix

**Internal prerelease to test on a spare tablet before production grocery deployment.**

This hotfix is based directly on the published **StorePOS v1.8.2** branch. It keeps SQLite **schema v4**, offline cash receipt preservation, inventory adjustment/synchronization, trusted catalog epoch, customer data, printer behavior, and existing Postgres schema unchanged.

## Fixed
- Startup no longer requires a fresh cloud validation when the tablet is offline **and** it already has a previously authenticated Supabase session plus a still-valid, previously verified StorePOS shop/license/plan snapshot.
- Offline validation matches exact signed-in cashier, Android device ID and shop membership. It respects license expiration and the existing offline grace-day limit. A new device, expired license, signed-out cashier, forced password-change account or missing local credentials cannot activate offline.
- POS now checks validated Internet before cloud requests, loads a previously trusted local product catalog on loss of Internet, and opens basic cash checkout without trying cloud quote first.
- If the Internet drops during price quotation, POS offers offline checkout only for safe simple sales with a trusted catalog. Manager approval, electronic payment verification, store credit, promotions, batches/serials/pack/weighed advanced items still require cloud.
- Added network error classification tests so insufficient stock, rejected payments, and expired licenses cannot be mistaken for an offline-able failure.
- When starting offline, users with POS permission go directly to the register instead of a cloud-dependent Dashboard.
- Offline sync queue, provisional receipts and local receipt printing continue using existing v1.8.2 code.

## Important first-time setup
1. **While online**, install v1.8.3-rc.1 over the existing app. **Do NOT uninstall or clear StorePOS data.** Open StorePOS, choose **Stay signed in**, sign in and confirm the license/shop loads successfully.
2. Enter POS while online once to refresh the current shop's trusted product catalog.
3. Without clearing app storage, disable mobile data and router Internet, reopen StorePOS; it should return to POS and display cached inventory.
4. Make a **small cash test sale only** and verify that a provisional receipt is saved and can be printed.
5. Restore Internet; review POS/Admin Center sync, verify that sales and inventory reconcile once and do not duplicate.
6. Check with both Android tablets, printer, and actual shop Wi-Fi/hotspot behavior before using the hotfix on production registers.

## Limitations and caution
- Users who selected **Stay signed in OFF** or explicitly signed out must reconnect and sign in online. The hotfix does not store passwords or allow offline registration.
- No offline login is possible without a prior valid online session and cached license. An older v1.8.2 installation does not have the new startup snapshot; first launch **after installing the hotfix must be online**.
- StorePOS v1.8.3-rc.1 is a targeted v1.8.2 startup fix; it is **not** the separate v1.9.0-beta.2 LAN shared-inventory build. Two devices offline without a shared LAN authority cannot guarantee real-time shared stock.
- No unverified PayMongo QR/card payment can be marked settled offline.
- The updater's published stable version remains v1.8.2 until physical acceptance tests pass. This is an **RC**, not an automatic mandatory production update.

VersionName: **1.8.3-rc.1**; versionCode: **34**; appId: **com.storepos.app**.
