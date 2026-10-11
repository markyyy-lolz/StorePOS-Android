# StorePOS Hybrid v1.8.2 RC1 — Full Offline Inventory

**This is an additive development implementation, not a production APK.** Existing StorePOS v1.8.1 hybrid POS cash sales/receipts remain in place. No destructive resets.

## What works without internet (after one trusted online catalog download)
- Inventory tab falls back to the encrypted-by-app-sandbox SQLite catalog and remembered categories; "OFFLINE INVENTORY" displays clearly
- An authorized **owner/manager/admin** can create a new product with a local stable UUID, edit a product's supported details and prices, and run a partial barcode-assisted physical stocktake
- An authorized **inventory** staff account can add/return/deduct stock using the Adjust Stock form; **owner/manager/admin** can also do this
- Each adjustment is a signed-in actor-bound immutable operation in the SQLite `pending_inventory` outbox and the updated product cache is committed in the same transaction
- New products can be scanned and sold locally; both stock changes and cash sales can be replayed when connectivity is restored
- Pending operations and rejected stock conflicts are visible in Inventory → View inventory sync, with manager-reconciliation guidance. Never clear app storage to get rid of a conflict.

## Two-device conflict rules
- **Stock delta (+/-)**: additive under PostgreSQL product row lock and logged with real inventory movement history; insufficient stock fails, never creates silent negative quantities.
- **Product edit**: expected original name, price, cost, unit and category compared with current cloud data. A second tablet editing that SKU simultaneously causes a Needs Review conflict.
- **Physical stocktake**: uses an expected system quantity; if cloud stock changed from another tablet's sale/restock, the absolute counted quantity is NOT blindly posted.
- **Create product**: stable server-generated? **No**, stable **locally generated UUID**. Duplicate shop SKU checks and cloud unique constraint reject conflicting creations for review.
- **Reset protection**: every operation is tied to the StorePOS shop's trusted catalog epoch. Shop reset or product delete invalidates stale operations.
- **Idempotency**: operation ID is locked and journaled atomically. Lost acknowledgements may be retried without double-restocking.
- **Offline restricted**: deletion/archiving, complex batch/serialized/weighed goods, merchant license setup, online payments and category edits still require internet.
- **Manager review** is read-only from Inventory; conflicts remain local until the manager has manually reconciled cash/stock. Do not auto-clear conflict records.
- When older offline edits and cash sales have opposite chronological dependencies (e.g. sale before a later edit), cloud price/stock checks may need manager review. Do not claim all operations are guaranteed to synchronize automatically.

## Existing backend
The StorePOS business lives in MotorPOS Supabase `qgyzdoltjlryjthxxscw`, separate from dedicated BrewPOS project. StorePOS shop scoped by `app_code='storepos'`.

Migration (pending for v1.8.2): `supabase/migrations/20261010_storepos_offline_inventory_v182.sql`
- new private inventory idempotency ledger, no direct client data mutations
- atomic `storepos_reconcile_inventory` RPC with Auth UID/shop role, epoch lock, product row locks and previous-value compare-and-set
- Does **not** alter/remove existing products, sales, payments, receipts, stock movements, staff, or license data

## Required release tests
1. Verify recoverable StorePOS backup, record current sales and stock counts
2. Apply additive v1.8.2 SQL migration and check RLS/grants with authenticated + anonymous sessions
3. Build/lint Gradle and verify PR #19 artifacts
4. On **two actual Samsung tablets**, log in and refresh catalog once; pull all Wi-Fi/cellular connections
5. On Tablet A: add, edit, restock, subtract and partial stocktake a test SKU, restart app and verify local outbox survives
6. On Tablet B: sell the same SKU; after reconnection, check that sold/restocked totals only post once, stale physical stocktake goes to Needs Review
7. Verify StorePOS v1.8.1 local receipt printing while editing offline inventory
8. Check different staff sign-in, invalid license, wrong shop, former employee and catalog reset
9. Only ship a production APK signed with the original release key; never replace the live grocery app with a debug-signed build without a backup/rollback plan
