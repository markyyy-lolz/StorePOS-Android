# StorePOS Android

Modern retail POS for Philippine stores, built by **Mark Reymuel Pascual**.

## Identity
- Package: `com.storepos.app`
- App code: `storepos`
- Business type: `retail`
- Shares the existing Supabase backend with MotoPOS while keeping shops, licenses and app updates separated.

## StorePOS v1.2.0
- Point of Sale
- Barcode scanning
- Bluetooth + USB ESC/POS receipt printing
- Products and inventory
- Customers, loyalty and store credit
- Suppliers and purchasing
- Cashier operations and returns
- Reports
- Offline sale queue
- Multi-branch support
- Alerts, support and licensing

StorePOS does not expose MotoPOS motorcycle service/job modules in its retail plan entitlements.


## v1.2.0
- Quick Favorites are now surfaced directly in the POS register for faster cashier selling.
- Advanced retail favorites remain user-specific and sync from the shared StorePOS backend.
- Retail Suite features from v1.1.0 remain included: pack/tingi, wholesale pricing, variants, weighed items, batches/expiry, serials, promos, reservations, credit terms, scheduled pricing, stock-loss/supplier-return controls, checklists, and digital receipts.
- Android CI validates clean debug builds on pushes and pull requests.


## v1.3.0
- Retail Control screen for Android
- Price checker with barcode scanning
- Reorder suggestions with supplier price comparison and draft PO creation
- Live X reports alongside existing Z reports
- GCash/Maya/card/bank reconciliation
- Manager approval request/review queue
- Negative-stock policy control
- Data-health checks for duplicate barcodes, negative stock, missing cost, expired batches, and stale scheduled prices
- Numbered receipt-reprint audit
