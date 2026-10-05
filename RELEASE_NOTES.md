# StorePOS v1.3.0

StorePOS v1.3.0 expands the Android POS into a more complete Philippine retail operations system.

## Retail Control Center
- Price checker with barcode/SKU scanning without adding items to the cart.
- Live X Report for the current cashier shift, while existing Z Reports remain the finalized closeout record.
- Manager approval queue for discounts, price overrides, negative-stock exceptions, void/refund requests, cash-drawer exceptions, and other controlled actions.
- GCash, Maya, card, bank, and other non-cash reconciliation with expected-vs-actual variance tracking.
- Data-health checks for duplicate barcodes, negative stock, missing cost, expired batches, and stale scheduled price changes.

## Inventory & Purchasing
- Reorder suggestions based on current stock and reorder levels.
- Supplier price comparison using recent purchase-order cost history.
- One-tap creation of a draft purchase order from a reorder suggestion.
- Existing barcode-assisted physical inventory count/stocktake workflow is integrated into Retail Control.
- Negative-stock policy can be enabled or blocked by authorized managers. Batch- and serial-tracked items still require valid tracked units.

## POS Improvements
- Cashier-specific Quick Favorites remain available directly in the POS.
- Audited receipt reprints: repeat copies are numbered and logged with the reason and user.
- Existing advanced retail suite remains included: tingi/pack/kahon, wholesale pricing, variants, weighed products, expiry/batches, serial tracking, promos/bundles, reservations/deposits, customer credit, scheduled pricing, supplier returns, stock-loss controls, opening/closing checklists, digital receipts, and offline sales.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 5
- Shared StorePOS Supabase backend using `app_code = storepos`
- Existing shop data, users, products, sales, and offline queues are preserved.
