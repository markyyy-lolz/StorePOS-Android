# RestaurantPOS Android v0.1.0
Package: com.azurate.restaurantpos

This is a real installable Android APK: native settings and system printing with a secure embedded web-powered RestaurantPOS Cloud UI. It is not a fully native Compose UI. The separate Neon database has been created, but the REST backend needs to be hosted at a public HTTPS URL and an owner account created. No Neon credentials are shipped in the APK.

Use JDK 17, Gradle 8.11.1 and Android SDK 36:
    gradle :app:assembleDebug

Cloud-only. Requires internet. Receipt print is through Android system Print/Save as PDF; direct ESC/POS auto-cut and offline mode not supported. Payment gateway authorization not supported (manual recording only). A non-BIR acknowledgment is never a substitute for a legally required invoice.

Source and debug artifact are on the separate restaurantpos-android-v0.1.0 branch of StorePOS-Android. StorePOS main is not modified.
