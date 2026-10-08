# StorePOS v1.6.2 — Staff First-Login Security

This release completes the StorePOS Staff Management v2 flow across StorePOS Cloud and Android.

## Staff first sign-in
- New StorePOS staff accounts created with a temporary password are now required to choose their own password before entering the Android app.
- The requirement is read from server-controlled Supabase `app_metadata.must_change_password`.
- Password changes are completed through the authenticated StorePOS `invite-staff` Edge Function.
- After changing the temporary password, the staff member signs in again with the new password.

## Security
- Temporary-password enforcement happens before shop/device/license access is loaded.
- Passwords are never stored in StorePOS tables or logs.
- The password-change flag is cleared server-side only after the password update succeeds.
- Existing linked Supabase accounts keep their existing password and are not forced through this flow unless explicitly marked.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: com.storepos.app
- Version code: 21
- Version name: 1.6.2
- Existing shops, products, sales, customers and licenses are preserved.
