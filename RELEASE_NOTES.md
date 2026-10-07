# StorePOS v1.5.1 — Customer Display UI Polish

This patch release improves the StorePOS customer-facing second display introduced in v1.5.0.

## Customer Display
- Redesigned the external customer display with a modern StorePOS blue/navy visual style.
- Uses the Android Presentation display context so scaling follows the actual second display density.
- Added a responsive two-column layout for 720p and 1080p customer screens.
- Added a clean empty-cart state with clearer customer messaging.
- Added STOREPOS branding, store name, live status, item count and checkout guidance.
- Improved product rows with quantity badges, unit pricing and line totals.
- Added a dedicated Amount Due panel with a larger, clearer total.
- Improved spacing, typography, contrast and rounded-card styling.
- Added a narrow-screen fallback for smaller external displays.

## Compatibility
- Android 8.0+ (minSdk 26)
- Package: com.storepos.app
- Version code: 18
- Version name: 1.5.1
- No changes to checkout, payment, inventory or cloud data behavior.
