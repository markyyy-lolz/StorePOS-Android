# StorePOS Windows v1.0.3-preview.1 — Retail Cashier UI Redesign

**Native WPF / C# / .NET 10 developer preview, Windows 10/11 x64. No Python.**

## What's improved
- Original StorePOS **midnight-navy navigation + brighter checkout workspace**, inspired by familiar fast Windows retail POS interaction patterns, with distinct Azurate design language.
- Redesigned merchant header with device connectivity, operator, license state and a prominent **Activate device** shortcut when the Windows seat is not licensed.
- Clear activation banner on the POS page. A connected session does **not** falsely imply that a license is active or that products were downloaded.
- Larger barcode/SKU scanner field, high-legibility product table, refined stock/price information and function-key hints.
- Helpful empty states for unlicensed device, no cached catalog and a search with no results. Every state routes to an actual existing action (activate, sync, clear).
- Redesigned cash checkout area with a visible cash-only label, useful empty-cart state, larger totals, cash received and change.
- Checkout button is disabled for an empty basket or a device without a valid license. Cashier shortcuts continue to invoke the same existing permission checks.
- The pre-existing offline SQLite, cloud queue, Quick Add, original receipt/PDF, Azurate footer and database backup remain untouched.

## Native functionality inherited from v1.0.2
- Barcode scanning and manager Quick Add, offline cash sale journal, receipt reprints, inventory movements, local backup, PDF export, Windows ESC/POS spooler, Supabase cloud sync and Windows device licensing.
- POS supplier contact footer: **AZURATE SOFTWARE SOLUTION — PULO SAN ROQUE, PAOMBONG, BULACAN — 09241134301.**

## Testing
- CI: `dotnet build -warnaserror`, native Windows offline tests, x64 self-contained publish, Inno Setup installer.
- Manual QA remaining: 1366×768 / 1600×900 at 100–150% Windows scaling; scanner focus/Enter, invalid vs activated seat, genuine shop catalog, offline/reconnect, Vozy G80 printer + cutter, PDF parity.

## Safety
- This is a **developer prerelease**, not certified complete Android v1.8.6 feature parity or commercial production use.
- **Commercial Authenticode code signing and physical equipment tests have not been completed.** Test on a nonproduction Windows PC.
- Existing Android StorePOS stable APK, shop database, pending device outboxes and MotoPOS/BrewPOS data were not modified.
- Windows local data is preserved at `%LOCALAPPDATA%\Azurate\StorePOS.Windows\storepos.sqlite3`; install over the existing Windows preview, never delete that directory while pending sales exist.

## Design references
- https://www.aronium.com/ — fast keyboard/scanner cashier interaction.
- https://help.aronium.com/hc/en-us/articles/208766849-Workspace — dense, clear sales workspace hierarchy.
- https://www.behance.net/gallery/224138979/CirclePOS-Smart-Point-of-Sale-Interface — contemporary retail visual polish.

StorePOS Windows is original Azurate software and is not affiliated with those references.
