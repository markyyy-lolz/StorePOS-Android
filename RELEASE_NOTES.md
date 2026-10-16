# StorePOS Android v1.8.6 — Azurate Supplier Contact Receipt Footer

**Optional stable update — Build 38, package `com.storepos.app`.**

## What's new

- Replaces the fixed **Powered by StorePOS** sales-receipt footer with clear **POS software supplier and technical support information**:
  - POS SYSTEM SUPPLIED & SUPPORTED BY
  - AZURATE SOFTWARE SOLUTION
  - PULO SAN ROQUE, PAOMBONG, BULACAN
  - TECHNICAL SUPPORT / INQUIRIES
  - **09241134301**
- Formats the fixed footer for **80mm and 58mm receipts**. The address may wrap into two centered lines on 58mm rolls.
- Keeps the merchant/store identity, business information, taxes, sales totals and editable customer thank-you message separate from the software supplier information.
- Shows the same footer in **Receipt Designer preview**, sales thermal print bytes and **Save PDF** (the PDF is rendered from the same official ESC/POS bytes).
- Printer test prints and thermal X/Z/batch report footers now show the supplier contact.
- Preserves user-selected **Footer-to-Cut Spacing**, including full-cut command after the final contact phone line.

## Safe update procedure

1. **Install v1.8.6 directly over the existing v1.8.5 APK.** Do **not** uninstall, clear storage, or factory reset. Pending offline sales and receipt bytes may be stored only on that tablet.
2. Online, open StorePOS once and verify the existing shop/license and cached catalog.
3. In **Settings → Receipt Printer**, run a physical VOZY G80 test print and verify the entire supplier address and contact number; check cutter spacing.
4. Make a small authorized test checkout and verify a new 80mm/58mm paper receipt, PDF output, and Receipt Designer preview.
5. Reprint existing historical transactions without creating a second sale. Previously saved immutable thermal receipt bytes/PDF files are not rewritten.
6. Verify any offline cash receipts and delayed cloud sync on the actual paired Samsung tablets before wide rollout.

## Not changed

- Existing **v1.8.5 offline SQLite backup compatibility fix**, trusted catalog, offline cash checkout, receipt queues, printer pairing, and Supabase shop data are preserved.
- **No database migrations, licensing schema changes, product edits, or changes to MotoPOS/BrewPOS**.
- **Peddlr-inspired Smart Barcode Quick Add** and **v1.9.0 LAN Hub beta** are *not included* in this release.
- A GitHub Android build/test result does not replace physical VOZY G80 acceptance testing.
