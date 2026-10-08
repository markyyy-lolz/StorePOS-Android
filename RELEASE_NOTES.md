# StorePOS Android v1.7.0-rc1 — VOZY G80 Shared Printing (TEST BUILD)

This version is a **release candidate for hardware acceptance only**, not the approved final StorePOS v1.7.0 production release. The feature is opt-in: existing installs remain in Direct printer mode until configured.

## New
- Two Android cashiers share one VOZY G80 USB/Bluetooth printer.
- Tablet 1 is the printer host; Tablet 2 sends print requests over a Supabase shop-scoped FIFO queue.
- Foreground host status notification, atomic queue claims, automatic connection retries (up to three), manual review of uncertain partial writes, and owner/admin host reassignment.
- Offline remote cashier receipts are persisted in a local outbox and replayed with stable request keys when back online.
- Print queue status and manual recovery tools in Settings.
- 80mm receipt tax/customer information and professional live X, archived Z and batch printouts; thermal/PDF share the receipt template.

## What remains
- **Unverified on physical VOZY G80 and two tablets.** Run SHARED_PRINTER_V170.md acceptance tests before production use.
- Logo bitmap printing and fully compliant/accredited Philippine tax-invoice formatting are not complete.
- SENT means a successful ESC/POS byte transfer, not independent physical receipt confirmation.
- Stable Android v1.6.4 remains recommended for production until the acceptance tests pass.

## Setup
Pair the G80 with Tablet 1. In StorePOS Settings select G80 80mm and test the Bluetooth connection; then tap Assign this tablet as host in Shared Printer Mode. On Tablet 2 enable Remote cashier and try Queue test print. For instructions and failure recovery see SHARED_PRINTER_V170.md in source repository.

## Build
APK is a debug-signed candidate, not a Google Play production-signed APK.
