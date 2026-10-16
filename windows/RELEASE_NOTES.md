# StorePOS Windows v1.0.2-preview.1 — Native WPF Developer Preview

**Windows 10/11 x64, C#/.NET 10 WPF, self-contained. NOT Python, Electron, or a web wrapper.**

## Included
- Existing Aronium-inspired native desktop cashier UI with barcode/SKU search and keyboard shortcuts.
- Simple cash sales, atomic offline SQLite/WAL stock adjustment and immutable sync outbox.
- Authentication to the existing StorePOS Supabase shop, per-device licensing and online reconnect replay via StorePOS reconciliation RPCs.
- Manager-only **Smart Barcode Quick Add**: unknown scanned barcode prompts product registration with name, SKU, selling price, cost, unit and opening stock, queued for conflict-aware cloud synchronization. Does not guess remote product information.
- Windows-installed USB/Bluetooth ESC/POS printer spooler support.
- **Azurate supplier support footer** on new Windows receipts: AZURATE SOFTWARE SOLUTION; PULO SAN ROQUE, PAOMBONG, BULACAN; 09241134301.
- **Save Receipt as PDF** from the original archived printed receipt string, with 58mm/80mm paper-width layout.
- **Save Verified SQLite Backup** (SQLite backup API + integrity check) without deleting local sales.
- Sales and inventory outbox status / Needs Review, stock adjustments and manager physical count.

## Security and compatibility
- This Windows device needs its own authorized StorePOS device seat. Authentication/CAPTCHA and reconciliation must be tested with a real authorized Windows account before commercial use.
- Offline cash is provisional and can require conflict review; two independently offline devices do not share stock until synced.
- This installer is **unsigned by a commercial code-signing certificate**; Windows SmartScreen may warn. Do not bypass security warnings on a machine you do not trust.
- Installer does not delete `%LOCALAPPDATA%\Azurate\StorePOS.Windows\storepos.sqlite3`.
- Does not modify Android StorePOS, MotoPOS or BrewPOS source/data; no Supabase migrations.

## Not yet equal to Android v1.8.6
Full Android feature parity is **NOT claimed**. Missing/unverified Windows modules include complex retail offers and refunds, full staff/client/supplier/loyalty operations, comprehensive cloud reports including X/Z, PayMongo/card/QR verification, multi-terminal LAN authority, PDF fidelity on a real printer, and dedicated Windows barcode/hardware acceptance testing.

## QA
The GitHub Actions workflow gates publication on `dotnet build -warnaserror`, `dotnet test`, win-x64 self-contained publish and Inno Setup installer generation. Those automated checks do not substitute for end-to-end live grocery tests.

**Recommended use: isolated testing on a spare Windows PC only until login, receipt printer, and original actor cloud replay are validated.**
