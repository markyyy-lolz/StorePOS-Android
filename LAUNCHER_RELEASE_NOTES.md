# StorePOS Terminal Launcher v1.0.0

First standalone StorePOS dedicated-device launcher.

## Highlights
- Separate Android package: `com.storepos.launcher`
- StorePOS + Settings only on the main launcher screen
- Official StorePOS branding
- First-run administrator PIN
- Long-press logo for Administrator Access
- Restricted device settings shortcuts
- Set as default Android Home app
- Device Owner detection
- Optional Lock Task / kiosk mode
- Optional StorePOS auto-open
- StorePOS installation detection
- Generic Android tablet and SUNMI-class terminal support

## Full kiosk setup
Full lock-down requires Device Owner provisioning on a freshly reset Android device:

```
adb shell dpm set-device-owner com.storepos.launcher/.admin.StorePosDeviceAdminReceiver
```

Without Device Owner, StorePOS Terminal still works as a custom Android launcher but cannot guarantee that all system escape paths are blocked.
