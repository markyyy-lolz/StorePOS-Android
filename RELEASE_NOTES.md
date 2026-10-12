# StorePOS Android v1.8.5 — Offline Database Backup Compatibility Fix

**Optional stable hotfix — Build 37, package com.storepos.app.**

## Fixed

- **Settings → Manage Offline Database → Save Offline Database Backup As...** no longer relies on unsupported `VACUUM INTO` SQLite syntax (common on Android 8 and Android 9).
- Creates a **read-only logical snapshot of the live database** using a source SQLite transaction, preserving the entire app-owned offline database, pending offline sales, original thermal receipt bytes and locally queued inventory changes as rows and BLOBs.
- Recreates the existing table schema and indexes into a separate temporary SQLite file, retaining the source database schema version. The live StorePOS database is NOT deleted, moved, reset, compacted or replaced.
- Before export, the temporary backup is checked for matching table names and row counts, its schema version and `PRAGMA integrity_check = ok`.
- After copying the temporary backup into the user-selected Android Save As destination, a SHA-256 comparison verifies the **actually saved file**. The app confirms verified size and fingerprint instead of falsely claiming success.
- If backup or verification fails, the UI reports a clear error and the original live database remains unchanged. Do not rely on an unverified file.
- Preserves v1.8.4 data-health presentation improvements, owner/admin offline DB panel, and v1.8.3 offline login and basic cash-sale functionality. **No new Supabase schema and no product, receipt or sale data migration.**

## Upgrade and test safely

1. With a live Internet connection, install the v1.8.5 APK **over existing v1.8.4**; never uninstall or clear app data, especially if there are pending offline sales.
2. Sign in once and open POS to validate your device and trusted products while online.
3. As owner/admin, open Settings → Manage Offline Database → Save Offline Database Backup As... and choose an Android Documents folder.
4. Confirm the screen displays **Backup saved and verified**, the number of copied tables, size, and first 12 characters of its SHA-256 fingerprint.
5. Inspect the saved file in the chosen folder and keep it somewhere private. It contains business/private transaction data; it is **not** an automatic restore feature.
6. Restore Internet after any offline test and ensure original sales remain intact and sync as normal.

## Limitations

- The backup snapshot briefly blocks other writes to ensure a consistent copy. Prefer creating backups when no checkout is actively being processed.
- The default private SQLite database cannot be moved to an SD card. Export creates a *copy*; app-private data remains untouched.
- On Android versions or document providers with unsupported operations, export may still fail. Report the new error message; do not uninstall the app or clear storage.
- Although automated Android emulator testing exercises SQLite backup behavior, **physical Samsung tablet and Bluetooth/USB printer acceptance tests remain necessary before rollout to active grocery registers**.
- This is an **optional stable update**; it does not force existing registers to stop working.

The v1.8.4 `near "INTO": syntax error` was caused by the device SQLite engine lacking the SQLite 3.27+ VACUUM INTO command. This release uses a compatible transaction-based alternative.
