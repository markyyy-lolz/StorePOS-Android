# RestaurantPOS Cloud v0.2.1 — deployment and production gating

## Before deployment

The existing Neon `azurate-restaurantpos` project (`delicate-forest-21053245`), database `restaurantpos` is separate from all other Azurate products. Its schema has 10 tables, including gateway checkouts. At the audit date, there were **no staff, restaurant, order, or payment records**, so the owner is **not** initialized.

**Never publish Neon passwords, PayMongo credentials, or owner passwords to GitHub, APK, screenshots, or chat.** Use the hosting platform's private environment variables.

### A. Deploy backend HTTPS

1. Create a dedicated **private GitHub repository** with the files of this folder **at the repository root**. Do not reuse or modify StorePOS main.
2. Connect GitHub to Render (or an equivalent persistent Node.js web host). `render.yaml` configures the Singapore region, health check, and starter tier. **Starter can incur charges: approve pricing first.**
3. Add environment variables: `DATABASE_URL` (Neon **pooled** connection URL with `sslmode=require`), `PUBLIC_BASE_URL` (the assigned **HTTPS** hostname), `NODE_ENV=production`.
4. Add `PAYMONGO_SECRET_KEY` and `PAYMONGO_WEBHOOK_SECRET` only for test-mode Checkout. Keep both absent to disable all PayMongo gateway actions until credentials are ready.
5. Start the service; `GET /api/health` must return `{"status":"ok","database":"connected"}` (HTTP 200). If 503, check private connection settings and Neon availability.
6. Run `TARGET_URL=https://YOUR-HOST npm run smoke` from any workstation with Node 20+; verify four checks pass.

### B. Create the first owner — once only

Initial owner creation is **not automatic**. Set `OWNER_EMAIL`, `OWNER_PASSWORD` (strong unique 12+ characters), `OWNER_NAME`, and `RESTAURANT_NAME` in an environment accessible to the backend's **one-off shell/administration task**. Execute `npm run setup` *once* while the service has access to Neon. The script refuses to create a second owner if any staff exist. Immediately delete `OWNER_PASSWORD` from the hosting environment and restart. Use a personal email controlled by the restaurant owner. Do not create a shared default account.

**Do not run setup twice** and do not manually create a dummy production owner. Confirm owner login on the native Android application only after HTTPS and Neon health checks pass.

### C. Enable test payments and then live payments

1. First configure PayMongo **test-mode** `sk_test_…` key and matching webhook-signing `whsk_…` secret in the Node backend (never on Android).
2. Register webhook URL `https://YOUR-HOST/api/webhooks/paymongo` for `checkout_session.payment.paid`.
3. Perform a checkout using PayMongo's documented test credentials/test methods; confirm backend payment status is not marked paid until verified. Repeat duplicate event delivery to confirm idempotency. Test canceled, pending, stale signature and wrong-amount cases. Record signed-event IDs and reconcile gateway vs ledger.
4. Only with merchant approval and full tests switch to **live** keys, register a separate live webhook, test a legitimate authorized live transaction and reconcile the report. Do not assume GCash, Maya, or cards are all enabled for your merchant.
5. A pending gateway checkout **blocks concurrent cash settlement** in this build. Resolve it via verified gateway status and administrative procedure before accepting cash for the same order.

### D. Android and printer acceptance

- Install signed `com.azurate.restaurantpos` native v0.2.0 APK on test Android terminal. Configure the *public HTTPS* URL, sign in online, then cache the menu before going offline.
- Register printers per role: kitchen and receipt. LAN / Wi-Fi devices use a local IP and a typical ESC/POS TCP port (commonly 9100, check model); Bluetooth uses pre-paired SPP; USB uses OTG and Android device permission.
- Complete `scripts/PRINT_TEST_MATRIX.md` on **every** supported printer/model/device. Test power failure, duplicate outbox sync, reconnection, printer disconnection, Bluetooth permission, USB reconnect, and correct cutter command. Printer jobs that may have printed are deliberately not blindly retried.
- Verify pending offline cash orders after internet reconnect. Price/service charge mismatches go to manager review, not automatic settlement. **Do not uninstall, reset, or clear the APK while offline outboxes contain unsynced sales.**
- Verify receipt vs kitchen ticket, X/Z operations, tax compliance/invoicing procedures, backups, least-privilege administrator access, security updates, incident response, and restore drills.

## Known non-production-ready areas

- No hosting service has been connected or deployed on your behalf.
- No real owner credentials have been provided; no owner account has been initialized.
- No actual physical printer or Android device was reachable for field testing.
- No live PayMongo merchant credentials or public webhook URL were available.
- Android offline SQLite is **not encrypted**, no key rotation/managed staff device revocation. Remote device access should be blocked until those controls exist.
- Admin resolution of conflicting gateway checkouts needs a documented manual process; avoid live usage until integrated.
- Printed non-BIR acknowledgment slip is supplementary and cannot replace BIR-required invoices or permits. Consult your accountant and RDO before invoicing customers.

**Go-live gate: NOT CLEARED.** Passing 9 backend unit tests and an APK CI build does not constitute production sign-off.
