# StorePOS Windows — Native retail POS for Windows 10/11

A real **C#/.NET 10 WPF Windows desktop app** with in-house SQLite WAL and existing StorePOS Supabase backend. **No Python, Electron or browser wrapper.**

## Latest candidate
- **v1.0.3-preview.1 — desktop checkout UI redesign**, with midnight-navy sidebar, improved catalog, cash panel, barcode workflow and action-oriented Windows activation state.
- GitHub Actions: `.github/workflows/windows-build.yml`. Creates standalone Windows x64 EXE + Inno Setup installer.
- Developer prerelease only: validation on actual Windows cashier hardware required before production.

## Existing functionality
- Supabase Auth + StorePOS shop-scoped cloud database + device licensing.
- SQLite offline-first sales/stock journal and role-scoped cloud reconciliation.
- Windows-installed USB/Bluetooth ESC/POS receipt printing, 58/80mm receipt width, original receipt PDF export, audited original receipt IDs.
- Unknown scanned barcode → manager Quick Add with price/cost/barcode/unit/stock, queued for conflict-aware sync.
- Manager product and stock changes; saved offline database backup, queue status and needs-review.

### How to use
1. Install the `StorePOS-Windows-v1.0.3-Setup.exe` developer prerelease on a spare **Windows 10/11 x64** PC.
2. Login online as an approved StorePOS employee. The UI distinguishes being **online** from having a valid **Windows license**.
3. If license is not active, use the top amber **Activate device** button; enter the issued device key in Settings.
4. Sync to download the shop catalog after successful activation.
5. Test barcode scanning, cash sales, Windows printer and receipt PDF; then test reconnect without duplicate cloud sales.
6. Do not uninstall/clear app data while any local sales remain queued or needs-review.

## Limitations
Full Android StorePOS v1.8.6 feature parity remains incomplete: complex promotions, extensive analytics/X/Z, multi-operator printing authority, and some staff, supplier and payment workflows are not yet fully ported. Windows login/Turnstile and actual VOZY G80 printing have not been field-certified. There is no commercial Authenticode signature.

## Data
- StorePOS cloud: `qgyzdoltjlryjthxxscw` (shop-isolated).
- Per-device SQLite: `%LOCALAPPDATA%\Azurate\StorePOS.Windows\storepos.sqlite3`.
- Windows installer updates the app without deleting that SQLite database.

Azurate Software Solutions — Pulo San Roque, Paombong, Bulacan.
