# StorePOS Android v1.8.8 — Spend & Get Free Item on Receipts

**Build 40 • Update over Android v1.8.7 (39) • package `com.storepos.app`.**

## New: Shop-wide spend threshold free item
- Owner/admin: **Settings → Spend & Get Free Item → Configure Free Item Promo**.
- Default is DISABLED. Set a minimum paid total (example PHP 1,500), item name, quantity and enable the offer. The configured rule syncs through the dedicated `public.storepos_receipt_freebie_rules` table with StorePOS-only shop RLS.
- Eligible *completed* sales include **FREE ITEM REWARD**, the free item description/quantity and **PRICE: PHP 0.00** on 58mm/80mm physical thermal receipts.
- Threshold compares the FINAL payable total after discounts, not simply the gross subtotal. Receipts below the limit show no free item notice.
- Rules are saved locally on each tablet in a shop-scoped SQLite blob after cloud refresh. A verified cached rule supports offline cash sale receipts even after the app is closed and reopened without internet. The exact offline thermal receipt bytes are stored with the queued sale, so its Save PDF output matches.
- Online receipt PDF uses the same thermal renderer and reward rule. The existing Azurate supplier footer and all StorePOS v1.8.7 offline catalog protections remain unchanged.

## Safety and redemption limitation
- The printed reward is a **claim eligibility notice**, not a recorded redemption, automatic stock deduction or proof the free item has been handed over.
- Staff must verify the receipt and independently record issuance/stock adjustment. **Do not hand out multiple gifts against duplicate printouts**. Cloud-enforced one-time redemption scanning is a separate future feature.
- Configurations are initially disabled for every shop; no sale, inventory or MotoPOS/BrewPOS data has been changed.
- The Supabase StorePOS-only migration is required. If a tablet is offline and has not downloaded an enabled rule, no offer is shown; sync Settings/POS online once first.
- No gift is added to the paid cart or cloud sale_items, preventing unsupported inventory/tax manipulation.

## Safe deployment on Samsung Galaxy Tab A9+
1. Back up device's offline outbox and check unsynced sales. **Install directly over v1.8.7**, never uninstall/clear data.
2. While online as StorePOS owner/admin, configure and enable a rule in Settings.
3. On each tablet, open POS online so the shared rule can be downloaded and cached.
4. Test a qualifying cash sale (example PHP 1,500) and one PHP 1,499 sale; inspect the printed receipt and Save PDF.
5. Turn off internet; close and reopen StorePOS; complete an authorized test cash sale, confirm same reward notice and local printing, then reconnect and verify idempotent sale sync.
6. Test 80mm VOZY G80 on physical devices before production use.

## Verification
- GitHub workflow runs unit tests, Android lint, APK build and signature verification before publishing.
- Live printer, stock handling for manually redeemed rewards, and production multi-tablet acceptance remain manual checks.
