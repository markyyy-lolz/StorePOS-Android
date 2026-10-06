# StorePOS v1.3.6

StorePOS v1.3.6 hardens PayMongo QR Ph checkout so customers are not asked to pay for inventory that StorePOS cannot finalize.

## PayMongo Stock Reservation
- StorePOS validates live stock before creating a PayMongo QR.
- Required stock is temporarily reserved before the QR is shown.
- Pack/tingi quantities reserve the correct base-product quantity.
- Serial-tracked items reserve their selected serial numbers while payment is pending.
- Reserved stock is released automatically when QR payment is cancelled, failed, or expired.
- If PayMongo creation itself fails, the stock hold is immediately released.
- Verified payments finalize through the reserved stock in the same database transaction.
- Retry finalization continues using the same transaction key, preventing duplicate sales.
- Stale payment stock holds expire automatically and are cleaned up on future PayMongo checkout attempts.

## Why this matters
- Prevents the previous flow where PayMongo could say Payment received but StorePOS later failed with insufficient stock.
- Stock is secured before the customer scans the QR.
- Normal StorePOS sales cannot consume inventory that is currently reserved for a pending PayMongo payment.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 11
- Version name: `1.3.6`
- Existing shops, products, sales, licenses and PayMongo settings are preserved.
