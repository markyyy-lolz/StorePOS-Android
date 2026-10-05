# StorePOS v1.3.1

StorePOS v1.3.1 adds native QR Ph payments and per-client PayMongo merchant integration for custom StorePOS licenses.

## Native QR Ph Payments
- QR Ph payment code appears directly inside the StorePOS Android checkout screen.
- The exact sale amount is attached automatically to the dynamic QR transaction.
- Large customer-facing QR display with amount, reference, countdown, and waiting state.
- Customers can scan using supported QR Ph wallets and banking apps.
- StorePOS shows a Payment received state before finalizing the sale.
- Successful PayMongo verification automatically completes the StorePOS transaction and proceeds to receipt handling.
- QR sessions expire automatically and can be cancelled safely before payment.

## Automatic Verification
- PayMongo webhook verification replaces manual payment-reference confirmation for the native QR Ph flow.
- StorePOS validates the confirmed amount before creating the final sale.
- Payment status has a server-side fallback check if webhook delivery is delayed.
- Paid transactions use a stable StorePOS transaction key to prevent duplicate sale creation.
- If payment succeeds but sale finalization fails, StorePOS keeps the verified payment and offers Retry finalization instead of charging again.

## Per-Client PayMongo Integration
- PayMongo automatic payments are available as a Custom StorePOS license module.
- Every StorePOS client connects their own PayMongo merchant account.
- Client PayMongo public + secret keys can be configured from StorePOS Cloud Settings.
- Secret keys stay server-side and are encrypted in Supabase Vault.
- Only shop Owner/Admin accounts can change PayMongo merchant credentials.
- Cashiers can accept enabled QR payments without access to merchant secrets.

## Existing v1.3 Retail Features
- Retail Control Center, price checker and live X Reports.
- GCash/Maya/card/bank reconciliation.
- Manager approvals and negative-stock policy.
- Supplier price comparison and reorder-to-PO workflow.
- Data-health checks and audited receipt reprints.
- Quick Favorites and advanced retail inventory features.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 6
- Version name: `1.3.1`
- Existing StorePOS shop data, users, products, sales, licenses, and offline queues are preserved.
