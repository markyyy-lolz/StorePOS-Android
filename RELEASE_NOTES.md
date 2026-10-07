# StorePOS v1.4.1 — Cloud Insert Response Hotfix

- Fixed a false **incomplete cloud response** error after successfully creating products.
- Product creation now explicitly requests the inserted row from Supabase before decoding it.
- Applied the same response-mode fix to customer, motorcycle, job order, supplier, expense, appointment, service reminder, warranty claim, and held-sale inserts.
- Prevents records from being saved successfully in the cloud while the Android UI incorrectly reports a failure.
- Keeps opening-stock initialization working after a new product is created.

This is a patch release for StorePOS Android v1.4.x.
