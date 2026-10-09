# StorePOS Android v1.7.3 — Bluetooth Receipt Permission Hotfix

## Resolved
- Fixed Bluetooth receipt printing on Android 12+ (including Samsung Galaxy Tab A9+ 5G) where `BluetoothAdapter.cancelDiscovery()` caused `android.permission.BLUETOOTH_SCAN` denial during a completed sale.
- StorePOS uses a paired classic Bluetooth (RFCOMM / SPP) printer, which requires **Nearby devices / BLUETOOTH_CONNECT**, not active Bluetooth discovery permissions.
- On API 31+, StorePOS now skips unnecessary discovery cancellation; older Android devices retain best-effort legacy cancellation.
- Explicit Bluetooth Connect check and user-friendly missing-permission guidance.
- Transaction Complete now differentiates successfully saved sales from unsuccessful receipt printing, and advises receipt-only retry or Save PDF.

## Existing transaction recovery
1. Do **not** repeat checkout after the Transaction Complete screen appears.
2. Keep that screen open and select **Save PDF** as an immediate receipt copy, or use Print receipt after installing the update and verifying the printer.
3. On Samsung Settings > Apps > StorePOS > Permissions, confirm **Nearby devices** is allowed.
4. Open StorePOS > Settings > Receipt printer and verify VOZY G80 is selected, paired and test prints successfully.
5. If using the shared printer mode, use the queue review/recovery process instead of blindly reprinting; ESC/POS transmission success is not physical print confirmation.

## Validation
- Regression test confirms API 31+ never invokes a discovery cancellation requiring BLUETOOTH_SCAN.
- Build, Android lint, and unit test gates required before release.
- Physical VOZY G80 Bluetooth testing is still required; Android tests cannot confirm receipt paper emerged.

No Supabase schema, MotoPOS records or PayMongo changes.
