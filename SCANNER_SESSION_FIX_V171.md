# StorePOS Android v1.7.1 — Scanner & Sign-In Reliability

## Fixed
- Camera barcode scanning from POS or Inventory no longer intentionally changes the current page to Dashboard after returning from a scanner Activity recreation.
- Selected navigation destination is saved/restored using Compose rememberSaveable, including on tablet and phone layouts.
- ZXing scanner orientation is locked for all POS and Inventory scanning entry points.
- Accidental HID scanner Back/Escape suffix does not send users from POS or Inventory to Dashboard. Use StorePOS sidebar/bottom navigation to switch pages.

## Added
- **Stay signed in** option on the StorePOS Android sign-in screen (checked by default for compatibility with existing accounts).
- With the option enabled, Supabase Auth restores its stored device session across app cold starts, subject to valid credentials/device licensing.
- With the option disabled, the local stored session is cleared the next time the app process launches from cold, requiring credentials again.
- The preference is device-local; passwords are never stored by this feature, and Sign out remains available. Returning briefly from the scanner does not cause a sign-out.

## Notes
- Camera orientation, app process recreation, and scanner-specific ESC/BACK suffix behavior depend on the actual tablet and barcode reader. Test on the Samsung Tab A9+ 5G before deploying to active checkout.
- The previous StorePOS production database and all MotoPOS records remain unchanged.
