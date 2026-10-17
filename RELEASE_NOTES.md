# StorePOS Android v1.8.9 — Separate Freebie Claim Slip with Auto-Cut

**Build 41 • Safe update over v1.8.8 • package `com.storepos.app`.**

## Receipt and promotional voucher print separation
- **Qualifying purchase:** first print the normal sales receipt through the standard store and Azurate supplier footer, then **FULL AUTO-CUT**, then print a separate **FREE ITEM CLAIM SLIP** containing the shop name, reward, quantity, price PHP 0.00, original sale reference and staff-verification reminder, followed by **another FULL AUTO-CUT**.
- **Non-qualifying purchase / disabled promo:** one normal receipt and one full cut, unchanged.
- Uses the existing device's configurable receipt footer-to-cut feed lines for both full cuts.
- Thermal receipt and claim slip are sent in **one immutable print stream** and (where applicable) remain together in the saved offline receipt and shared print queue, preventing separate voucher print-queue jobs.
- **Receipt PDF parity:** the ESC/POS decoder preserves the internal full-cut boundary. PDF displays the sales receipt and qualifying claim slip **on separate pages**, keeping print/PDF content consistent.
- **Offline sale:** provisional voucher uses the unique `OFF-...` reference and clearly states **OFFLINE - PENDING SYNC**. No invented final cloud sale number.
- Reprints produce the same claim slip and sale reference, but **do not authorize additional gifts**. Staff must check whether the gift was already issued.

## Existing behavior preserved
- Store owner/admin can configure the spending threshold, item, and quantity under Settings → Spend & Get Free Item. Default OFF. Settings cache to shop-scoped SQLite; offline cash sales can generate the slip after initial online sync.
- Paid sale totals and tax calculations unchanged; free item is not automatically added to `sale_items` nor deducted from stock. There is no one-time claim ledger in this version.
- No new Supabase migration or changes to StorePOS/MotoPOS/BrewPOS inventory, history or user data.
- Standard receipt, X/Z and printer test reports preserve existing full-cut behavior.

## Upgrade/test
1. **Install over v1.8.8**, do not uninstall or clear data while offline sales are pending.
2. Configure a rule (for example PHP 1,500 / free rice 1kg), and sync both tablets online.
3. Test PHP 1,499.99 (one normal cut) and PHP 1,500.00 (two physical pieces, two cuts) with the VOZY G80.
4. Check both online and offline scenarios, save PDF, verify its page split and the exact sale reference.
5. Avoid physically issuing the reward twice after any receipt reprint; proper redemption tracking is a separate requirement.

**Status:** Android unit tests, lint, packaging, signature verification and GitHub Release occur in CI. Printer hardware verification must still be performed on the actual VOZY G80.
