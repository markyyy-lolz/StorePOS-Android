# StorePOS Windows — native WPF desktop client

StorePOS Windows is a **real C#/.NET 10 WPF app for Windows 10/11 x64**. It is NOT a Python program, browser-only wrapper or Electron application.

## Current build
- **v1.0.2-preview.1** (developer preview, not fully feature-parity certified)
- GitHub Actions: `.github/workflows/windows-build.yml`.
- Creates a portable self-contained `StorePOS.Windows.exe` and per-user Inno Setup installer `StorePOS-Windows-v1.0.2-Setup.exe`.
- Download the prerelease from the repository GitHub Releases page after the build succeeds.

## Current features
Native cashier UI with barcode scanning, unknown-barcode manager Quick Add, customer cash tender/change, offline SQLite WAL receipts and stock deductions, queued cloud reconciliation, shop-scoped Supabase authentication, device licensing, stock movements/physical count, conflict review, Windows USB/Bluetooth RAW ESC/POS receipt printing, original receipt PDF export, and verified local database snapshot backup.

Prints the POS provider contact footer at the bottom of new receipts:
**AZURATE SOFTWARE SOLUTION · PULO SAN ROQUE, PAOMBONG, BULACAN · 09241134301**.

## Database architecture
- Cloud: **existing StorePOS Supabase** `qgyzdoltjlryjthxxscw`, using staff JWTs and StorePOS shop-scoped RPCs. No service-role key is shipped.
- Offline: **per-Windows-PC SQLite** at `%LOCALAPPDATA%\Azurate\StorePOS.Windows\storepos.sqlite3`.
- Sync uses idempotent operation UUIDs, shop epoch, actor identity and manager-review status on business conflicts.
- Cloud catalog overwrite is blocked while unsynced local operations exist.
- Cash-only offline; no shared last-item inventory guarantee when two devices cannot communicate.

## Important limitations
Android v1.8.6 **full parity is not complete**. See `windows/RELEASE_NOTES.md` for exact gaps.
- Cloudflare Turnstile-to-Windows login not field-verified.
- PDF, Bluetooth/USB Windows spooler, device seat, and actual cloud reconciliation must be tested with physical equipment.
- No commercial Windows publisher code-signing cert; Microsoft SmartScreen may warn.
- No proven fiscal/BIR receipt compliance. All offline receipts show provisional status.
- Never uninstall the installed StorePOS version or clear local SQLite if pending sales exist.
