# StorePOS Android v1.9.0 — Production-safety implementation matrix

**Base:** latest released `v1.8.9` (not stale `main`).  
**Status:** DEVELOPMENT BRANCH / DRAFT PR, not a production release.  
**Tenant:** StorePOS `shops.app_code = 'storepos'` only. No MotoPOS/BrewPOS mutations.  
**Principle:** Do not recreate customer, promo, sale, inventory or print tables that already exist.

| Requested area | Existing v1.8.9 implementation | v1.9 branch enhancement / remaining acceptance |
|---|---|---|
| Suki Membership & Loyalty | `customers.loyalty_points`, customer add, manual audited adjustments | **Added:** searchable stable suki reference, loyalty/prepaid balance summary. Still needs printable/scanable QR cards, earning/redeeming business policy and one-time redemption tests. |
| Utang / Customer Credit | `customer_receivables`/receivables, credit terms, partial-payment recording | **Added:** manager-visible outstanding utang + available credit, separate from prepaid wallet. Need atomic credit-limit tests and return/void reconciliation before wider roll-out. |
| Wholesale Pricing | Retail pack/wholesale SKU and schedule features in `RetailSuitePage` | Reuse and verify tiered quantities on real receipts; online validation is mandatory when wholesale price or packs apply. |
| Advanced Promos | Existing `retail_promos` and quote-based server checkout | Reuse/verify BXGY, bundles, stacking rules and date windows, refund behavior. Avoid applying promo logic offline without server guarantees. |
| Returns & Exchanges | Existing audited returns/refunds, approvals and partial returns in `OperationsPage` | Verify exchange links, receipt reprints, tax and restored stock, PayMongo reconciliation. |
| Repacking & Conversion | Stock adjustment ledger and real independently stocked SKUs | **Added (staged):** owner/manager online UI; idempotent PostgreSQL RPC proposal, both stock updates and movements plus audit in one transaction. SQL migration NOT deployed yet. Requires migration security review, staging integration and offline conflict tests. |
| Inventory Counting | Existing barcode/HID stocktake, manager approval, offline conflict queue | Reuse rather than replace. Real 2-tablet concurrent count acceptance still required. |
| Barcode Label Printing | Existing `ProductLabelCenterDialog` ESC/POS label printing | Reuse and validate layout on VOZY G80, multiple copies and barcode readability. |
| Cashier Fraud Monitoring | Existing approval, reconciliation, receipt-reprint and audit tables | **Added:** explainable manager review indicators for repeated reprints, denied approvals and wallet variances. Indicators are not allegations or proof of fraud. |
| Checkout Recovery | Existing hold/recall and offline sale queue; no private unpaid cart recovery | **Added:** SQLite unpaid cart snapshots keyed by shop, cashier and catalog epoch; 72-hour expiry; no old payment, price override, serialized allocation or sale restoration. User explicitly accepts/rejects restored items. |
| Remote Store Monitoring | Existing cloud device sessions and settings | **Added:** owner heartbeat overview (last-seen, not definitive online status), explicit this-device pending offline outbox counts. Other devices' SQLite cannot be inspected remotely while offline. |
| Express Checkout | Existing HID scanner, favorites and standard payment workflow | **Added:** persistent per-shop/per-cashier express toggle, favorites-first sorting, quick checkout action; existing shift, discount, payment and offline safety still mandatory. |

## Release gates (ALL mandatory for production v1.9.0)
1. CI Android unit tests, launcher tests, Android lint, APK compilation; check signing certificate is identical to deployed version.
2. Supabase migration deployed in controlled environment with authorization/RLS tests. Confirm authenticated cashier cannot perform repack, cross-tenant requests fail, same idempotency key cannot double-write, source stock never goes negative and concurrent transactions serialize.
3. Run actual two-tablet online/offline production-like tests: one sale queued, crash recovery, app cold start, returns, held sales, repack attempt while pending offline stock/sales, sync conflict review.
4. Verify 80mm VOZY G80 normal/freebie split cutter, X/Z, PDF parity and audited reprints.
5. Back up and protect local SQLite outboxes; update APK over installed app; never uninstall or clear data when sales are pending.
6. Do not merge to stale main or publish production v1.9.0 until required unfinished work is completed.

## Source of truth
- Existing production Supabase project is shared with MotoPOS but isolated by `app_code`, RLS and `shop_id`.
- Additive migration: `supabase/migrations/20261017_storepos_v190_repack_conversion.sql` is **code only**, not a claim of live DB deployment.
- `StoreRepackRepository` intentionally requires online server RPC. No optimistic local repack stock changes.
