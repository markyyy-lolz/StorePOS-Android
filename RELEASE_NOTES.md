# StorePOS v1.4.2 — Barcode Scanner Auto-Enter

- Hardware barcode scanners now automatically submit scanned Barcode / SKU values in the POS register.
- HID scanners using Enter, CR, or LF suffixes immediately add exact matches to the cart.
- Scanners configured without a suffix use a short exact-match fallback, so the cashier no longer needs to tap the product manually.
- Repeated scans of the same item increase its cart quantity automatically.
- Camera barcode scanning uses the same centralized barcode/SKU lookup and add-to-cart behavior.
- Successful scans clear the search field and keep the register ready for the next item.
- Weighed products still open the quantity-entry flow instead of forcing a quantity of one.

This is a patch release for StorePOS Android v1.4.x.
