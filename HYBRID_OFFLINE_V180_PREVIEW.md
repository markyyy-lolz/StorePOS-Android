# StorePOS Android v1.8.0-rc1 — Hybrid Offline Sync Safety Preview

**Development candidate, not a production APK. Do not install on the two live cashier tablets yet.**

## Supported in this first phase

- Each Android tablet already has its own app-private SQLite product cache and pending-sale queue.
- **Shop-isolated queues:** pending sales belonging to one shop cannot be uploaded from a different shop's register.
- **Legacy protection:** pending sales from pre-v1.8.0 SQLite databases are retained but marked **Needs review** instead of being blindly replayed after Sherine Store's reset.
- **Cache protection:** a successful fresh cloud catalog load after upgrading is required before offline cash checkout.
- **Offline cash only:** cloud-dependent payments, store credit, PayMongo QR Ph and manager-approval workflows remain online-only.
- **Same idempotency key:** the initial online checkout and an ambiguous network-failure fallback use the same client key, so retries can use the existing server-side request ledger.
- **Cash sale prices:** the cached selling price is embedded in queued payloads. A changed/missing product or price causes **Needs review** instead of the sync silently repricing the historical cash sale.
- **Cloud stock conflict:** insufficient available cloud stock is quarantined locally for review.
- **Automatic sync while the POS page remains open:** attempts the shop queue roughly every 30 seconds, with manual Sync still available.

## Offline thermal receipt and PDF — phase 1

- After a cash sale is queued on **this tablet**, persist the original ESC/POS receipt bytes **in the same SQLite transaction** as the pending cash sale. If local storage fails, the cart stays open and the app must not claim the sale is saved.
- Cashier can **Print offline** through the VOZY G80 directly paired to the SAME tablet by Bluetooth HID/SPP or USB. Automatic print runs when enabled in printer settings; manual Print offline retry is available without running a second checkout.
- Printed receipt is visibly **OFFLINE CASH RECEIPT**, **PENDING SYNC**, **NOT POSTED TO STOREPOS CLOUD**, and **NOT AN OFFICIAL TAX RECEIPT** with a stable unique `OFF-...` provisional number, cash amounts, change and StorePOS footer/cut spacing.
- **Save PDF** uses the same already-stored ESC/POS byte stream as the physical print, preserving thermal receipt parity and paper width. It works while airplane mode is on.
- **Admin → Sync Center** permits locally archived provisional receipt reprint and matching PDF even after closing the confirmation dialog or reopening the app, while the pending record remains. Printing never triggers a new sale or cloud write.
- **Shared cloud printer** does NOT function in airplane mode. With two tablets and one VOZY G80, only the tablet with direct Bluetooth/USB connection can print immediately. The second tablet needs a separate local printer connection or LAN-host print bridge; StorePOS cloud print queue is not a LAN printer replacement.
- Offline receipt is **provisional**, not the final numbered cloud receipt or proof of PayMongo verification. When sync fails or needs manager review, local receipt remains for cash reconciliation.

## Known requirements before v1.8.0 stable

- Needs authenticated/background WorkManager replay and clear review dashboard. This branch currently retries only while the POS UI is open.
- Physical offline VOZY G80 Bluetooth and USB printing, PDF parity, auto cutter placement and reprint after app restart still need two-tablet hardware validation.
- Cross-tablet global stock cannot be guaranteed during network loss: quantities are provisional until cloud verification. Conflicting stock needs manager resolution, not a silent negative-stock write.
- A shop catalog reset epoch should eventually be validated **inside the same database transaction** as the cash sale write; the current client-side product/price comparison is an additional safety layer but is not an atomic server-side gate.
- Need to repair/stabilize GitHub APK signing: prior releases had certificate mismatches, so a newly signed APK may not install over an existing tablet installation without preserving the original signing key.
- After resetting Sherine Store, old Android versions must not be trusted to resync their historical SQLite queues. Update and reconcile both tablets before allowing automatic uploads.
- Offline checkout requires a valid locally cached session/device entitlement and authorized staff, with subsequent cloud validation.
- Test camera/HID barcode handling, 2-tablet same-item stock contention, duplicate/retry-after-timeout, printer, expired session, airplane mode, network-flap, app restart, and post-reset stale cache.

## Safety and data integrity

No Supabase tables, existing Sherine Store records, MotoPOS shops, cloud sales or payments were modified to create this development branch. Database snapshots and previous private backups remain unchanged.

A pending offline transaction is not the same as a posted/paid cloud transaction. Do not use a provisional offline receipt as a final BIR receipt until the application has the correct approved fiscal/compliance workflow for your business.
