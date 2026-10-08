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


## v1.3.3
- Fixed the Android Turnstile success callback so verified users can immediately press Sign in.
- Uses a dedicated Turnstile WebView instead of polling a hidden token from the StorePOS website.
- Correctly clears expired/failed CAPTCHA tokens.
- Removes unrelated website/footer content from the Android security-verification area.


## v1.3.4
- Fixes the blank Android Turnstile area by loading a hosted StorePOS Cloud verification page.
- Uses the real StorePOS HTTPS origin for Cloudflare Turnstile.
- Successful verification returns the token directly to Android and enables sign-in.
- Keeps Supabase CAPTCHA protection enabled.


## v1.3.5
- Fixes the final Turnstile token handoff where Cloudflare could show Success but Sign in stayed disabled.
- Uses a StorePOS callback URL intercepted directly by Android WebView.
- Enables Sign in immediately after Android receives a valid CAPTCHA token.
- Keeps Supabase CAPTCHA protection enabled.


## v1.3.6
- Reserves inventory before generating a PayMongo QR Ph payment.
- Blocks QR creation when live stock is insufficient.
- Releases reserved stock on failed, cancelled, expired, or QR-generation-failed payments.
- Finalizes verified PayMongo payments atomically against the reserved stock.
- Supports base quantities for pack/tingi products and reserves selected serial numbers.


## v1.3.7
- Professional customer receipt layout with client store branding first and subtle StorePOS footer branding.
- PayMongo QR Ph customer-facing receipt label changed to ORPH.
- Thermal receipts can print a QR code for the secure digital receipt.
- Digital receipt links expire after 3 days / 72 hours.
- Expired digital receipt links show a dedicated privacy/security expiry page.


## v1.4.0
- New Receipt Designer with live 58mm/80mm-style thermal preview.
- Per-shop receipt title, header, footer, visibility toggles and section ordering.
- Receipt design applies to ESC/POS printing and StorePOS digital receipts.
- Digital receipt QR can be enabled or disabled per shop.
- Store identity stays first and Powered by StorePOS stays at the bottom.
- Proper Android adaptive launcher icon fixes generic placeholder icons on tablets and POS terminals.


## v1.6.2 — Staff First-Login Security
- Enforces a required password change for newly created StorePOS staff accounts before Android workspace access.
- Uses the live StorePOS `invite-staff` Edge Function v4 for authenticated password changes.
- Keeps existing linked-user passwords intact and preserves all StorePOS data.


## v1.6.3 — PDF Receipt Archive
- Save permanent A4 receipt PDF from checkout or searchable Operations sales history.
- Share PDFs via Android's secure share sheet and print historical PDFs via system print services.
- Original recorded sale-item snapshots; duplicate-copy watermark and multi-page item list.
- Device-saved PDFs remain printable offline; fetching an unsaved historical receipt requires cloud access.


## v1.6.4 — Thermal-matched receipt PDFs
- PDF exports use exactly the same ESC/POS text and QR payload as direct thermal printing.
- Selected 58mm or 80mm roll width, not a separate A4 PDF invoice.
- Recorded payment details are used for historical receipt exports.
- Old expired digital QR tokens and old cashier labels may be unrecoverable.


## v1.7.0 Release Candidate — VOZY G80 Shared Printer
- Two Android tablets share one Bluetooth/USB 80mm VOZY G80 printer through a foreground print host and Supabase FIFO queue.
- Atomic claims, safe bounded retries, manual uncertain-print review, authenticated shop isolation and owner/admin reassignment.
- Client-side offline print outbox, receipt tax/customer sections, PDF parity and X/Z/batch printout options.
- Physical hardware validation remains pending; see SHARED_PRINTER_V170.md.
