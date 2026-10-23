# RestaurantPOS Cloud — readiness build 0.2.1

**Not yet production-approved.** See [DEPLOYMENT.md](DEPLOYMENT.md) and [printer test sheet](scripts/PRINT_TEST_MATRIX.md). The existing v0.2.0 native Android APK uses the same REST API.

Run `npm test` for backend unit tests; `npm run preflight` (with environment variables set) for static configuration validation; `TARGET_URL=https://your-host npm run smoke` after HTTPS deployment. Security improvements: byte-exact webhook parsing, bounded JSON payload handling, prevention of simultaneous cash settlement with pending PayMongo sessions, and explicit startup/shutdown handling.

---

# RestaurantPOS Cloud by Azurate Software Solutions

**Version:** 0.2.1 deployment-readiness backend (not yet production certified)  
**Stack:** Node.js 20+, PostgreSQL on Neon, vanilla responsive web frontend.  
**Inspiration:** StorePOS's blue-and-white dashboard layout and checkout interaction, implemented as a new standalone product. No StorePOS source files, credentials, customer data, or existing databases are changed.

## Neon database already created

- **Project:** `azurate-restaurantpos`
- **Project ID:** `delicate-forest-21053245`
- **Database:** `restaurantpos`
- **Region:** Singapore (`aws-ap-southeast-1`)
- **Schema:** `restaurantpos`
- **Initial migration:** `db/001_initial.sql` (already applied, no customer records seeded)

Neon Console: https://console.neon.tech/ . Keep the database password private. Do **not** put it in frontend JavaScript, public GitHub, or GitHub Pages.

## What works

- Separate restaurant login with scrypt password hashing; server-side database sessions in HttpOnly cookies
- Staff roles: owner / manager / cashier / kitchen (bootstrap owner first)
- Menu categories, products, prices, activation status
- Dine-in/takeout checkout, optional table assignment and discount for managers
- Server-side menu-price validation, idempotent order creation, no duplicate open orders for the same table
- Kitchen queue: queued → preparing → ready → served; displays live changes at regular intervals
- Payments: cash with change; **manual records** for GCash/Maya/card (no actual gateway capture or verification)
- Printable 80mm-style **order slip** (before payment) and **payment acknowledgment** (after payment), browser Print/Save as PDF
- Daily sales reports and payment-method summaries in Asia/Manila time
- Configurable restaurant name/address/phone/service-charge settings
- Restaurant-scoped database tables, API permission checks, atomic PostgreSQL checkout/payment transactions

## Quick start (backend hosting required)

1. Install **Node.js 20+**.
2. Unzip and run `npm install` in the project directory.
3. Make a local `.env` from `.env.example` and replace placeholders. In Neon Console, get the **pooled** database connection string for database `restaurantpos`. Select the `azurate-restaurantpos` project and `main` branch. Do not share the credentials.
4. Choose your owner account credentials:

   ```ini
   DATABASE_URL=postgresql://.../restaurantpos?sslmode=require
   OWNER_EMAIL=you@yourrestaurant.com
   OWNER_PASSWORD=YOUR_PRIVATE_STRONG_PASSWORD
   OWNER_NAME=Restaurant Owner
   RESTAURANT_NAME=Your Restaurant
   NODE_ENV=development
   PORT=3000
   ```

5. **Once only:** `npm run setup` creates the initial restaurant and owner. Existing records are never replaced; the setup command intentionally stops if any staff already exists. Remove `OWNER_PASSWORD` from the hosting environment after successful bootstrap.
6. Run `npm start`; browse to **http://localhost:3000** to sign in. For internet access, deploy the Node backend and public files together on a platform that supports Node processes, TLS, environment variables, and outbound PostgreSQL connections. Set `NODE_ENV=production` behind HTTPS.
7. In the UI, create categories, items, and tables; then enter and pay orders.

**GitHub Pages cannot run this Node backend**, so static hosting alone is insufficient. A Cloudflare Worker-based backend could be developed later, but this version needs Node hosting. 

## Data and environment protection

- The current staging project `azurate-cloud-staging` and the existing StorePOS, MotoPOS, DailyDash, and BrewPOS data are left untouched.
- Every restaurant's business tables have `restaurant_id`. The server derives it **from the authenticated session**, not from user input. The client never connects to PostgreSQL directly.
- SSL validation is enabled for Postgres connections, and `NODE_ENV=production` adds `Secure` to session cookies.
- Prices are computed on the server. Client totals are displays only.
- Non-cash payment selections are *manual entry*, not proof of receipt of funds; staff must verify payment out of band.
- This version is **cloud-only** and requires connectivity to the Node API. Offline SQLite sync, Bluetooth ESC/POS, LAN KDS, PayMongo payment verification, inventory, modifiers, split bills, multi-branch owner accounts and audit logs are not yet implemented.
- Before handling real customers and publicly exposing the app, implement production-grade rate limiting, audit trail, automated database backups, staff account management, TLS, gateway verification, and proper invoicing compliance.
- Node test checks: `npm test`. Test a real printer/device on-site before rollout.

## Non-BIR payment acknowledgment

The browser produces **two** distinctly labeled document types:

- Unpaid order: **ORDER SLIP - UNPAID**
- Paid order: **PAYMENT ACKNOWLEDGMENT**

Both prominently show:

> NON-BIR ORDER / PAYMENT ACKNOWLEDGMENT  
> NOT AN OFFICIAL RECEIPT OR SALES INVOICE  
> NOT VALID FOR INPUT TAX CLAIM  
> A BIR-REGISTERED INVOICE MUST BE ISSUED WHEN REQUIRED BY LAW.

This slip is for internal ordering/payment acknowledgment **only**. It must not be used to avoid tax or invoice obligations. Philippine BIR guidance under RR 7-2024 (as amended) treats **invoices** as the primary evidence of sales, while payment receipts are supplementary. Check with your accountant and RDO regarding required invoices and POS permits before production operation.

Official BIR guidance: https://bir-cdn.bir.gov.ph/BIR/pdf/RR%20No.%207-%202024.pdf and https://www.bir.gov.ph/secondary-registration

## API endpoint overview

`/api/login`, `/api/logout`, `/api/me`, `/api/health`, `/api/dashboard`, `/api/menu`, `/api/categories`, `/api/tables`, `/api/orders`, `/api/orders/:id`, `/api/orders/:id/kitchen`, `/api/orders/:id/payment`, `/api/orders/:id/receipt`, `/api/reports`, `/api/settings`.

## Implementation status

- [x] Standalone Neon project provisioned, dedicated schema created
- [x] Functional web source + Node API + SQL migration
- [x] Browser receipt generation and server-side acknowledgment labeling
- [x] Static JS syntax and logic test suite
- [ ] Owner account bootstrap (requires user's private account details)
- [ ] Full integrated online E2E test (requires hosted Node API)
- [ ] Cloud deployment and public HTTPS URL
- [ ] Certified printing, tax invoicing integration, and field testing

## Android v0.2.0 hosted payment integration

- Neon migration: `restaurantpos.gateway_checkouts` (already created in the separate restaurant database). Never run the migration in StorePOS production database.
- Set `PAYMONGO_SECRET_KEY=sk_test_...`, `PAYMONGO_WEBHOOK_SECRET=whsk_...` and `PUBLIC_BASE_URL=https://your-backend-host.example` in your **private backend** environment. Never include them in Android APK or browser JS.
- `POST /api/orders/:id/paymongo-checkout` securely creates or reuses a PayMongo v2 Hosted Checkout session. Supports configured merchant's card, GCash, Maya methods.
- Register a PayMongo webhook (event `checkout_session.payment.paid`) pointing to `/api/webhooks/paymongo`. A verified HMAC signature and a backend retrieve of the session are required before any order can be marked paid. A successful return redirect alone does not confirm payment.
- `GET /api/orders/:id/paymongo-status` checks the provider directly, useful for manual reconciliation after temporary webhook outages.
- Direct `POST /api/orders/:id/payment` only accepts physical cash to prohibit staff from marking unverified digital payments paid.
- Production installation still needs private PayMongo merchant credentials, a public HTTPS host, real paid/test checkout and webhooks verification, secure backup policy and legal BIR invoicing compliance.

**Deployment status:** A production backend has not been deployed because a public HTTPS hosting service and confidential payment credentials have not been configured. Live payment collection is not yet enabled or tested. The Android APK can run offline with a previously cached menu and an unexpired offline login after the first successful online sign-in.
