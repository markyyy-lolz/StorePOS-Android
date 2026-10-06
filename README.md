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


## v1.3.1
- Native QR Ph appears directly inside the Android POS checkout with the exact sale amount.
- Automatic PayMongo webhook verification and payment-status fallback.
- Payment received state, QR expiry, cancellation safety, and retry-safe sale finalization.
- Per-client PayMongo merchant accounts through the Custom StorePOS license module.
- Owner/Admin credential setup with server-side secret handling and Supabase Vault encryption.


## v1.3.2
- Added Cloudflare Turnstile verification directly to Android sign-in and sign-up.
- Fixes Android `captcha_failed` errors while keeping Supabase CAPTCHA protection enabled.
- Sends verified CAPTCHA tokens with Supabase email/password authentication.
- Improves CAPTCHA failure/expiry messaging and refresh behavior.
- Uses the production StorePOS Cloud confirmation URL for new Android sign-ups.
