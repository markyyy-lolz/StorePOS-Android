# StorePOS v1.6.0 — Complete Retail Suite

StorePOS v1.6.0 closes the largest remaining Android retail-operations gaps while preserving the v1.5 register, inventory, payment, reporting and customer-display workflows.

## Admin Center
- New dedicated Admin Center for operational recovery and diagnostics.
- Offline Sync Center lists queued sales, last errors, retry-one and retry-all actions.
- Full Audit Trail viewer with actor, action, entity, device and change details.
- Diagnostics shows app version, StorePOS Cloud alerts, data-health checks and database schema health.
- Android Backup & Export center for Products, Customers and Sales CSV files plus a full JSON shop backup.

## Staff & Security
- Granular per-staff module access controls for POS, Inventory, Retail Ops, Customers, Service, Quotations, Suppliers, Branches, Reports, Admin Center, Alerts, Support and Settings.
- Permission overrides can only restrict the staff member's existing role and plan; they cannot bypass role access, license entitlements or Supabase RLS.
- Existing staff activation/deactivation, role management and device revoke/reactivate controls remain available.

## Suppliers & Payables
- New Supplier Accounts Payable ledger.
- Fully received purchase orders automatically create supplier payables.
- Default payable due date is 30 days after receiving and can be tracked from Suppliers.
- Partial and full supplier payments support cash, bank, GCash, Maya, card, cheque and other methods.
- Supplier payments are protected by manager access, processed atomically and written to the audit log.

## Inventory & Labels
- New Barcode & Shelf Label Center in Android Inventory.
- Print 1–100 ESC/POS labels using the configured Bluetooth or USB printer.
- Labels include product name, brand, selling price, Code128 barcode/SKU and shelf location.
- Existing stocktake, batch/expiry, serial tracking, receiving, damage/loss, supplier returns, price history and scheduled price changes remain available.

## Multi-Branch
- Branch overview now consolidates today's sales, inventory value and low-stock counts.
- Each accessible branch also shows its own sales and inventory snapshot.
- Existing inter-branch stock transfers remain available.

## Notifications & Reliability
- Background StorePOS alerts check critical/warning conditions every 30 minutes when internet is available.
- Background alerts can be enabled or disabled in Settings.
- New database migration/schema health diagnostic verifies required StorePOS tables and RPCs.
- Existing operational Data Health checks remain available.

## Customer Display 2.0
- New Settings > Customer Display configuration.
- Optional auto-start when an Android presentation display is connected.
- Optional StorePOS branding and customizable idle message.
- Customer display now shows a dedicated Payment Complete screen after checkout.
- Total paid, receipt number and change due are clearly displayed to the customer.

## Existing Retail Suite
v1.6.0 keeps the production POS register, barcode auto-add, PayMongo QR Ph, split payments, store credit, customer credit/receivables, returns/refunds, voids, cash drawer, cashier shifts, X/Z reports, manager approvals, payment reconciliation, products/categories, stocktake, batches, serials, supplier purchasing, transfers, promos, reservations, loyalty, quotations, reporting, Training Mode, device licensing and customer display.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: com.storepos.app
- Version code: 19
- Version name: 1.6.0
- Existing StorePOS shops, products, sales, customers and license data are preserved.

StorePOS receipt/document tools remain configurable business software. StorePOS does not claim that installing the app by itself satisfies tax-registration, invoicing or government accreditation requirements.
