# RestaurantPOS Multi-Printer Device Acceptance Sheet

Complete this **on the actual Android terminal and thermal printers** before any live rollout. A successful code build is not a physical paper-delivery test.

## Configuration
Record the Android manufacturer/model, OS, APK build/signing fingerprint, terminal ID, Wi-Fi SSID or USB adapter, network subnet, printer vendor/model/firmware, paper width (58/80mm), transport, receipt/kitchen role, IP+port or MAC or USB VID/PID. Do not record passwords.

## Repeat each case on each configured printer

| Test | Pass? | Evidence / result |
|---|---|---|
| Print a test ticket from the destination configuration | □ | |
| Print a sale acknowledgment from POS | □ | |
| Print kitchen ticket for unpaid dine-in order | □ | |
| Print kitchen ticket for takeout order | □ | |
| Correct diacritics/symbols and Philippine peso amount | □ | |
| 58/80mm alignment, feed and cutter | □ | |
| Correct printer routing; no extra printer receives order | □ | |
| Two printers same role receive exactly one ticket each | □ | |
| Disconnect Bluetooth/USB/network mid-ticket | □ | |
| Confirm job flagged uncertain, no blind auto-repeat | □ | |
| Reconnect, intentionally approve one marked reprint | □ | |
| Two terminals queue kitchen orders concurrently | □ | |
| Disable internet but keep local Wi-Fi, print offline order | □ | |
| Reconnect internet and reconcile without double charging | □ | |
| Power-cycle device with pending SQLite print jobs | □ | |

**CAUTION:** ESC/POS write acknowledgments generally do not prove the paper emerged. Physically inspect the ticket before requesting a reprint. Each printer must have a stable IP/DHCP reservation; the cash register and printers must be on a secured private LAN. PayMongo webhook signing must be independently tested with test transactions.
