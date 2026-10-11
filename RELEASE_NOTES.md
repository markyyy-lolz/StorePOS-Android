# StorePOS Android v1.8.4 — Data Health & Offline Database Settings

**Stable patch over v1.8.3 • build 36 • Android package com.storepos.app**

## What's fixed

- **Admin Center → Diagnostics → Data Health** no longer prints raw JSON arrays next to the issue labels. Each check now shows its actual count, a green "No issues" status where applicable, and a separate "Result unavailable" when the cloud response is missing or malformed.
- **View / Hide details** displays readable product/record names, SKU and selling price for up to 30 reported problem items; large JSON payloads no longer take over the screen.
- **Settings → Manage Offline Database** now appears immediately below Supabase Cloud for Owner/Admin. Shows the real app-private SQLite database path/size, pending/review sales and inventory changes, and a **Save Offline Database Backup As...** button to choose an export destination.
- The local backup is an export-only consistent SQLite snapshot; it does not replace, move, wipe, re-import, or reset the active database.
- **No price/inventory edits**: The products SKU 1001, 1002, 1003 flagged under "Missing cost" actually have cost_price = 0.00; this release preserves those records. Verify their actual service costs separately before changing any product field.
- Retains v1.8.3 previously verified offline startup, offline cash sale/receipt and stock sync behavior, cash receipt printing, and current SQLite schema v4.

## Install and acceptance testing

1. Keep Internet available during the first app launch after upgrade, verify the shop/license online and leave **Stay signed in** enabled for future offline starts.
2. Download StorePOS v1.8.4 and install **over** the current version. Do **not** uninstall, clear Android app data, or delete pending offline receipts.
3. Owner/Admin → Settings: confirm **Manage Offline Database** appears below Supabase Cloud. Check pending counts and export a backup to a private folder; confirm the file opens as SQLite using a trusted viewer.
4. Admin Center → Diagnostics: confirm the 3 missing-cost StorePOS subscription products appear only after tapping **View 3**. Other healthy checks show **No issues**, not `[]`.
5. While disconnected from the Internet, confirm previously verified offline login and basic cash checkout. Reconnect and confirm pending transactions synchronize once.

## Operational caveats

- The **Missing cost** warning is real for the three service products and will remain until their cost price is determined. Do not assign arbitrary costs or change selling prices to hide a warning.
- Offline cash sales remain provisional until cloud reconciliation; electronic payments, subscription/credit approvals and certain advanced transactions still require an Internet connection.
- The active SQLite database remains in Android's private app directory; Save As copies a backup, not a live location change. Backups may contain customer and sales data: handle them confidentially.
- Some old Android SQLite runtimes do not support `VACUUM INTO`. For safety, unsupported devices show a backup error instead of copying an inconsistent live database.
- Automated CI and signing cannot replace testing on the actual Samsung tablet and Bluetooth/USB printer. Roll out to one spare/test device before an active grocery cashier.

**Update status:** optional, not forced. No Supabase data migrations, no product modifications, no database reset.
