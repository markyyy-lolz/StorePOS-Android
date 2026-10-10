# StorePOS Windows v1.0.0 · Modern Desktop POS

Native C# / .NET 10 WPF Windows 10/11 x64 application in the existing StorePOS GitHub repository. Its source is isolated under windows/ and the installer is built by GitHub Actions on windows-latest.

## Architecture
- Backend: existing StorePOS Supabase project qgyzdoltjlryjthxxscw (NOT the BrewPOS DB). Only memberships with app_code=storepos are accepted.
- Auth: Supabase Auth email/password, using a real user JWT (never a service-role key). The production Auth CAPTCHA may require a hosted Turnstile challenge handoff; v1.0.0 includes an optional token entry field but does NOT yet offer embedded CAPTCHA.
- Licensing: existing validate_device_access and activate_device_access RPCs, bound to a stable per-installation Windows device UUID. Offline grace and actual expiry checked against cached server verification; reject clock rollback.
- Offline storage: SQLite WAL in %LOCALAPPDATA%\Azurate\StorePOS.Windows\storepos.sqlite3. The Windows installer never deletes this database.
- Cash: each sale, immutable UUID, receipt, cashier identity, stock decrement and retryable outbox entry commit as one local transaction. Sync uses storepos_hybrid_reconcile_sale. Only basic cash sales are allowed offline.
- Inventory: locally create, edit, adjust (+/-) or count simple products, saving to the same local outbox. Sync uses storepos_reconcile_inventory, with idempotency, role, epoch and stock conflict checks.
- Reconnect: a failed upload retains the original UUID. Business conflicts are marked review (no destructive auto-overwrite). Every action is uploaded as its original authenticated employee.
- Printer: locally installed Windows USB/Bluetooth ESC/POS RAW spooler; provisional receipts may be reprinted offline. A print error does not reverse a successfully saved sale.

## Build and installer
- Workflow: .github/workflows/windows-build.yml
- SDK: .NET 10 and Windows build runner.
- Publish output: windows/publish/StorePOS.Windows.exe, self-contained win-x64.
- Inno Setup script: windows/installer/StorePOS.iss
- Installer artifact: StorePOS-Windows-v1.0.0-Setup.exe, per-user installation (no elevation).
- After a successful GitHub Actions run, download the Installer artifact from the workflow run's Artifacts section.

## Production rollout checklist (NOT yet completed)
1. Verify recoverable production backup; preserve all existing Android StorePOS data.
2. TEST first login and CAPTCHA challenge on Windows with approved hosted Turnstile flow.
3. Validate StorePOS membership and available device license seat on a non-production account.
4. Download a trusted catalog once; disconnect internet; sell a simple cash product; restart and confirm queue and provisional receipt survive.
5. Print with a real installed USB/Bluetooth 58mm or 80mm printer. Verify codepage, paper-width, and cutter.
6. Reconnect as the original cashier and verify cloud sale/items/payments, retail receipt details and stock movement exactly once.
7. On Android Tablet B and Windows PC, try two concurrent offline sales for the last stock unit; confirm the rejected one is Needs Review.
8. Restock/edit/count offline; reconnect and test manager approval of a conflicting stocktake without rewriting unrelated cloud stock.
9. Check that offline licenses expire correctly; local device clock rollback is blocked.
10. Do not deploy this unverified Windows candidate to live grocery cashiers until all tests pass.

## Deliberate v1 limitations
- No embedded Cloudflare Turnstile CAPTCHA token bridge yet; some production logins will be blocked until implemented.
- No automatic Windows updater, publisher code-signing certificate, branch switcher, or advanced retail packs/promotions offline.
- Manager-review conflicts must be resolved manually; the app never silently forces stock adjustments.
- No claim that offline provisional receipts are officially BIR-compliant fiscal documents.
- No local LAN replication among tablets and PCs while all devices are disconnected; they sync independently when internet returns.

Azurate Software Solutions — StorePOS Windows.
