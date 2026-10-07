# StorePOS v1.6.1 — QR Payment & Customer Display Fix

This patch improves the live QR Ph checkout flow and makes the customer display useful during payment and after checkout.

## Customer Display
- Mirrors the active QR Ph payment QR to the second/customer display.
- Shows the exact amount due beside the payment QR.
- After a successful sale, the customer display shows Payment Complete, total paid, change due and receipt number.
- If a digital receipt token is available, the customer display shows a scannable digital receipt QR code.
- Digital receipt QR remains separate from the payment QR so customers can clearly tell when they are paying versus collecting a receipt.

## QR Payment Reliability
- QR cancellation now checks the latest PayMongo status before cancelling.
- Prevents a race where a payment could be confirmed while the cashier is pressing Cancel QR.
- If payment is already confirmed, StorePOS finalizes the sale instead of cancelling.
- If cancellation cannot be confirmed yet, StorePOS keeps the QR active and continues checking instead of showing the previous global red cancellation error.
- Cancel and Check Now controls are locked while cancellation verification is in progress.
- Reserved stock is released only after the QR is confirmed failed, expired or cancelled.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: com.storepos.app
- Version code: 20
- Version name: 1.6.1
- Existing StorePOS data, shops, products, customers, sales and licenses are preserved.
