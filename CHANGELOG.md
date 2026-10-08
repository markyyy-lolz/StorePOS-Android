## Android v1.6.3 — PDF Receipt Archive
- Added Save/Share PDF to successful checkout; searchable receipt history with Save/Share/Print PDF.
- Uses immutable sold-item details, A4 pagination, Android document picker and secure FileProvider.
- Kept original thermal printing and cloud sales untouched.

# Changelog

## v1.4.1
- Fixed Supabase insert operations decoding empty `return=minimal` responses.
- Added explicit `select()` return representation for inserts that immediately decode the created row.
- Fixes the Inventory message: `StorePOS received an incomplete cloud response. Please retry once.` after a successful product insert.

# StorePOS v1.0.1

- Fixed the update checker offering MotoPOS releases from the shared database.
- StorePOS now selects only published StorePOS releases, with a second product check after decoding.
- Corrected retail wording on sign-in and Software information.
- Added regression tests for mixed-product releases, unpublished releases and missing product identity.

Android 8.0+ • Package: `com.storepos.app` • Version code: 2.
Existing shop data and offline queues are preserved.

# Changelog

## StorePOS v1.0.0

Initial retail release.

- StorePOS retail workspace identity
- POS and barcode scanning
- Bluetooth and USB receipt printing
- Inventory and stock adjustments
- Customers, loyalty and store credit
- Suppliers and purchasing
- Cashier operations and returns
- Reports and offline sale queue
- Branches, stock transfers, alerts, support and licensing
