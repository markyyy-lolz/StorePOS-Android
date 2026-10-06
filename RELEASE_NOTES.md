# StorePOS v1.3.7

StorePOS v1.3.7 introduces a more professional customer receipt experience with StorePOS branding kept subtle and the client store presented first.

## Professional Receipts
- The client store name remains the primary branding at the top of the receipt.
- StorePOS branding appears only in the footer as "Powered by StorePOS".
- Customer-facing PayMongo QR transactions are labeled as **ORPH** instead of exposing the payment processor name.
- Processor prefixes are removed from customer-facing payment references.
- Payment status is clearly shown as PAID.

## Digital Receipt QR
- Printed Bluetooth/USB ESC/POS receipts now include a QR code when a digital receipt token is available.
- Customers can scan the QR to open the secure StorePOS digital receipt.
- The QR uses the deployed StorePOS Web receipt page.
- Receipt reprints include the same digital receipt QR while the link remains valid.

## 3-Day Digital Receipt Availability
- Digital receipt links are available for exactly 3 days from the original sale time.
- After 72 hours, the backend no longer returns receipt contents.
- Expired links show a professional expiry notice asking the customer to contact the store for another copy.
- The digital receipt page displays its expiration time while still active.

## Digital Receipt Payment Details
- Customer-facing digital receipts show ORPH for StorePOS PayMongo QR Ph transactions.
- Actual PayMongo processor details remain internal to StorePOS operations.
- Digital receipts show payment amount, paid status, and a customer-safe transaction reference.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 12
- Version name: `1.3.7`
- Existing shops, products, sales, licenses, PayMongo settings, and receipt tokens are preserved.
