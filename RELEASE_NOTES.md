# StorePOS v1.4.0

StorePOS v1.4.0 adds the new Receipt Designer and fixes Android launcher branding for dedicated POS tablets and terminals.

## Receipt Designer
- New Settings → Receipt Designer workspace with a live thermal receipt preview.
- Customize the customer-facing receipt title, header and thank-you/footer message.
- Toggle store address, contact number, TIN, receipt number, date/time, cashier and payment reference.
- Toggle the 3-day digital receipt QR independently.
- Optional compact thermal layout for tighter 58mm receipts.
- Store logo can be enabled on digital receipts when a shop logo is configured.
- Reorder receipt sections using move-up / move-down controls.
- Store identity remains pinned at the top.
- Powered by StorePOS remains pinned at the bottom.
- Receipt design is stored per shop in StorePOS Cloud and follows the shop across devices.

## Thermal Receipt Printing
- Bluetooth and USB ESC/POS printing now follows the saved receipt design.
- ORPH remains the customer-facing label for PayMongo QR Ph payments.
- Payment processor prefixes stay hidden from customer-facing references.
- Digital receipt QR printing respects the Receipt Designer QR toggle.
- 58mm and 80mm preview/layout behavior remains supported.

## Digital Receipt
- StorePOS digital receipts now read the same saved receipt design.
- Address, contact, TIN, receipt number, date and payment reference visibility follow shop settings.
- Custom receipt title, header and footer are supported.
- Configured shop logos can be displayed on the digital receipt.
- Digital receipts remain available for 3 days / 72 hours only.

## Android Launcher Icon Fix
- Added proper Android adaptive launcher icon resources.
- Fixes devices that displayed the generic purple Android placeholder icon instead of the StorePOS logo.
- Includes legacy launcher icon fallback for older Android POS terminals and tablets.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: `com.storepos.app`
- Version code: 14
- Version name: `1.4.0`
- Existing shops, products, sales, licenses, payment settings and receipt tokens are preserved.
