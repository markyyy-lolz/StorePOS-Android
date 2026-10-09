# StorePOS Android v1.7.2 — Bluetooth HID Scanner

- POS: barcode-search auto-focus, safe Enter/Tab/Escape suffix consumption, repeated-scan quantity increment and search reset.
- Inventory: scanned barcode selected for quick replacement by the next HID scan.
- Stocktake: new Bluetooth HID scanner field adds +1 counted quantity per matched barcode; does not approve changes.
- Added regression tests for CR/LF, Tab, empty scan and repeated barcode inputs.
- No Supabase migrations or changes to MotoPOS, payment or printing workflows.

See BLUETOOTH_SCANNER_V172.md for Samsung Galaxy Tab A9+ Bluetooth pairing and hardware tests.