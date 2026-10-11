# StorePOS v1.8.1 — Two-Tablet Hybrid Implementation Notes

> **Not production-ready.** This work includes PR #17 (offline SQLite receipts, PDF, direct printing, reconnect WorkManager) plus an atomic server gate. Complete CI + migration checks and real Samsung tablet testing before release.

## Expected behavior

- Tablet A and Tablet B can check out **cash-only** sales independently with no network.
- Each stores the original cashier, StorePOS shop, immutable item/unit-price snapshot, **cloud catalog epoch**, globally unique UUID and exact thermal ESC/POS receipt bytes in app-private SQLite.
- Once the local transaction commits, the provisional offline cash receipt can print immediately using **directly paired Bluetooth or USB** (VOZY G80). A PDF renders from the **same bytes**, even with no network. A shared cloud print job requires network; two disconnected tablets cannot magically use one physically connected printer.
- An authenticated WorkManager job (periodic and network-constrained on reconnect) and 30-second foreground retry replay queued sales into the dedicated StorePOS cloud. Both tablets' completed sales appear in the same Supabase shop when server accepts them.
- The **identical** client UUID is reused across an online attempt, ambiguous timeout, SQLite outbox record and all retries. Server checks already-posted UUID **before stock reconciliation**, avoiding a duplicate after a lost response.
- The database function takes an epoch row lock and ordered product locks; expired/reset catalogs, changed prices and oversold stock fail closed for manager review. A local provisional receipt still exists, and customer cash must be reconciled manually; the app never silently edits historical prices or posts negative stock.
- Legacy queue rows without the new epoch remain locally archived as Needs Review; there is no automatic upload after the Sherine Store catalog reset.
- StorePOS and MotoPOS can live in the same Supabase project without mixing shops: only shops with `app_code='storepos'` acquire epoch records; sales access also requires an active shop membership.
- No Shop, Sale, Payment, Receipt, Inventory Movement, or Backup data is removed/overwritten by this migration.

## Server staging and migration

Apply `supabase/migrations/20261010_storepos_hybrid_epoch_atomic_sync.sql` to **MotorPOS** project `qgyzdoltjlryjthxxscw` only. It adds:
- private `storepos_hybrid_epochs` per StorePOS shop (RLS member SELECT only)
- generation bootstrap on new StorePOS shops
- generation rotation on product DELETE or TRUNCATE; **stock quantity updates do NOT rotate**
- `storepos_hybrid_reconcile_sale` server-owned, role-restricted RPC with idempotency, checkout constraints, cash-only payments, price and locked stock validation

For catalog resets performed through other privileged mechanisms (such as re-creating the entire shop with a new ID), ensure all tablets refresh. Never reset the current production shop or wipe old Android SQLite queues automatically. Do not configure new shop sync against another tenant.

## Test matrix before installing on cashiers

1. Verify a recoverable production backup and record existing counts for shops, sales, sale_items, payments, inventory_movements, offline_sale_requests and receipts.
2. Install on spare or safely backed-up Samsung tablets. Check same **release signing certificate** as installed app.
3. Both tablets log in as the intended cashier, connect once, receive same shop catalog epoch and cache products.
4. Disconnect both; sell different cash orders, power-cycle one device, confirm sales/reprint bytes/PDF match 58/80mm printer.
5. Reconnect Tablet A then B; check server counts and matched shop and cashier, stock decrements exactly once, transaction UUID replay does not double-post after lost reply.
6. Sell the last unit simultaneously on both offline tablets; the second must stay Needs Review without silent oversell.
7. Reset/delete a **test product in a test shop**; verify stale pending sale is refused, with original local provisional receipt retained.
8. Disconnect the cloud printer; test local VOZY Bluetooth/USB. Confirm second tablet needs its own direct connection or local print bridge.
9. Test suspended/unlicensed users, wrong-shop session, expired token, Android app process killed, WorkManager eventual retry and manager review.
10. No BIR/final-numbered tax receipt claim for provisional `OFF-` numbers; finance should reconcile provisional cash until cloud-generated receipt and compliance workflows are confirmed.

## Known remaining work

- SQL needs to be applied and verified on the dedicated production project's safe migration path.
- Android CI and lint/tests must succeed; real device/signed release build, POS UX, printers and inventory contention must be tested.
- Changing a product **price** does not bump the epoch; the reconciliation RPC independently compares its snapshot.
- Cloud sales can only sync with an authenticated authorized original cashier session. Network recovery is not a bypass of login, license or branch permissions.
