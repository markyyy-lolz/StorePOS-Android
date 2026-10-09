# StorePOS Android v1.7.4 — Adjustable Receipt Footer-to-Cut Spacing

The VOZY G80 receipt auto-cut can now be moved closer to or farther from the final **Powered by StorePOS** footer without changing the business content or default full-cut command.

## What's new
- **Settings → Receipt printer → Footer-to-Cut Spacing**: integer slider from **0 through 8 extra blank lines**. Default **2**, matching previous sales receipts. Setting saves automatically on the tablet.
- **Test print** reflects your currently selected spacing and ends with the actual StorePOS footer, so you can check where the physical cutter lands before selling.
- **POS sale receipts** embed the chosen spacing in the ESC/POS bytes. Works with both direct Bluetooth/USB and shared two-tablet VOZY G80 printer queues.
- **Shared queue test prints**, **X Reading**, **Z Reading**, and batch sales reports respect the saved line spacing.
- Existing 80mm / 58mm settings, automatic full cut, scanner, PDF rendering, sales, and payments remain unchanged.

## Set up on Samsung Galaxy Tab A9+ 5G
1. Install v1.7.4 over your existing StorePOS. Don't uninstall or clear app data.
2. Go to **Settings → Receipt printer**. Adjust **Footer-to-Cut Spacing** to **2 extra lines** first.
3. With your VOZY G80 connected, choose **Test print**. The cutter should act after the final "Powered by StorePOS" line.
4. If text is too close to the cut, increase to **3–4**. If too much blank paper, reduce to **1**, retest. **0** could cut too close to the text due to printer hardware offset.
5. The setting is **per tablet** and is embedded in newly generated print jobs. Jobs already queued use their original spacing. On two cashiers, configure each if you need identical output.
6. Use existing sale receipt **reprint** only if you need it. Never repeat checkout to test paper cut.

## Verification
- Unit tests cover line spacing normalization, exact ESC/POS full-cut bytes, direct receipt footer location, shared queue test byte identity and X report footer.
- GitHub Android CI must pass before distributing an APK.
- Hardware cut position depends on the VOZY G80 printer mechanism; physical testing is required.

No Supabase schema changes, PayMongo changes or MotoPOS record changes.
