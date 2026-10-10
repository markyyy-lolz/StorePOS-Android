# StorePOS Android v1.8.2 — Hybrid Offline Sales & Inventory

## What's new
- **Completely offline cash checkout** using a durable device-local SQLite sale queue after the shop's product catalog has been synchronized at least once.
- **Direct offline Bluetooth/USB thermal receipts** and local receipt PDFs from the same ESC/POS bytes; internet is not needed for a paired printer.
- **Two-tablet independent sales** that upload their cash transactions separately to the same StorePOS Supabase shop when authenticated connectivity returns.
- **Offline inventory**: add products, edit standard product details, restock/deduct stock, and barcode-assisted partial physical stocktaking with local queuing.
- **Automatic reconnect synchronization** of inventory actions and cash sales, including background WorkManager scheduling and recovery from ambiguous network timeouts.
- **Inventory collision safety**: immutable operation UUIDs, cloud catalog generations, server-locked stock movements, price/stocktake compare-and-set and manager-review quarantine for conflicting updates. Never silently overwrite another tablet's inventory or discard a receipt.
- **Pending Sync & Needs Review** indicators in inventory; legacy/pre-reset outbox transactions are not uploaded against a new catalog automatically.

## Prerequisites and limitations
- Each Samsung tablet must log in and download its authorized shop catalog while online **before** going offline. Existing sessions, memberships and licensing rules still apply.
- **Offline is cash-only.** Online PayMongo, QR payment verification, server-backed pricing, cloud print queues, complex serial/batch/weighed stock and product deletion require internet.
- Stock adjustments made on independent tablets can conflict. If they do, the conflicting item remains queued for manager review; in particular an absolute physical stocktake cannot blindly overwrite sales from another device.
- Directly paired Bluetooth/USB printing works without internet; one shared cloud-connected printer does not.
- **Do not uninstall or clear StorePOS app data** while unsynced transactions or inventory changes remain on the tablet. Back up first.
- Not every offline sale is guaranteed to auto-accept into cloud stock; mismatched catalog/price/permissions, overselling and stocktake conflicts require manual reconciliation.

## Upgrade notes
- Android package `com.storepos.app`, `versionCode 31`.
- Local SQLite migration adds an inventory operation journal without deleting cached receipts or pending cash sales.
- StorePOS-specific additive Supabase migrations are installed in project `qgyzdoltjlryjthxxscw`. No prior sales, payments, stock movements, products or receipts were deleted.
- If you install over v1.7.4, make sure Android confirms the APK signature matches your currently installed StorePOS. Do not uninstall and reinstall to bypass a signature error; that risks losing unsynced on-device data.
- The StorePOS app and the optional terminal launcher have separate APKs and update paths.

## Verification and rollout
- GitHub Android unit tests, lint, and debug APK compilation passed on v1.8.2 RC1.
- **Physical end-to-end testing on two Samsung tablets, direct receipt printer, power-cycle/outage recovery and concurrent stock conflicts has NOT been completed by this release process.**
- Start with one test tablet and test shop. Check cloud sale/receipt accuracy, local printing, and queued operation reconciliation before upgrading active grocery checkout terminals.
- Not a certified fiscal receipt guarantee. Follow applicable PH receipt and tax compliance rules.

Prepared by Azurate Software Solutions.
