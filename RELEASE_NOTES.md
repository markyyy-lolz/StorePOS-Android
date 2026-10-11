# StorePOS Android v1.8.3 — Stable Offline Startup Hotfix

**Stable release | Build 35 | Updates StorePOS v1.8.2 without uninstalling**

## Fixed

- **Offline startup:** A previously signed-in StorePOS cashier can reopen the app when Internet drops, using a previously verified same-device, same-user, same-shop cached workspace and a license still within its valid offline grace period.
- **Faster offline POS checkout:** With no validated Internet, StorePOS reads its previously verified local inventory catalog instead of blocking startup with “Unable to reach StorePOS Cloud.” Basic supported offline cash checkout can continue, subject to local stock and catalog safeguards.
- **Connection-loss handling:** If the network disappears during checkout preparation, the app can switch supported basic cash sales to its original local offline receipt and sync queue. Never automatically turn a payment, stock, permission or license denial into an offline sale.
- **Existing data preserved:** Original SQLite schema v4, offline pending sale and inventory queues, original printed receipt bytes, printer configuration, trusted catalog epoch and cloud reconciliation are preserved. No database reset or Supabase migration.

## Required first-time setup

1. **Before testing offline**, install the APK **over** your existing StorePOS v1.8.2. Never uninstall or clear storage; pending offline sales may exist there.
2. Connect to Internet; sign in with **Stay signed in** selected, open StorePOS and let it verify the existing license/shop. Open POS while online once to refresh your current trusted product catalog.
3. Keep Wi-Fi enabled but disconnect its Internet access, or disable Internet. Reopen StorePOS: the app should enter POS using its verified cached workspace, provided the account/session and offline license remain valid.
4. Perform a small **cash-only** offline transaction, verify the provisional receipt prints or is saved, restore Internet and confirm that the same sale appears once after cloud sync.
5. Check printer and restart on a spare/test tablet before rolling out to active grocery registers.

## Limitations and safety

- New logins, signed-out sessions, first-time license activation, accounts that did not choose **Stay signed in**, revoked sessions, and expired/cached-invalid licenses still require Internet.
- PayMongo and any payment provider verification, store credit, manager-approved, advanced weighed/serial/batch/pack-priced, or complex promotional checkout remain online-only.
- This hotfix **does not provide realtime shared inventory between two disconnected tablets**. The separate experimental Wi-Fi LAN Hub feature is in the v1.9.0 beta channel, not this stable release.
- Offline sales are provisional until Supabase reconciliation succeeds. A manager should review failed sync events and stock conflicts. Never repeat a checkout to solve a printing issue.
- No physical two-tablet, printer or disconnected cold-start test has been performed by the automated GitHub build. Deploy to a spare device for an acceptance test before placing on the main till.

Version: **1.8.3**, versionCode **35**, package **com.storepos.app**. Stable signing certificate continuity is verified against the earlier StorePOS update release. **This is an optional update, not a forced lockout**.
