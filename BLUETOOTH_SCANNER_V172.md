# StorePOS Android v1.7.2 — Bluetooth HID Scanner Setup

## Samsung Galaxy Tab A9+ 5G
1. On scanner, enable Bluetooth HID Keyboard mode per its manufacturer manual.
2. On Samsung tablet, open Settings > Connections > Bluetooth and pair.
3. In Samsung Notes, scan a barcode. It should type the entire code and preferably press Enter/CR at the end.
4. In StorePOS POS, scan without opening the camera scanner: the search field automatically takes keyboard focus.
5. Scan Product A twice, then B. Cart should contain Product A quantity 2 and B quantity 1, without leaving POS.
6. If the field loses focus, tap the barcode search box. Clear previous manual searches with the X control.
7. With a paired HID keyboard, StorePOS consumes Enter, Tab and Escape suffixes in the focused search field.

## Inventory and Stocktake
- Inventory: Scan while the search is focused. The result remains selected so a subsequent HID scan replaces it.
- Stocktake: Select the new Bluetooth HID stocktake scanner (+1) field, then scan a counted item. Each match adds 1 to the count, without directly posting or approving inventory changes.
- For adding/editing a product, scan into the Barcode field.

## Troubleshooting and acceptance checks
- If scanning works in Samsung Notes but not StorePOS, tap the POS barcode search field and scan again.
- If symbols are wrong or text is incomplete, verify scanner keyboard layout, pairing and suffix.
- Prefer Enter/CR; avoid Escape/Back suffix if scanner can be configured.
- An unknown barcode must show a create-product prompt rather than silently becoming a sale.
- Repeat tests with matching, repeated, unknown and mixed UPC-A/EAN-13/Code128 barcodes.
- Check that the POS remains on screen, offline sales remain safe, and normal checkout/receipt printing still work.

Hardware acceptance testing on the physical Samsung Galaxy Tab A9+ 5G and scanner is still required. The specific Shopee scanner model was not verified.
No Supabase schema, PayMongo, or MotoPOS data changes in this update.