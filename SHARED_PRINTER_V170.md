# StorePOS v1.7.0-rc1 — VOZY G80 Shared Printing

**Status:** release candidate, NOT hardware-approved until checked on two actual Android tablets + a VOZY G80 USB/Bluetooth printer.

## Deployment (one shop, two tablets)
1. Install the candidate APK on both tablets; sign in with active StorePOS shop members.
2. Tablet 1 (owner/admin): pair VOZY G80 from Android Bluetooth settings; choose it under Settings > Receipt printer, 80mm, and print a direct test.
3. Settings > Shared Printer Mode > Assign this tablet as host. Allow Bluetooth and foreground-service access.
4. Tablet 2: Settings > Shared Printer Mode > Remote cashier. No Bluetooth pairing on Tablet 2.
5. Tablet 2: Queue test print. Only Tablet 1 directly connects to the G80.
6. Run consecutive completed sales across both tablets; one receipt per sale, FIFO by queue creation order.
7. Changing host requires shop owner/admin. Pair VOZY G80 to the other tablet, then assign that tablet as host there. The old host loses RPC authorization.

## Failure and offline handling
- Connection fails before sending bytes: safe auto retry, at most three attempts.
- Partial stream write, interrupted print or expired claim lease: NEEDS_REVIEW. Check paper output before manually retrying.
- SENT means stream bytes transmitted, NOT proof of completed physical paper.
- Client outbox: while Supabase is unreachable, app-private storage retains up to 100 receipts; reconnect/reopen POS to flush. Settings has Sync pending receipts.
- Offline sale must sync to Supabase before receipt can pass sale validation.
- Device heartbeat and foreground notification show host availability. Android battery policies can still interfere.

## Printouts
- Receipt uses same ESC/POS bytes for direct/remote output and thermal-width PDF. Customer/tax lines are informational; **not BIR-accredited**.
- X is live and non-closing. Z prints only an already-finalized shift. Batch uses the recorded Z snapshot with blank denomination counting lines.
- Actual logo bitmap rendering, full Philippine regulatory invoice compliance and cashier audit sign-off are **not complete** in this candidate.

## Required physical acceptance tests
- [ ] Tablet 1 pairs/prints to VOZY G80.
- [ ] Tablet 2 queues receipt; only Tablet 1 transmits it.
- [ ] Ten alternating receipts preserve order and physical cutter spacing.
- [ ] Tablet 2 offline outbox flushes once without duplicating sales.
- [ ] G80 unavailable before connection: max three safe automatic retries.
- [ ] Partial stream write: manual review and no automatic reprint.
- [ ] Host killed: incomplete claim enters NEEDS_REVIEW after lease.
- [ ] Owner/admin failover removes old-host printing authority.
- [ ] Thermal roll and PDF inspected side-by-side.
- [ ] X, Z and Batch report printouts reviewed on real 80mm paper.
- [ ] Cross-shop / MotoPOS isolation checked with distinct real accounts.

## Database and CI
SQL migration: supabase/migrations/20261008_storepos_shared_printer_v170.sql
Two additive RLS tables; authenticated users can SELECT, not directly INSERT/UPDATE.
The authenticated RPC enforces StorePOS retail shop membership on every call and serializes claims using row locking.
Existing MotoPOS and StorePOS sales records are not reset.
GitHub Actions runs unit tests, Android lint and debug APK builds; it does not prove physical printer behavior.
