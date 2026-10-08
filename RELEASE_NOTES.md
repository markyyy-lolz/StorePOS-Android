# StorePOS Android v1.6.4 — Thermal-matched PDF Receipts

## One receipt, two outputs
- Saved PDF now uses the same ESC/POS print data as Bluetooth/USB printers, instead of a separate A4 design.
- Physical PDF width follows the selected 58mm/80mm paper size (32/48 columns).
- Item names, prices, header, footer, tax, totals, section order and receipt QR content match the thermal data.
- Historical receipts fetch recorded payment methods, amounts and references from StorePOS Cloud.
- Long receipts continue onto additional narrow pages. Saved PDFs do not expire.
- Android print dialog uses 58mm/80mm paper-width hints, subject to printer driver support.
- Existing direct thermal printing remains untouched.

## Limitation for past transactions
- Expired digital receipt QR tokens and the exact old cashier label may not be recoverable. No unavailable information is invented.
- Historic PDFs use the current shop receipt design for the original sale items and recorded payments.

## Compatibility
- Android 8.0+; package com.storepos.app; version code 23.
- No database changes, stock adjustments or changes to PayMongo payment processing.

---

# StorePOS Android v1.6.3 — Permanent PDF Receipt Archive

## New receipt actions
- After completed checkout: **Save PDF** and **Share PDF**, alongside existing Bluetooth/USB thermal printing.
- Operations > Receipts & Sales Aftercare: search by receipt number or date, then **Save PDF**, **Share**, or **Print PDF** from recorded sales.
- Uses the original sale item snapshots and original sold prices, not current catalog pricing.
- A4 multi-page PDF with shop identity, itemized quantities, discounts, tax, payment summary where available, and sale number.
- Explicit **duplicate copy** and **not an official tax receipt** labeling; receipts generated from sales history are for recordkeeping and do not change payment or inventory state.
- Android's Storage Access Framework saves PDF to a user-selected destination (including device files or a connected document provider) without broad storage permissions.
- Share PDFs securely using scoped temporary FileProvider access.
- Android system Print dialog prints historical PDF copies; the print request is submitted to the existing StorePOS receipt reprint audit.

## Compatibility
- Android 8.0+; application ID com.storepos.app.
- Version code: 22; version name: 1.6.3.
- Existing Supabase transactions, PayMongo integration, stock, shop data and digital receipts remain unchanged.
- PDF files saved to the device remain available without internet access. Historical PDF generation requires the saved sale items to be retrievable from StorePOS Cloud.
- Print output availability depends on installed Android print services; 58mm/80mm ESC/POS printing continues to use the existing direct printing path.

---

# StorePOS v1.6.2 — Staff First-Login Security

This release completes the StorePOS Staff Management v2 flow across StorePOS Cloud and Android.

## Staff first sign-in
- New StorePOS staff accounts created with a temporary password are now required to choose their own password before entering the Android app.
- The requirement is read from server-controlled Supabase `app_metadata.must_change_password`.
- Password changes are completed through the authenticated StorePOS `invite-staff` Edge Function.
- After changing the temporary password, the staff member signs in again with the new password.

## Security
- Temporary-password enforcement happens before shop/device/license access is loaded.
- Passwords are never stored in StorePOS tables or logs.
- The password-change flag is cleared server-side only after the password update succeeds.
- Existing linked Supabase accounts keep their existing password and are not forced through this flow unless explicitly marked.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: com.storepos.app
- Version code: 21
- Version name: 1.6.2
- Existing shops, products, sales, customers and licenses are preserved.
