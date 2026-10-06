# StorePOS Terminal Launcher

StorePOS Terminal Launcher is a separate Android home-screen application for dedicated retail tablets and SUNMI-style Android POS terminals.

## What it does

- Presents only two primary launcher tiles: **StorePOS** and **Settings**.
- Uses a restricted Settings screen for Wi-Fi, Bluetooth, display, sound, and date/time.
- Hides administrator tools behind a long-press on the StorePOS logo plus an admin PIN.
- Can be selected as the Android default Home/Launcher app.
- Supports optional Android **Device Owner + Lock Task** kiosk mode.
- Can auto-open StorePOS when the launcher starts.
- Detects whether the main StorePOS package (`com.storepos.app`) is installed.
- Uses the official StorePOS retail branding.

## Package

`com.storepos.launcher`

This is separate from the main StorePOS application package:

`com.storepos.app`

## Standard mode

Install the APK, open **Administrator Access** by long-pressing the StorePOS logo, then tap **Set as default Home app**. Android will let you choose StorePOS Terminal as the default launcher.

Standard mode changes only the home screen. Android system controls can still provide ways to leave the launcher.

## Full kiosk mode

For a properly locked dedicated device, provision StorePOS Terminal as Android Device Owner. Device Owner generally requires a freshly reset device with no existing user accounts.

After installing the launcher APK through ADB, run:

```bash
adb shell dpm set-device-owner com.storepos.launcher/.admin.StorePosDeviceAdminReceiver
```

Then open Administrator Access and enable **Kiosk lock**.

In Device Owner mode, StorePOS Terminal allowlists:
- StorePOS Terminal Launcher
- StorePOS Android
- the device's Android Settings package needed for restricted settings activities

Kiosk mode disables the status bar/keyguard where the Android device allows it and starts Android Lock Task mode.

## Admin PIN

On first launch, StorePOS Terminal requires a 4–8 digit administrator PIN. The PIN is stored as a salted SHA-256 hash tied to the Android device ID, not as plain text.

## Compatibility

- Android 8.0+ (API 26+)
- Generic Android tablets
- Android-based POS terminals, including SUNMI-class devices
- Portrait and landscape layouts
